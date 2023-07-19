package chat.paramvr.ws

import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import chat.paramvr.avatar.Avatar
import chat.paramvr.parameter.ParameterType
import chat.paramvr.ws.Sockets.connections
import chat.paramvr.ws.Sockets.getListener
import chat.paramvr.ws.Sockets.parameterDAO
import chat.paramvr.ws.Sockets.sessionDAO

data class TriggerSession(val uuid: String, val clientId: String, val targetUser: String, val inviteId: Long?)

class TriggerConnection(

    private val triggerSession: TriggerSession,
    session: DefaultWebSocketServerSession,
    private var lastTrigger: Long = -1

) : Connection(session, triggerSession.targetUser) {

    val perms = PermissionValidator(this)

    override suspend fun close(msg: String) {
        super.close(msg)
        sessionDAO.deleteTriggerSession(triggerSession.uuid)
    }

    fun getInviteId() = triggerSession.inviteId
    fun getClientId() = triggerSession.clientId

    suspend fun listener(action: String, callback: suspend (con: ListenConnection) -> Unit)
        = listener(action)?.let { callback(it) }

    fun listener(action: String): ListenConnection? {
        val listener = getListener()
        log("Preparing to $action, Listener found = ${listener != null}")
        return listener
    }

    fun getListener() = getListener(targetUser)

    fun checkSpam(): Boolean {
        // Due to the client-side rate-limit of 100ms, this should only occur
        // if someone is intentionally attempting to bypass that rate-limit.
        val time = System.currentTimeMillis()
        return if (time - lastTrigger < 75) {
            log("Ignoring trigger due to spam")
            true
        } else {
            lastTrigger = time
            false
        }
    }

    suspend fun checkActivity() {
        checkActivity(ParameterChange("chat-paramvr-activity", "", 0))
    }

    suspend fun checkActivity(param: ParameterChange) {
        listener("ping activity") {
            sendStatus("active", it.isActive())
            val time = System.currentTimeMillis()
            if (time - it.lastActivityPing > 30000) {
                it.lastActivityPing = time
                log("Pinging activity")
                it.sendSerialized(param)
            }
        }
    }
    suspend fun sendParameters() {
        listener("send parameters") {
            // Considering vrcOpen == null as true
            if (it.vrcOpen != false) {
                val params = perms.filterViewable()
                log("Sending ${params.size} parameters")
                sendSerialized(params)
            } else {
                send(Frame.Text("[]"))
            }
        }
    }

    suspend fun lock(lock: ParameterLock) {

        val result = perms.validate(lock)
        if (result.valid) {

            log("Triggering ParameterLock ${lock.name} = ${lock.locked}")

            val valid = parameterDAO.setParameterLock(result.listener().userId,
                result.parameter().parameterId!!, lock.locked, getClientId(), getInviteId())
            log("Attempted to update lock in database, valid = $valid")

            if (valid) {
                result.parameter().lockedByClientId = if (lock.locked) getClientId() else null
                propagateLock(result.parameter(), lock)
            }
        }
    }

    private suspend fun propagateLock(param: Parameter, lock: ParameterLock) {
        connections.target(targetUser).forEach { triggerCon ->
            if (triggerCon.perms.canView(param)) {
                triggerCon.sendParameterLock(lock.name, lock.locked,
                    if (getClientId() == triggerCon.getClientId()) getClientId() else null)
            }
        }
    }

    suspend fun trigger(change: ParameterChange) {
        val result = perms.validate(change)
        if (result.valid) {
            log("Triggering ParameterChange ${change.name} = ${change.value}")
            result.listener().sendSerialized(change)
            if (result.parameter().type == ParameterType.BUTTON.id) {
                result.listener().scheduleButtonMaxPress(result.parameter())
            }
        }
    }

    suspend fun sendFullStatus() {
        listener("send full status") { listener ->
            val obj = JsonObject()
            val status = JsonObject()
            obj.addProperty("type", "status")
            obj.add("status", status)

            status.addProperty("avatar", listener.avatar?.name)
            listener.avatar?.id?.let {
                status.addProperty("image", Avatar.getHref(it))
            }

            status.addProperty("muted", listener.muted)
            status.addProperty("isPancake", listener.isPancake)
            status.addProperty("afk", listener.afk)
            status.addProperty("active", listener.isActive())
            status.addProperty("vrcOpen", listener.vrcOpen)

            sendSerialized(obj)
        }
        sendMutatedParams()
        sendLockedParams()
    }

    private suspend fun sendMutatedParams() {
        listener("send mutated params") { listener ->
            listener.getParams().forEach { param ->

                val value = listener.mutatedParams[param.name]
                val canSend = value != null && perms.canView(param)
                log("Attempting to send mutated param ${param.name} = $value," +
                        " can send = $canSend, vrcOpen = ${listener.vrcOpen}")

                // Considering vrcOpen == null as true
                if (listener.vrcOpen != false && canSend) {
                    sendGenericParameter(param.name, value)
                }
            }
        }
    }

    suspend fun sendLockedParams() {
        listener("send locked params") { listener ->
            listener.getParams().filter { it.lockedByClientId != null }.forEach { param ->
                val canView = perms.canView(param)
                log("Attempting to send locked param ${param.name}," +
                        " can view = $canView, vrcOpen = ${listener.vrcOpen}")

                // Considering vrcOpen == null as true
                if (listener.vrcOpen != false && canView) {
                    sendParameterLock(param.name, param.lockedByClientId != null,
                        if (param.lockedByClientId == getClientId()) getClientId() else null )
                }
            }
        }
    }

    suspend fun sendGenericParameter(name: String, value: Any?) = sendGeneric(name, value, "parameter", "value")

    suspend fun sendStatus(name: String, value: Any?) = sendGeneric(name, value, "status")

    private suspend fun sendGeneric(name: String, value: Any?, type: String) = sendGeneric(name, value, type, null)

    private suspend fun sendGeneric(name: String, value: Any?, type: String, parameterType: String?) {
        log("Sending generic $name = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        setProperty(toSend, value)
        toSend.addProperty("type", type)
        parameterType?.let {
            toSend.addProperty("parameter-type", parameterType)
        }
        sendSerialized(toSend)
    }

    private suspend fun sendParameterLock(name: String, locked: Boolean, clientId: String?) {
        log("Sending lock $name = $locked")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        toSend.addProperty("locked", locked)
        toSend.addProperty("type", "parameter")
        toSend.addProperty("parameter-type", "lock")
        clientId?.let {
            toSend.addProperty("lockKey", it)
        }
        sendSerialized(toSend)
    }
}