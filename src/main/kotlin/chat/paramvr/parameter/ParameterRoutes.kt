package chat.paramvr.parameter

import chat.paramvr.*
import chat.paramvr.auth.userId
import chat.paramvr.ws.Sockets.getListener
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.file.Files
import java.util.*

private val dao = ParameterDAO()

fun Route.manageParameterRoutes() {
    route("parameter") {
        route("listen-key") {
            tryPost {
                log("") // This is intentional, it is just to get the default logged info
                val key = UUID.randomUUID().toString()
                dao.updateListenKey(userId(), key)
                call.respond(key)
            }
        }
        tryPost {
            val param = call.receive<PostParameter>()
            log("avatarId = ${param.avatarId} parameterId = ${param.parameterId} name = ${param.name}")
            if (param.parameterId == null) {
                if (param.name.length > 32) {
                    call.respond(HttpStatusCode.BadRequest, "Parameter name cannot be longer than 32 characters.")
                    return@tryPost
                }
                if (param.name.contains(" ")) {
                    call.respond(HttpStatusCode.BadRequest, "Parameter name cannot contain whitespace.")
                    return@tryPost
                }
                dao.insertParameter(userId(), param)
            } else {
                dao.updateParameter(userId(), param)
            }
            clearListenerParamCache()
            call.respond(HttpStatusCode.NoContent)
        }
        tryGet {
            val params = dao.retrieveParameters(userId())
            call.respond(params)
        }
        tryDelete {
            val body = call.receive<DeleteParameter>()
            log("parameterId = ${body.parameterId}")
            dao.deleteParameter(userId(), body.parameterId)
            clearListenerParamCache()
            call.respond(HttpStatusCode.NoContent)
        }
        route("value") {
            tryPost {
                val value = call.receive<PostParameterValue>()
                log("parameterId = ${value.parameterId} value = ${value.value}")
                dao.insertUpdateParameterValue(userId(), value)
                clearListenerParamCache()
                call.respond(HttpStatusCode.NoContent)
            }
            tryDelete {
                val body = call.receive<DeleteParameterValue>()
                log("userId = ${userId()} parameterId = ${body.parameterId} value = ${body.value}")
                if (dao.deleteParameterValue(userId(), body.parameterId, body.value)) {

                    val img = ParameterWithImage.getImage(body.parameterId)
                    if (img != null) {
                        Files.delete(img)
                        Files.delete(ParameterWithImage.getDirectory(body.parameterId))
                    }
                }
                clearListenerParamCache()
                call.respond(HttpStatusCode.NoContent)
            }
        }
        tryPost("/order") {
            val body = call.receive<PostParameterOrder>()
            log("") // This is intentional, it is just to get the default logged info
            dao.updateParameterOrder(userId(), body.parameterIds)
            clearListenerParamCache()
            call.respond(HttpStatusCode.NoContent)
        }
        route("image") {
            tryPost {

                val img = receiveMultipartFile()
                log("Handling upload for Parameter $img")

                if (img.hasData()) {

                    if (!dao.validateParameterUserId(userId(), img.id!!)) {
                        call.respond(HttpStatusCode.NoContent)
                        return@tryPost
                    }

                    val path = ParameterWithImage.getDirectory(img.id).resolve("image.png")
                    log("Saving file to $path")
                    if (!Files.exists(path.parent)) {
                        Files.createDirectory(path.parent)
                    }
                    scale(img.data!!, 128, path)
                    clearListenerParamCache()
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            tryDelete {
                val id = call.receiveText().toLong()
                log("id = $id")
                val path = ParameterWithImage.getDirectory(id).resolve("image.png")
                Files.deleteIfExists(path)
                clearListenerParamCache()
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

fun Route.basicParameterRoutes() {
    route ("parameter") {
        tryPost {
            val userId = call.attributes[AttributeKey("user-id")] as Long
            val targetUser = call.attributes[AttributeKey("target-user")] as String

            val listener = getListener(targetUser)
            val avatarId = listener?.avatar?.id

            log("BASIC (Step 1) targetUser = $targetUser, avatarId = $avatarId")

            if (avatarId == null) {
                call.application.environment.log.info("$targetUser : No current avatar, cannot add parameters")
                call.respond(HttpStatusCode.BadRequest)
                return@tryPost
            }

            val param = call.receive<BasicPostParameter>()
            log("BASIC (Step 2) name = ${param.name} values = ${param.values}")
            dao.importParameter(userId, avatarId, param)

            listener.avatarParams = null // invalidate the cache

            call.respond(HttpStatusCode.NoContent)
        }
        tryPost("emergency-unlock") {
            val userId = call.attributes[AttributeKey("user-id")] as Long
            val targetUser = call.attributes[AttributeKey("target-user")] as String
            log("BASIC targetUser = $targetUser, userID = $userId")
            dao.unlockAll(userId)
            getListener(targetUser)?.let {
                it.avatarParams = null // invalidate the cache
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}