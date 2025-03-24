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
import com.google.gson.JsonArray

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
        debug("Preparing to $action, Listener found = ${listener != null}")
        return listener
    }

    fun getListener() = getListener(targetUser)

    fun checkSpam(): Boolean {
        // Due to the client-side rate-limit of 100ms, this should only occur
        // if someone is intentionally attempting to bypass that rate-limit.
        val time = System.currentTimeMillis()
        return if (time - lastTrigger < 75) {
            debug("Ignoring trigger due to spam")
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
                debug("Pinging activity")
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
                sendSerialized(Parameters(params))
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
            result.listener().sendSerialized(ParameterChangeWrapped(change))
            if (result.parameter().type == ParameterType.BUTTON.id) {
                result.listener().scheduleButtonMaxPress(result.parameter())
            }
        }
    }

    suspend fun changeAvatar(vrcUuid: String) {
        listener("change avatar") { listener ->

            val canChange = perms.canChange(listener.avatar!!)
            val allowChangeFrom = listener.avatar?.allowChange == "Y"
            val allowChangeTo = listener.getChangeableAvas().any { it.vrcUuid == vrcUuid }
            var offCooldown = false
            listener.settings?.let {
                offCooldown = System.currentTimeMillis() - listener.lastAvatarChange > it.avatarChangeCooldown * 1000
            }

            log("Attempting to change avatar, " +
                    "can change = $canChange, allow from = $allowChangeFrom, " +
                    "allow to = $allowChangeTo, last change = ${listener.lastAvatarChange}")

            if (canChange && allowChangeFrom && allowChangeTo && offCooldown) {
                listener.lastAvatarChange = System.currentTimeMillis()
                listener.sendSerialized(AvatarChange(vrcUuid))
            }
        }
    }

    suspend fun sendFullStatus() {
        listener("send full status") { listener ->
            val obj = JsonObject()
            val status = JsonObject()
            obj.addProperty("type", "status")
            obj.add("status", status)

            val avatar = JsonObject()
            listener.avatar?.let {
                avatar.addProperty("name", it.name)
                avatar.addProperty("image", Avatar.getHref(it.id))
                avatar.addProperty("vrcUuid", it.vrcUuid)
            }
            status.add("avatar", avatar)

            status.addProperty("muted", listener.muted)
            status.addProperty("isPancake", listener.isPancake)
            status.addProperty("afk", listener.afk)
            status.addProperty("active", listener.isActive())
            status.addProperty("vrcOpen", listener.vrcOpen)
            listener.settings?.let {
                status.addProperty("avatarChangeCooldown",
                    it.avatarChangeCooldown * 1000 - (System.currentTimeMillis() - listener.lastAvatarChange))
                status.addProperty("colorPrimary", it.colorPrimary)
            }

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

    suspend fun sendChangeableAvatars() {
        listener("send changeable avatars") { listener ->

            val changeableAvatars = JsonObject()
            val list = JsonArray()
            // If missing the permission to change _to_ the current avatar,
            // we will also prevent the connection from changing to other avatars.
            val allowChange = listener.avatar?.allowChange == "Y"
            val canChange = listener.avatar?.let { perms.canChange(it) } ?: false

            if (allowChange && canChange) {
                listener.getChangeableAvas().filter {
                    listener.avatar?.vrcUuid != it.vrcUuid && perms.canChange(it)
                }.forEach { ava ->
                    val obj = JsonObject()
                    obj.addProperty("vrcUuid", ava.vrcUuid)
                    obj.addProperty("name", ava.name)
                    obj.addProperty("image", ava.image)
                    list.add(obj)
                }
            }

            changeableAvatars.add("changeableAvatars", list)
            sendSerialized(changeableAvatars)
        }
    }

    suspend fun sendGenericParameter(name: String, value: Any?) = sendGeneric(name, value, "parameter", "value")

    suspend fun sendStatus(name: String, value: Any?) {
        debug("Sending status $name = $value")
        val toSend = JsonObject()
        val status = JsonObject()
        setProperty(status, value, name)
        toSend.add("status", status)
        sendSerialized(toSend)
    }

    private suspend fun sendGeneric(name: String, value: Any?, type: String) = sendGeneric(name, value, type, null)

    private suspend fun sendGeneric(name: String, value: Any?, type: String, parameterType: String?) {
        debug("Sending generic $name = $value")
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