package chat.paramvr.ws.listen

import chat.paramvr.avatar.Avatar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import chat.paramvr.avatar.AvatarDAO
import chat.paramvr.parameter.DataType
import chat.paramvr.ws.Connection
import chat.paramvr.ws.connections
import chat.paramvr.ws.parameterDAO
import chat.paramvr.ws.target

class ListenConnection(session: DefaultWebSocketServerSession, targetUser: String, private val userId: Long,
                       var avatar: Avatar? = null, var muted: Boolean? = null, var isPancake: Boolean? = null,
                       var afk: Boolean? = null, var lastActivity: Long = -1, var vrcOpen: Boolean? = null,
                       var lastActivityPing: Long = -1,

                            // Mutated params cannot be tracked as part of avatar params
                            // because we need the mutated list to persist even when avatar params is overwritten.
                            // If avatars share parameters, or if a parameter is unsaved on the avatar,
                            // this could result in incorrect values getting sent to the client.
                       val mutatedParams: MutableMap<String, Any> = mutableMapOf(),
                       var avatarParams: List<Parameter>? = null

): Connection(session, targetUser) {

    private val avatarDAO = AvatarDAO()

    override suspend fun close(msg: String) {
        super.close(msg)

        // Update the parameters shown on the parameter trigger webpage.
        connections.target(targetUser).forEach {
            it.send(Frame.Text("[]"))
        }
    }

    fun isActive() = System.currentTimeMillis() - lastActivity < 60000

    fun removeUnsavedMutatedParams() {
        getParams().filter { p -> p.saved == "N" }.forEach {
            mutatedParams.remove(it.name)
        }
    }

    fun getParams(): List<Parameter> {
        if (avatarParams == null) {
            avatarParams = parameterDAO.retrieveParameters(userId, avatar?.vrcUuid)
            log("Updating cache with ${avatarParams?.size} parameters, UUID = ${avatar?.vrcUuid}")
        }
        return avatarParams!!
    }

    suspend fun setAvatar(avatarName: String) {
        avatar = avatarDAO.retrieveAvatar(userId, name = avatarName)
        log("New avatar = ${avatar?.name}")
        if (avatar != null) {
            avatarChanged()
        }
    }

    suspend inline fun notifyConnected(connected: Boolean) {
        sendGenericParameter("connected", connected)
    }

    private suspend fun sendParameters() {
        val params = getParams()
        log("Sending ${params.size} parameters, vrcOpen = $vrcOpen")
        connections.target(targetUser).forEach {
            if (vrcOpen != false) {
                val filtered = params.filterKeys(it.parameterKeys)
                if (params.size != filtered.size) {
                    log("${params.size - filtered.size} parameters filtered out")
                }
                it.sendSerialized(filtered)
            } else {
                it.send(Frame.Text("[]"))
            }
        }
    }

    private suspend fun sendSensitiveParameter(param: Parameter, value: Any) {

        log("Sending sensitive ${param.name} = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", param.name)
        setProperty(toSend, value)
        connections.target(targetUser).forEach { triggerCon ->

            val matchType = param.checkKeys(triggerCon.parameterKeys, value)
            if (matchType == MatchType.GOOD) {
                triggerCon.sendSerialized(toSend)
            }
        }
    }
    suspend fun sendGenericParameter(name: String, value: Any?) {
        log("Sending generic $name = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", name)
        setProperty(toSend, value)
        sendJson(toSend)
    }

    private suspend fun sendJson(obj: JsonObject) {
        connections.target(targetUser).forEach {
            it.sendSerialized(obj)
        }
    }

    private suspend fun avatarChanged() {
        sendGenericParameter("avatar", avatar?.name)
        sendGenericParameter("image", avatar?.image)
        sendGenericParameter("isPancake", isPancake)
        avatarParams = null // force sendParameters to update
        sendParameters()
        removeUnsavedMutatedParams()
    }

    private suspend fun updateVrcOpen() {
        sendGenericParameter("vrcOpen", vrcOpen)
    }

    suspend fun handleListenerUpdates(updates: JsonArray) {
        val avatarChange = updates.find {
            it.asJsonObject.get("name").asString == "/avatar/change"
        }
        log("Handling ${updates.size()} listener updates. Avatar changed = ${avatarChange != null}")
        avatarChange?.let {
            val uuid = it.asJsonObject.get("value").asString
            isPancake = true
            avatar = avatarDAO.retrieveAvatar(userId, vrcUuid = uuid!!)

            if (vrcOpen != true) {
                // in case they opened recently and activity check hasn't fired
                vrcOpen = true
                updateVrcOpen()
            }
            avatarChanged()
        }

        updates.forEach { handleUpdate(it.asJsonObject) }
    }

    private suspend fun handleUpdate(update: JsonObject) {
        var name = update.get("name").asString
        val value = update.get("value")
        log("Handling update $name = $value")
        when (name) {
            "/avatar/change" -> {
                // ignore, this was handled by the caller
            }
            "/avatar/parameters/MuteSelf" -> {
                muted = value.asBoolean
                sendGenericParameter("muted", muted)
            }
            "/avatar/parameters/VRMode" -> {
                isPancake = false
                sendGenericParameter("isPancake", isPancake)
            }
            "/avatar/parameters/AFK" -> {
                afk = value.asBoolean
                sendGenericParameter("afk", afk)
            }
            "/chat/paramvr/lastActivity" -> {
                lastActivity = value.asLong
            }
            "/chat/paramvr/vrcOpen" -> {
                if (value.asBoolean != vrcOpen) {
                    vrcOpen = value.asBoolean
                    updateVrcOpen()
                    sendParameters()
                }
            }
            else -> {
                // Assume it is an avatar parameter
                name = name.substring(19)
                val param = getParams().find { it.name == name }
                log("Mutating param $name to $value, DataType = ${param?.dataType}")

                param?.let {
                    DataType.parse(value, param.dataType)?.let {
                        mutatedParams[name] = it
                        sendSensitiveParameter(param, it)
                    }
                }
            }
        }
    }

}