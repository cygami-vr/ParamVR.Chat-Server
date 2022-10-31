package chat.paramvr.parameter

import chat.paramvr.auth.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import chat.paramvr.auth.vrcParametersSession
import chat.paramvr.avatar.Avatar
import chat.paramvr.ws.getListener
import io.ktor.http.content.*
import io.ktor.util.*
import java.nio.file.Files
import java.util.*

private val dao = ParameterDAO()

fun Route.manageParameterRoutes() {
    route("parameter") {
        route("listen-key") {
            post {
                val key = UUID.randomUUID().toString()
                dao.updateListenKey(userId(), key)
                call.respond(key)
            }
        }
        post {
            val param = call.receive<PostParameter>()
            if (param.parameterId == null) {
                dao.insertParameter(userId(), param)
            } else {
                dao.updateParameter(userId(), param)
            }
            getListener(vrcParametersSession().userName)?.avatarParams = null
            call.respond(HttpStatusCode.NoContent)
        }
        get {
            val params = dao.retrieveParameters(userId())
            call.respond(params)
        }
        delete {
            val body = call.receive<DeleteParameter>()
            dao.deleteParameter(userId(), body.parameterId)
            getListener(vrcParametersSession().userName)?.avatarParams = null
            call.respond(HttpStatusCode.NoContent)
        }
        route("value") {
            post {
                val value = call.receive<PostParameterValue>()
                dao.insertUpdateParameterValue(userId(), value)
                getListener(vrcParametersSession().userName)?.avatarParams = null
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                val body = call.receive<DeleteParameterValue>()
                if (dao.deleteParameterValue(userId(), body.parameterId, body.value)) {

                    val img = GetParameter.getImage(body.parameterId)
                    if (img != null) {
                        Files.delete(img)
                        Files.delete(Avatar.getDirectory(body.parameterId))
                    }
                }
                getListener(vrcParametersSession().userName)?.avatarParams = null
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post("/image") {

            var parameterId: Long? = null
            var fileName: String? = null
            var fileBytes: ByteArray? = null

            call.receiveMultipart().forEachPart {
                when (it) {
                    is PartData.FormItem -> {
                        parameterId = it.value.toLong()
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

            call.application.environment.log.info("Handling upload for $parameterId / $fileName")

            if (parameterId != null && fileName != null) {

                if (!dao.validateParameterUserId(userId(), parameterId!!)) {
                    call.respond(HttpStatusCode.NoContent)
                    return@post
                }

                val extension = fileName!!.substring(fileName!!.lastIndexOf('.'))
                val path = GetParameter.getDirectory(parameterId!!).resolve("image$extension")
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

fun Route.basicParameterRoutes() {
    route ("parameter") {
        post {
            val userId = call.attributes[AttributeKey("user-id")] as Long
            val targetUser = call.attributes[AttributeKey("target-user")] as String

            val listener = getListener(targetUser)
            val avatarId = listener?.avatar?.id

            if (avatarId == null) {
                call.application.environment.log.info("$targetUser : No current avatar, cannot add parameters")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val param = call.receive<BasicPostParameter>()
            dao.importParameter(userId, avatarId, param)

            listener.avatarParams = null

            call.respond(HttpStatusCode.NoContent)
        }
    }
}