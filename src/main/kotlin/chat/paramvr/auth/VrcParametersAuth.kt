package chat.paramvr.auth

import com.google.gson.JsonObject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import chat.paramvr.Crypto
import chat.paramvr.conf
import chat.paramvr.isProduction
import chat.paramvr.tryPost
import io.ktor.util.date.*
import java.util.*
import kotlin.text.toCharArray

private val dao = SessionAuthDAO()

fun AuthenticationConfig.installVrcParametersAuth() {
    basic(name = "Basic-ListenKey") {
        realm = "Access to the /parameter-listen path"
        validate {credentials ->
            val targetUser = credentials.name
            val listenKey = credentials.password

            application.environment.log.info("Performing Basic auth for $targetUser:$listenKey")

            val authUser = dao.retrieveUser(targetUser)
            if (authUser == null || listenKey != authUser.listenKey) {
                return@validate null
            }

            request.call.attributes.put(AttributeKey("target-user"), targetUser)
            request.call.attributes.put(AttributeKey("user-id"), authUser.id)
            UserIdPrincipal(targetUser)
        }
    }
    form(name = "Form") {
        userParamName = "username"
        passwordParamName = "password"
        skipWhen { it.sessions.get<VrcParametersSession>() != null }
        validate { credentials ->

            application.environment.log.info("Performing Form auth for ${credentials.name}")

            val user = dao.retrieveUser(credentials.name)
            if (user != null && user.failedLogins >= conf.getFailedLoginLimit()) {
                return@validate null
            }

            request.cookies["Quick-Auth"]?.let {
                application.environment.log.info("Trying Quick-Auth")
                dao.retrieveUserForQuickAuth(it)?.let { user ->
                    sessions.set(user.newSession())
                    return@validate UserIdPrincipal(credentials.name)
                }
                // attempt normal auth if Quick-Auth not recognized
            }

            if (user == null) {
                return@validate null
            }

            val digest = Crypto.hash(credentials.password.toCharArray(), user.salt)
            if (digest.contentEquals(user.saltedHash)) {
                sessions.set(user.newSession())
                val quickAuthKey = UUID.randomUUID().toString()

                response.cookies.append("Quick-Auth", quickAuthKey, maxAge = 2000000000L, secure = isProduction, httpOnly = true)

                dao.createQuickAuth(user, quickAuthKey)
                user.failedLogins = 0
                dao.updateUser(user)
                return@validate UserIdPrincipal(credentials.name)
            }
            else {
                user.failedLogins++
                dao.updateUser(user)
                return@validate null
            }
        }
    }
}

fun Route.authRoutes() {
    tryPost("login") {
        val body = JsonObject()
        body.addProperty("userName", vrcParametersSession().userName)
        body.addProperty("userId", userId())

        call.respond(body)
    }
    tryPost("logout") {
        call.sessions.clear<VrcParametersSession>()
        call.response.cookies.append("Quick-Auth", "", expires = GMTDate.START)
        call.respond(HttpStatusCode.NoContent)
    }
    route("account") {
        tryPost {
            val request = call.receive<JsonObject>()
            val newPassword = request.get("newPassword").asString
            val authUser = dao.retrieveUser(id = userId())!!
            authUser.salt = Crypto.nextSalt(32)
            authUser.saltedHash = Crypto.hash(newPassword.toCharArray(), authUser.salt)
            dao.updateUser(authUser)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}