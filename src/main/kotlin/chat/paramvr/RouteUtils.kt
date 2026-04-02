package chat.paramvr

import chat.paramvr.auth.VrcParametersSession
import chat.paramvr.auth.vrcParametersSession
import chat.paramvr.ws.Sockets.getListener
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.toByteArray

fun RoutingContext.log(msg: String) {
    val userId = call.sessions.get<VrcParametersSession>()?.userId
    call.application.environment.log.info("${call.request.httpMethod.value} ${call.request.uri} userId = $userId $msg")
}

fun RoutingContext.getListener() = getListener(vrcParametersSession().userName)

fun RoutingContext.clearListenerParamCache() {
    val listener = getListener()
    listener?.avatarParams = null
    listener?.changeableAvatars = null
}

class MultipartFile(val id: Long?, val name: String?, val data: ByteArray?) {
    override fun toString() = "$id / $name"
    fun hasData() = id != null && name != null
}

suspend fun RoutingContext.receiveMultipartFile(): MultipartFile {

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
                fileBytes = it.provider().toByteArray()
            }
            else -> {
                call.application.environment.log.warn("Unhandled multipart type for $it")
            }
        }
    }

    return MultipartFile(id, fileName, fileBytes)
}

suspend fun RoutingContext.handleThrowable(t: Throwable) {
    val userId = call.sessions.get<VrcParametersSession>()?.userId
    call.application.environment.log.error("Uncaught throwable in route ${call.request.httpMethod.value} ${call.request.uri} userId = $userId", t)
    call.respond(HttpStatusCode.InternalServerError)
}

fun Route.tryGet(body: suspend RoutingContext.() -> Unit): Route {
    return get {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}

fun Route.tryGet(route: String, body: suspend RoutingContext.() -> Unit): Route {
    return get(route) {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}

fun Route.tryPost(body: suspend RoutingContext.() -> Unit): Route {
    return post {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}

fun Route.tryPost(route: String, body: suspend RoutingContext.() -> Unit): Route {
    return post(route) {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}

fun Route.tryDelete(body: suspend RoutingContext.() -> Unit): Route {
    return delete {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}

fun Route.tryDelete(route: String, body: suspend RoutingContext.() -> Unit): Route {
    return delete(route) {
        try {
            body()
        } catch (ex: Exception) {
            handleThrowable(ex)
        }
    }
}