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
import java.time.Duration

val conf = AppConfig()

fun main(args: Array<String>) {
    if (conf.isProduction()) {
        EngineMain.main(args)
    } else {
        val env = applicationEngineEnvironment {
            developmentMode = true
            module { module() }
            connector {
                host = "0.0.0.0"
                port = conf.getPort()
            }
        }
        embeddedServer(Netty, env).start(true)
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
        timeout = Duration.ofMinutes(5)
        maxFrameSize = 1024 * 1024 * 1024 // 1 MiB
    }

    routing {

        static("/") {
            files("public")
            default("public/index.html")
        }

        static("/p/{targetUser}") {
            files("public")
            default("public/index.html")
        }

        static("/nv/{invite}") {
            files("public")
            default("public/index.html")
        }

        static("/f/avatar") {
            files("uploads/avatars")
        }
        static("/f/parameter") {
            files("uploads/parameters")
        }

        corsRouting()

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