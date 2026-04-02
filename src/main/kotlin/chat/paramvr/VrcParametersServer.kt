package chat.paramvr

import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import chat.paramvr.auth.*
import chat.paramvr.avatar.avatarRoutes
import chat.paramvr.avatar.basicAvatarRoutes
import chat.paramvr.invite.inviteRoutes
import chat.paramvr.parameter.basicParameterRoutes
import chat.paramvr.parameter.manageParameterRoutes
import chat.paramvr.usersettings.userSettingsRoutes
import chat.paramvr.ws.Sockets.vrcParameterSockets
import chat.paramvr.ws.TriggerSessionDAO
import chat.paramvr.ws.wsRoutes
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import java.io.File
import kotlin.time.Duration.Companion.minutes

val conf = AppConfig()

fun main(args: Array<String>) {
    if (conf.isProduction()) {
        EngineMain.main(args)
    } else {
        embeddedServer(
            Netty,
            serverConfig {
                developmentMode = true
                module(Application::module)
            },
            configure = {
                connector {
                    host = "0.0.0.0"
                    port = conf.getPort()
                }
            }
        ).start(wait = true)
    }
}

fun Application.module() {

    log.info("prod = ${conf.isProduction()}")

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Server is exiting.")
        TriggerSessionDAO().deleteAllTriggerSessions()
    })

    install(ContentNegotiation) {
        gson {
            serializeNulls()
            if (!conf.isProduction()) {
                setPrettyPrinting()
            }
        }
    }

    install(Sessions) {
        cookie<VrcParametersSession>("SESSION", storage = SessionStorageMemory()) {
            cookie.secure = conf.useSsl()
        }
    }

    install(Authentication) {
        installVrcParametersAuth()
    }

    install(WebSockets) {
        contentConverter = GsonWebsocketContentConverter()
        timeout = 5.minutes
        maxFrameSize = 1024 * 1024 * 1024 // 1 MiB
    }

    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
    }

    val origin =  conf.getOrigin()
    environment.log.info("Origin = $origin")

    if (!conf.isProduction()) {
        install(CORS) {
            allowHost(origin, listOf(if (conf.isProduction()) "https" else "http"))
            allowCredentials = true
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
        }
    }

    install(CachingHeaders) {
        options { _, _ ->
            CachingOptions(
                CacheControl.NoStore(CacheControl.Visibility.Private)
            )
        }
    }

    routing {

        staticFiles("/", File("public"))
        staticFiles("/p/{targetUser}", File("public"))
        staticFiles("/nv/{invite}", File("public"))
        staticFiles("/f/avatar", File("uploads/avatars"))
        staticFiles("/f/parameter", File("uploads/parameters"))

        authenticate("Form") {
            authRoutes()
            manageParameterRoutes()
            avatarRoutes()
            inviteRoutes()
            userSettingsRoutes()
        }

        authenticate("Basic-ListenKey") {
            route("client") {
                basicAvatarRoutes()
                basicParameterRoutes()
                uploadLogRoute()
            }
        }

        get("/discord") { call.respond(conf.getDiscordInvite()) }

        vrcParameterSockets()
        wsRoutes()
    }
}