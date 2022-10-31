package chat.paramvr.ws

import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import chat.paramvr.avatar.Avatar
import chat.paramvr.ws.listen.ListenConnection
import chat.paramvr.ws.listen.MatchType
import chat.paramvr.ws.listen.checkKeys
import chat.paramvr.ws.listen.filterKeys

class TriggerConnection(targetUser: String, val parameterKeys: List<String>,
                        session: DefaultWebSocketServerSession, var lastTrigger: Long = -1)
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

    suspend fun checkActivity(param: VrcParameter) {
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
            if (it.vrcOpen != false) {
                val params = it.getParams().filterKeys(parameterKeys)
                log("Sending ${params.size} parameters")
                sendSerialized(params)
            } else {
                send(Frame.Text("[]"))
            }
        }
    }

    suspend fun trigger(param: VrcParameter) {
        listener("trigger VrcParameter")?.let {
            val matchType = it.getParams().checkKeys(this, param)
            log("Attempting to trigger VrcParameter ${param.name} = ${param.value}, MatchType = $matchType")
            if (matchType == MatchType.GOOD) {
                it.sendSerialized(param)
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
            status.addProperty("isPancake", listener.isPancake)
            status.addProperty("active", listener.isActive())
            status.addProperty("vrcOpen", listener.vrcOpen)

            sendSerialized(obj)

            listener.getParams().forEach {

                val value = listener.mutatedParams[it.name]
                val matchType = if (value != null) it.checkKeys(parameterKeys, value) else null
                log("Attempting to send mutated param ${it.name} = $value," +
                        " matchType = $matchType, vrcOpen = ${listener.vrcOpen}")

                if (listener.vrcOpen != false && matchType == MatchType.GOOD) {
                    sendGenericParameter(it.name, listener.mutatedParams[it.name])
                }
            }
        }
    }
    suspend fun sendGenericParameter(name: String, value: Any?) {
        log("Sending generic $name = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        setProperty(toSend, value)
        sendSerialized(toSend)
    }
}