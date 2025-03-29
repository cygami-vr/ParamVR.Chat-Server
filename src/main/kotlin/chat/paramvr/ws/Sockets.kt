package chat.paramvr.ws

import chat.paramvr.invite.InviteDAO
import chat.paramvr.parameter.ParameterDAO
import chat.paramvr.usersettings.UserSettingsDAO
import com.google.gson.JsonArray
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.*

data class TriggerMessage(val lock: ParameterLock?, val change: ParameterChange?, val vrcUuid: String?)
data class ParameterLock(val name: String, val locked: Boolean)
data class ParameterChange(val name: String, val value: String, val dataType: Short)
data class ParameterChangeWrapped(val parameter: ParameterChange)
data class AvatarChange(val vrcUuid: String)
data class Parameters(val parameters: List<Parameter>)

object Sockets {

    val listeners: MutableSet<ListenConnection> = Collections.synchronizedSet(HashSet())
    val connections: MutableSet<TriggerConnection> = Collections.synchronizedSet(HashSet())

    private val inviteDAO = InviteDAO()
    val sessionDAO = TriggerSessionDAO()
    val parameterDAO = ParameterDAO()

    private const val CLIENT_PROTOCOL_VERSION = "0.3"

    inline fun DefaultWebSocketServerSession.trace(s: String) {
        if (call.application.environment.log.isTraceEnabled)
            call.application.environment.log.trace(s)
    }
    inline fun DefaultWebSocketServerSession.debug(s: String) {
        if (call.application.environment.log.isDebugEnabled)
            call.application.environment.log.debug(s)
    }
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

                val con = ListenConnection(this, targetUser.lowercase(), userId, inviteDAO.retrieveMinimalInvites(userId))
                con.settings = UserSettingsDAO().retrieveSettings(userId)
                InviteExpirationHandler.handleListener(this, con)
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

            // This is a work-around for an issue where an Apache proxy server
            // might not flush the HTTP 101 Switching Protocols response.
            send(Frame.Text("\"Connected\""))

            val uuid = (incoming.receive() as Frame.Text).readText()
            val session = sessionDAO.retrieveTriggerSession(uuid)

            if (session == null) {
                close(CloseReason(CloseReason.Codes.NORMAL, "No trigger session"))
                return@webSocket
            }

            val targetUser = session.targetUser

            if (connections.count { it.targetUser == targetUser } >= 25) {
                warn("$targetUser : too many trigger connections, rejecting this one")
                close(CloseReason(CloseReason.Codes.NORMAL, "Too many connections"))
                return@webSocket
            }

            val con = TriggerConnection(session, this)

            connections += con
            log("$targetUser : New trigger connection, Session ID = ${session.uuid}, Client ID = ${session.clientId}")
            con.sendParameters()

            val listener = listeners.find { it.targetUser == targetUser }
            con.sendStatus("connected", listener != null)

            if (listener != null) {
                con.sendFullStatus()
                con.sendChangeableAvatars()
                con.checkActivity()
            }

            try {
                while (true) {
                    receiveTriggerMessage(con)
                }
            } catch (e: ClosedReceiveChannelException) {
                connections.removeIf { it === con }
                log("$targetUser : TriggerConnection closed")
                sessionDAO.deleteTriggerSession(session.uuid)
            } catch (t: Throwable) {
                sessionDAO.deleteTriggerSession(session.uuid)
                con.logAndClose("Unexpected error in TriggerConnection", t)
            }
        }
    }

    private suspend fun DefaultWebSocketServerSession.receiveTriggerMessage(con: TriggerConnection) {
        val msgs = receiveDeserialized<Array<TriggerMessage>>()
        if (con.checkSpam())
            return

        for (msg in msgs) {
            if (msg.lock != null) {
                val lock = msg.lock
                log("${con.targetUser} : Received ParameterLock ${lock.name} = ${lock.locked}")
                con.lock(lock)
            } else if (msg.change != null) {
                val change = msg.change
                debug("${con.targetUser} : Received ParameterChange ${change.name} = ${change.value}")

                if (change.name == "chat-paramvr-activity") {
                    con.checkActivity(ParameterChangeWrapped(change))
                } else {
                    con.trigger(change)
                }
            } else if (msg.vrcUuid != null) {
                debug("${con.targetUser} ?: Received AvatarChange to ${msg.vrcUuid}")
                con.changeAvatar(msg.vrcUuid)
            } else {
                warn("${con.targetUser} : TriggerMessage is empty")
            }
        }
    }

}