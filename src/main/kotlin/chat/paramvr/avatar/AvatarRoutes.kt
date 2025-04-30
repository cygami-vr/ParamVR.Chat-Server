package chat.paramvr.avatar

import chat.paramvr.*
import chat.paramvr.auth.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.file.Files

val dao = AvatarDAO()

fun Route.avatarRoutes() {
    route("avatar") {
        tryGet {
            call.respond(dao.retrieveAvatars(userId()))
        }
        tryPost {
            val avatar = call.receive<PostAvatar>()
            log("ID = ${avatar.id} UUID = ${avatar.vrcUuid} Name = ${avatar.name}")
            if (avatar.id == null) {
                dao.insertAvatar(userId(), avatar)
            } else {
                dao.updateAvatar(userId(), avatar)
            }
            getListener()?.avatar?.let {
                if (it.vrcUuid == avatar.vrcUuid) {
                    it.title = avatar.title
                }
            }
            // Since UUID could have changed, we need to clear cache.
            clearListenerParamCache()
            call.respond(HttpStatusCode.NoContent)
        }
        tryDelete {
            val toDelete = call.receive<DeleteAvatar>()
            log("ID = ${toDelete.id}")

            if (dao.deleteAvatar(userId(), toDelete.id)) {
                val img = Avatar.getImage(toDelete.id)
                if (img != null) {
                    Files.delete(img)
                    Files.delete(Avatar.getDirectory(toDelete.id))
                }
            }

            call.respond(HttpStatusCode.NoContent)
        }
        route("image") {
            tryPost {

                val img = receiveMultipartFile()

                log("Handling upload for Avatar $img")

                if (img.hasData()) {

                    if (!dao.validateAvatarUserId(userId(), img.id!!)) {
                        call.respond(HttpStatusCode.NoContent)
                        return@tryPost
                    }

                    val path = Avatar.getDirectory(img.id).resolve("image.png")
                    log("Saving file to $path")
                    if (!Files.exists(path.parent)) {
                        Files.createDirectory(path.parent)
                    }

                    scale(img.data!!, 512, path)
                    clearListenerParamCache()
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            tryDelete {
                val id = call.receiveText().toLong()
                log("id = $id")
                val path = Avatar.getDirectory(id).resolve("image.png")
                Files.deleteIfExists(path)
                clearListenerParamCache()
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

fun Route.basicAvatarRoutes() {
    route ("avatar") {
        tryPost {
            val userId = call.attributes[AttributeKey("user-id")] as Long
            val toInsert = call.receive<PostAvatar>()
            log("BASIC avatarId = ${toInsert.id} avatarName = ${toInsert.name}")
            dao.insertAvatar(userId, toInsert)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}