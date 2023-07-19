package chat.paramvr.ws

import chat.paramvr.ws.Sockets.listeners
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*

abstract class Connection(val session: DefaultWebSocketServerSession, val targetUser: String) {

    inline fun log(s: String) = session.application.environment.log.info("$targetUser : $s")
    inline fun warn(s: String) = session.application.environment.log.warn("$targetUser : $s")
    inline fun error(s: String, t: Throwable) = session.application.environment.log.error("$targetUser : $s", t)

    suspend inline fun logAndClose(msg: String, t: Throwable) {
        error(msg, t)
        close(msg)
    }

    open suspend fun close(msg: String) {
        listeners.removeIf { it === this }
        session.close(CloseReason(CloseReason.Codes.NORMAL, msg))
    }

    suspend inline fun send(frame: Frame) {
        try {
            session.send(frame)
        } catch (ex: Exception) {
            logAndClose("Error occurred sending message", ex)
        }
    }

    suspend inline fun <reified T: Any> sendSerialized(data: T) {
        try {
            session.sendSerialized(data)
        } catch (ex: Exception) {
            logAndClose("Error occurred sending serialized data", ex)
        }
    }

    fun setProperty(obj: JsonObject, value: Any?, property: String = "value") {
        when (value) {
            is Boolean -> obj.addProperty(property, value)
            is String -> obj.addProperty(property, value)
            is Number -> obj.addProperty(property, value)
            is JsonElement -> obj.add(property, value)
            else -> warn("Unknown property type for $value")
        }
    }
}

fun <T: Connection> Iterable<T>.target(targetUser: String) = filter { it.targetUser == targetUser }