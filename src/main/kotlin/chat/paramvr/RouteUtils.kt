package chat.paramvr

import chat.paramvr.auth.VrcParametersSession
import chat.paramvr.auth.vrcParametersSession
import chat.paramvr.ws.Sockets.getListener
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*

fun PipelineContext<Unit, ApplicationCall>.log(msg: String) {
    val userId = call.sessions.get<VrcParametersSession>()?.userId
    call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri} userId = $userId $msg")
}

fun PipelineContext<Unit, ApplicationCall>.clearListenerParamCache() {
    val listener = getListener(vrcParametersSession().userName)
    listener?.avatarParams = null
    listener?.changeableAvatars = null
}

class MultipartFile(val id: Long?, val name: String?, val data: ByteArray?) {
    override fun toString() = "$id / $name"
    fun hasData() = id != null && name != null
}

suspend fun PipelineContext<Unit, ApplicationCall>.receiveMultipartFile(): MultipartFile {

    var id: Long? = null
    var fileName: String? = null
    var fileBytes: ByteArray? = null

    call.receiveMultipart().forEachPart {
        when (it) {
            is PartData.FormItem -> {
                id = it.value.toLong()
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

    return MultipartFile(id, fileName, fileBytes)
}

suspend fun PipelineContext<Unit, ApplicationCall>.handleThrowable(t: Throwable) {
    val userId = call.sessions.get<VrcParametersSession>()?.userId
    call.application.environment.log.error("Uncaught throwable in route ${call.request.httpMethod.value} ${call.request.uri} userId = $userId", t)
    call.respond(HttpStatusCode.InternalServerError)
}

fun Route.tryGet(body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return get {
        try {
            body.invoke(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}

fun Route.tryGet(route: String, body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return get(route) {
        try {
            body.invoke(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}

fun Route.tryPost(body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return post() {
        try {
            body(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}

fun Route.tryPost(route: String, body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return post(route) {
        try {
            body(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}

fun Route.tryDelete(body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return delete() {
        try {
            body(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}

fun Route.tryDelete(route: String, body: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit): Route {
    return delete(route) {
        try {
            body(this, Unit)
        } catch (t: Throwable) {
            handleThrowable(t)
        }
    }
}