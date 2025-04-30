package chat.paramvr.usersettings

import chat.paramvr.*
import chat.paramvr.auth.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val dao = UserSettingsDAO()

fun Route.userSettingsRoutes() {
    route("settings") {
        tryGet {
            log("")
            val settings = dao.retrieveSettings(userId())
            call.respond(settings)
        }
        tryPost {
            val settings = call.receive<UserSettings>()
            if (settings.avatarChangeCooldown < 10) {
                call.respond(HttpStatusCode.BadRequest, "Cooldown less than 10 seconds not allowed in order to prevent abuse.")
                return@tryPost
            }
            if (!settings.validateColors()) {
                call.respond(HttpStatusCode.BadRequest, "Color contains illegal characters.")
                return@tryPost
            }
            log("settings = $settings")
            dao.updateSettings(userId(), settings)
            getListener()?.settings = settings
            call.respond(HttpStatusCode.NoContent)
        }
    }
}