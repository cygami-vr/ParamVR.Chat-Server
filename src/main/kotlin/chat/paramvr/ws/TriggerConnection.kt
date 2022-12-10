package chat.paramvr.ws

import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import chat.paramvr.avatar.Avatar
import chat.paramvr.parameter.ParameterType
import chat.paramvr.ws.listen.ListenConnection
import chat.paramvr.ws.listen.MatchType
import chat.paramvr.ws.listen.filterViewable

class TriggerConnection(targetUser: String, val parameterKeys: List<String>,
                        session: DefaultWebSocketServerSession, var uuid: String, var lastTrigger: Long = -1)
    : Connection(session, targetUser) {

    private fun listener(action: String): ListenConnection? {
        val listener = getListener(targetUser)
        log("Preparing to $action, Listener found = ${listener != null}")
        return listener
    }
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

    suspend fun checkActivity(param: ParameterChange) {
        listener("ping activity")?.let {
            sendGenericParameter("active", it.isActive())
            val time = System.currentTimeMillis()
            if (time - it.lastActivityPing > 30000) {
                it.lastActivityPing = time
                log("Pinging activity")
                it.sendSerialized(param)
            }
        }
    }
    suspend fun sendParameters() {
        listener("send parameters")?.let {
            // Considering vrcOpen == null as true
            if (it.vrcOpen != false) {
                val params = it.getParams().filterViewable(this)
                log("Sending ${params.size} parameters")
                sendSerialized(params)
            } else {
                send(Frame.Text("[]"))
            }
        }
    }

    suspend fun lock(lock: ParameterLock) {
        listener("trigger ParameterLock")?.let { listenCon ->

            val parameter = listenCon.getParam(lock)
            val matchType = parameter?.matchModify(parameterKeys, uuid)
            log("Attempting to trigger ParameterLock ${lock.name} = ${lock.locked}, MatchType = $matchType")

            if (matchType == MatchType.GOOD) {

                val valid = parameterDAO.setParameterLock(listenCon.userId, parameter.parameterId!!, lock.locked, uuid)
                log("Attempted to update lock in database, valid = $valid")

                if (valid) {

                    parameter.lockKey = if (lock.locked) uuid else null

                    connections.target(targetUser).forEach { triggerCon ->

                        if (parameter.canView(triggerCon.parameterKeys)) {
                            triggerCon.sendParameterLock(lock.name, lock.locked, if (uuid == triggerCon.uuid) uuid else null)
                        }
                    }
                }
            }
        }
    }

    suspend fun trigger(paramChg: ParameterChange) {
        listener("trigger ParameterChange")?.let {
            val param = it.getParam(paramChg)
            val matchType = param?.matchModify(parameterKeys, paramChg.value, uuid)
            log("Attempting to trigger ParameterChange ${paramChg.name} = ${paramChg.value}, MatchType = $matchType")
            if (matchType == MatchType.GOOD) {
                it.sendSerialized(paramChg)
                if (param.type == ParameterType.BUTTON.id) {
                    it.scheduleButtonMaxPress(param)
                }
            }
        }
    }

    suspend fun sendFullStatus() {
        listener("send full status")?.let { listener ->
            val obj = JsonObject()
            val status = JsonObject()
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

    suspend fun sendMutatedParams() {
        listener("send mutated params")?.let { listener ->
            listener.getParams().forEach {

                val value = listener.mutatedParams[it.name]
                val matchType = if (value != null) it.matchView(parameterKeys) else null
                log("Attempting to send mutated param ${it.name} = $value," +
                        " matchType = $matchType, vrcOpen = ${listener.vrcOpen}")

                // Considering vrcOpen == null as true
                if (listener.vrcOpen != false && matchType == MatchType.GOOD) {
                    sendGenericParameter(it.name, listener.mutatedParams[it.name])
                }
            }
        }
    }

    suspend fun sendLockedParams() {
        listener("send locked params")?.let { listener ->
            listener.getParams().filter { it.lockKey != null }.forEach {
                val matchType = it.matchView(parameterKeys)
                log("Attempting to send locked param ${it.name}," +
                        " matchType = $matchType, vrcOpen = ${listener.vrcOpen}")

                // Considering vrcOpen == null as true
                if (listener.vrcOpen != false && matchType == MatchType.GOOD) {
                    sendParameterLock(it.name, it.lockKey != null, if (it.lockKey == uuid) uuid else null )
                }
            }
        }
    }
    suspend fun sendGenericParameter(name: String, value: Any?) {
        log("Sending generic $name = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        setProperty(toSend, value)
        toSend.addProperty("type", "value")
        sendSerialized(toSend)
    }

    suspend fun sendParameterLock(name: String, locked: Boolean, lockKey: String?) {
        log("Sending lock $name = $locked")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        toSend.addProperty("locked", locked)
        toSend.addProperty("type", "lock")
        lockKey?.let {
            toSend.addProperty("lockKey", it)
        }
        sendSerialized(toSend)
    }
}