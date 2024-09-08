package chat.paramvr

import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.corsRouting() {

    val isProduction = conf.isProduction()

    if (!isProduction) {
        options("/{url...}") {
            call.request.headers["Access-Control-Request-Method"]?.let {
                call.response.header("Access-Control-Allow-Methods", it)
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    val origin =  conf.getOrigin()
    environment?.log?.info("Origin = $origin")

    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("Access-Control-Allow-Origin", origin)
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.cacheControl(CacheControl.NoStore(CacheControl.Visibility.Private))
        if (!isProduction) {
            call.response.headers.append("Access-Control-Allow-Credentials", "true")
            call.response.headers.append("Access-Control-Allow-Headers", "content-type")
        }
    }
}