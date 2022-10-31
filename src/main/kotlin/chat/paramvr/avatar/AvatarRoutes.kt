package chat.paramvr.avatar

import chat.paramvr.auth.userId
import chat.paramvr.log
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.file.Files

val dao = AvatarDAO()

fun Route.avatarRoutes() {
    route("avatar") {
        get {
            call.respond(dao.retrieveAvatars(userId()))
        }
        post {
            val toInsert = call.receive<PostAvatar>()
            log("UUID=${toInsert.vrcUuid} Name=${toInsert.name}")
            dao.insertAvatar(userId(), toInsert)
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            val toDelete = call.receive<DeleteAvatar>()
            call.application.environment.log.info("DELETE /avatar userId=${userId()} ID=${toDelete.id}")

            if (dao.deleteAvatar(userId(), toDelete.id)) {
                val img = Avatar.getImage(toDelete.id)
                if (img != null) {
                    Files.delete(img)
                    Files.delete(Avatar.getDirectory(toDelete.id))
                }
            }

            call.respond(HttpStatusCode.NoContent)
        }
        post("image") {

            var avatarId: Long? = null
            var fileName: String? = null
            var fileBytes: ByteArray? = null

            call.receiveMultipart().forEachPart {
                when (it) {
                    is PartData.FormItem -> {
                        avatarId = it.value.toLong()
                    }
                    is PartData.FileItem -> {
                        fileName = it.originalFileName
                        fileBytes = it.streamProvider().readBytes()
                    }
                    else -> {
                        call.application.environment.log.warn("Unhandled multipart type for $it")
                    }
                }
            }

            call.application.environment.log.info("Handling upload for $avatarId / $fileName")

            if (avatarId != null && fileName != null) {

                if (!dao.validateAvatarUserId(userId(), avatarId!!)) {
                    call.respond(HttpStatusCode.NoContent)
                    return@post
                }

                val extension = fileName!!.substring(fileName!!.lastIndexOf('.'))
                val path = Avatar.getDirectory(avatarId!!).resolve("image$extension")
                call.application.environment.log.info("Saving file to $path")
                Files.createDirectory(path.parent)
                Files.write(path, fileBytes!!)
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

fun Route.basicAvatarRoutes() {
    route ("avatar") {
        post {
            val userId = call.attributes[AttributeKey("user-id")] as Long
            val toInsert = call.receive<PostAvatar>()
            dao.insertAvatar(userId, toInsert)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}