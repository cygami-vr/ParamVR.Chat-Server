package chat.paramvr

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.file.Files
import java.nio.file.Paths

fun Route.uploadLogRoute() {
    tryPost("/log") {

        val targetUser = call.attributes[AttributeKey("target-user")] as String
        val log = receiveMultipartFile()

        log("$targetUser : Handling upload for Log ${log.name}")

        if (log.name != null) {

            val path = Paths.get("uploads/logs").resolve("$targetUser-${log.name}.log")
            log("Saving file to $path")

            Files.write(path, log.data!!)
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}