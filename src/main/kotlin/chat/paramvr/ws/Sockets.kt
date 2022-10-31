package chat.paramvr.ws

import com.google.gson.JsonArray
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import chat.paramvr.parameter.ParameterDAO
import chat.paramvr.ws.listen.ListenConnection
import java.util.*

data class VrcParameter(val name: String, val value: String, val dataType: Short)

val listeners: MutableSet<ListenConnection> = Collections.synchronizedSet(HashSet())
val connections: MutableSet<TriggerConnection> = Collections.synchronizedSet(HashSet())

val parameterDAO = ParameterDAO()

const val CLIENT_PROTOCOL_VERSION = "0.2"

inline fun DefaultWebSocketServerSession.log(s: String) = call.application.environment.log.info(s)
inline fun DefaultWebSocketServerSession.warn(s: String) = call.application.environment.log.warn(s)

fun getListener(targetUser: String) = listeners.target(targetUser.lowercase()).firstOrNull()
fun Route.vrcParameterSockets() {

    authenticate("Basic-ListenKey") {
        webSocket("/parameter-listen") {

            val targetUser = call.attributes[AttributeKey("target-user")] as String
            val userId = call.attributes[AttributeKey("user-id")] as Long

            send(Frame.Text(CLIENT_PROTOCOL_VERSION))
            val protocol = (incoming.receive() as Frame.Text).readText()
            log("$targetUser connecting with client protocol version $protocol")
            if (protocol != CLIENT_PROTOCOL_VERSION) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Client protocol out of date"))
            }

            val duplicates = listeners.target(targetUser)
            if (duplicates.isNotEmpty()) {
                warn("$targetUser : There are already ${duplicates.size} listeners connected, closing them now")
                duplicates.forEach {
                    it.session.close(CloseReason(CloseReason.Codes.NORMAL, "Duplicate listener"))
                }
                listeners.removeIf { it.targetUser == targetUser }
            }

            val con = ListenConnection(this, targetUser.lowercase(), userId)
            listeners += con

            con.setAvatar((incoming.receive() as Frame.Text).readText())
            con.notifyConnected(true)

            try {
                while (true) {
                    val updates = receiveDeserialized<JsonArray>()
                    con.handleListenerUpdates(updates)
                }
            } catch (e: ClosedReceiveChannelException) {
                listeners.removeIf { it === con }
                log("$targetUser : ListenConnection closed")
                con.notifyConnected(false)
            } catch (t: Throwable) {
                con.logAndClose("Unexpected error in ListenConnection", t)
                con.notifyConnected(false)
            }
        }
    }

    webSocket("/parameter-trigger") {
        val targetUser = (incoming.receive() as Frame.Text).readText()
        val exists = parameterDAO.userExists(targetUser)
        if (!exists) {
            close(CloseReason(CloseReason.Codes.NORMAL, "No such user"))
            return@webSocket
        }

        if (connections.count { it.targetUser == targetUser } > 25) {
            warn("$targetUser : too many trigger connections, rejecting this one")
            close(CloseReason(CloseReason.Codes.NORMAL, "Too many connections"))
            return@webSocket
        }

        val parameterKeys = (incoming.receive() as Frame.Text).readText().split(',')

        val con = TriggerConnection(targetUser, parameterKeys, this)
        connections += con
        log("$targetUser : New trigger connection, Keys = $parameterKeys")
        con.sendParameters()

        val available = listeners.any { it.targetUser == targetUser }
        con.sendGenericParameter("connected", available)

        if (available) {
            con.sendFullStatus()
        }

        try {
            while (true) {
                val param = receiveDeserialized<VrcParameter>()
                if (con.checkSpam())
                    continue

                log("$targetUser : Received VrcParameter ${param.name} = ${param.value}")

                if (param.name == "chat-paramvr-activity") {
                    con.checkActivity(param)
                } else {
                    con.trigger(param)
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            connections.removeIf { it === con }
            log("$targetUser : TriggerConnection closed")
        } catch (t: Throwable) {
            con.logAndClose("Unexpected error in TriggerConnection", t)
        }
    }
}
