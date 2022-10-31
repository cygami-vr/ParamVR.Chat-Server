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
    post("/log") {

        val targetUser = call.attributes[AttributeKey("target-user")] as String
        var fileName: String? = null
        var fileBytes: ByteArray? = null

        call.receiveMultipart().forEachPart {
            when (it) {
                is PartData.FileItem -> {
                    fileName = it.originalFileName
                    fileBytes = it.streamProvider().readBytes()
                }
                else -> {
                    call.application.environment.log.warn("$targetUser : Unhandled multipart type for $it")
                }
            }
        }

        call.application.environment.log.info("$targetUser : Handling upload for $fileName")

        if (fileName != null) {

            val path = Paths.get("uploads/logs").resolve("$targetUser-$fileName.log")
            call.application.environment.log.info("Saving file to $path")

            Files.write(path, fileBytes!!)
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}