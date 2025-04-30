package chat.paramvr.ws

import chat.paramvr.avatar.Avatar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import chat.paramvr.avatar.AvatarDAO
import chat.paramvr.invite.Invite
import chat.paramvr.parameter.DataType
import chat.paramvr.usersettings.UserSettings
import chat.paramvr.ws.Sockets.connections
import chat.paramvr.ws.Sockets.parameterDAO
import kotlinx.coroutines.*
import java.lang.Long.parseLong

class ListenConnection(

    session: DefaultWebSocketServerSession, targetUser: String, val userId: Long, var invites: List<Invite>,
    var avatar: Avatar? = null, var muted: Boolean? = null, var isPancake: Boolean? = null,
    var afk: Boolean? = null, private var lastActivity: Long = -1, var vrcOpen: Boolean? = null,
    var lastActivityPing: Long = -1, var settings: UserSettings? = null,

    var changeableAvatars: List<Avatar>? = null,
    var lastAvatarChange: Long = -1,

    // Mutated params cannot be tracked as part of avatar params
    // because we need the mutated list to persist even when avatar params is overwritten.
    // If avatars share parameters, or if a parameter is unsaved on the avatar,
    // this could result in incorrect values getting sent to the client.
    val mutatedParams: MutableMap<String, Any> = mutableMapOf(),

    var avatarParams: List<Parameter>? = null,
    private val buttonJobs: MutableMap<String, Job> = mutableMapOf()

): Connection(session, targetUser) {

    private val avatarDAO = AvatarDAO()

    override suspend fun close(msg: String) {
        super.close(msg)

        // Update the parameters shown on the parameter trigger webpage.
        connections.target(targetUser).forEach {
            it.sendSerialized(Parameters(emptyList()))
        }

        buttonJobs.values.forEach { it.cancel() }
    }

    fun isActive() = System.currentTimeMillis() - lastActivity < 60000

    fun scheduleButtonMaxPress(param: Parameter) {
        buttonJobs[param.name]?.let {
            if (!it.isCompleted && !it.isCancelled) {
                log("${param.name} Button Job is incomplete, canceling it now")
                it.cancel()
            }
        }
        param.defaultValue?.let {
            val job = GlobalScope.launch {
                try {
                    delay(parseLong(param.maxValue))
                } catch (ex: NumberFormatException) {
                    warn("${param.maxValue} is not valid")
                } catch (ex: CancellationException) {
                    log("Button coroutine cancelled")
                    return@launch
                }
                log("${param.name} Button reached max press time")
                sendSerialized(ParameterChange(param.name, it, param.dataType))
            }
            buttonJobs[param.name] = job
        }
    }

    private fun removeUnsavedMutatedParams() {
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

    fun getChangeableAvas(): List<Avatar> {
        if (changeableAvatars == null) {
            val avas = mutableListOf<Avatar>()
            avatarDAO.retrieveAvatars(userId)
                .filter { it.allowChange == "Y" }
                .forEach { ava ->
                    avas += ava
                }
            changeableAvatars = avas
            log("Updating changeable avatars with ${changeableAvatars?.size} avatars")
        }
        return changeableAvatars!!
    }

    fun getParam(change: ParameterChange): Parameter? {
        val param = getParams().find { it.name == change.name }
        log("Getting parameter name = ${change.name}, Parameter found = ${param != null}")
        return param
    }

    fun getParam(lock: ParameterLock): Parameter? {
        val param = getParams().find { it.name == lock.name }
        log("Getting parameter name = ${lock.name}, Parameter found = ${param != null}")
        return param
    }

    suspend fun setAvatar(avatarIdOrName: String) {
        avatar = if (avatarIdOrName.startsWith("avtr_"))
            avatarDAO.retrieveAvatar(userId, vrcUuid = avatarIdOrName)
            else avatarDAO.retrieveAvatar(userId, name = avatarIdOrName)
        log("New avatar = ${avatar?.name}")
        if (avatar != null) {
            avatarChanged()
        }
    }

    suspend inline fun notifyConnected(connected: Boolean) {
        sendStatusParameter("connected", connected)
        if (connected) {
            connections.target(targetUser).forEach {
                it.sendLockedParams()
                it.sendChangeableAvatars()
            }
        }
    }

    private suspend fun sendParameters() {
        val params = getParams()
        log("Sending ${params.size} parameters, vrcOpen = $vrcOpen")
        connections.target(targetUser).forEach {
            // Considering vrcOpen == null as true
            if (vrcOpen != false) {
                val filtered = it.perms.filterViewable()
                if (params.size != filtered.size) {
                    log("${params.size - filtered.size} parameters filtered out")
                }
                it.sendSerialized(Parameters(filtered))
            } else {
                it.sendSerialized(Parameters(emptyList()))
            }
        }
    }

    private suspend fun sendSensitiveParameter(param: Parameter, value: Any) {

        debug("Sending sensitive ${param.name} = $value")
        val toSend = JsonObject()
        toSend.addProperty("name", param.name)
        toSend.addProperty("type", "parameter")
        toSend.addProperty("parameter-type", "value")
        setProperty(toSend, value)

        connections.target(targetUser).forEach { triggerCon ->
            if (triggerCon.perms.canView(param, value)) {
                triggerCon.sendSerialized(toSend)
            }
        }
    }

    suspend fun sendStatusParameter(name: String, value: Any?) {
        debug("Sending status $name = $value")
        val toSend = JsonObject()
        val status = JsonObject()
        setProperty(status, value, name)
        toSend.add("status", status)
        sendJson(toSend)
    }

    private suspend fun sendJson(obj: JsonObject) {
        connections.target(targetUser).forEach {
            it.sendSerialized(obj)
        }
    }

    private fun getStatus(): JsonObject {
        val fullStatus = JsonObject()
        val status = JsonObject()
        val avatarStatus = JsonObject()
        avatarStatus.addProperty("name", avatar?.name)
        avatarStatus.addProperty("vrcUuid", avatar?.vrcUuid)
        avatarStatus.addProperty("image", avatar?.image)
        avatarStatus.addProperty("allowChange", avatar?.allowChange)
        avatarStatus.addProperty("title", avatar?.title)
        status.add("avatar", avatarStatus)
        status.addProperty("isPancake", isPancake)
        settings?.let {
            status.addProperty("avatarChangeCooldown",
                it.avatarChangeCooldown * 1000 - (System.currentTimeMillis() - lastAvatarChange))
            val colors = JsonObject()
            colors.addProperty("colorPrimary", it.colorPrimary)
            colors.addProperty("darkModeColorPrimary", it.darkModeColorPrimary)
            status.add("colors", colors)
        }
        fullStatus.add("status", status)
        return fullStatus
    }

    fun getFullStatus(): JsonObject {
        val fullStatus = getStatus()
        val status = fullStatus.getAsJsonObject("status")
        status.addProperty("muted", muted)
        status.addProperty("afk", afk)
        status.addProperty("active", isActive())
        status.addProperty("vrcOpen", vrcOpen)
        return fullStatus
    }

    private suspend fun avatarChanged() {
        sendJson(getStatus())
        avatarParams = null // force sendParameters to update
        sendParameters()
        removeUnsavedMutatedParams()
        connections.target(targetUser).forEach {
            it.sendChangeableAvatars()
        }
    }

    private suspend fun updateVrcOpen() = sendStatusParameter("vrcOpen", vrcOpen)

    suspend fun handleListenerUpdates(updates: JsonArray) {
        val avatarChange = updates.find {
            it.asJsonObject.get("name").asString == "/avatar/change"
        }
        debug("Handling ${updates.size()} listener updates. Avatar changed = ${avatarChange != null}")
        avatarChange?.let {
            val uuid = it.asJsonObject.get("value").asString
            isPancake = true // will be overwritten later if false
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
        debug("Handling update $name = $value")
        when (name) {
            "/avatar/change" -> {
                // ignore, this was handled by the caller
            }
            "/avatar/parameters/MuteSelf" -> {
                muted = value.asBoolean
                sendStatusParameter("muted", muted)
            }
            "/avatar/parameters/VRMode" -> {
                isPancake = false
                sendStatusParameter("isPancake", isPancake)
            }
            "/avatar/parameters/AFK" -> {
                afk = value.asBoolean
                sendStatusParameter("afk", afk)
            }
            "/chat/paramvr/lastActivity" -> {
                lastActivity = value.asLong
            }
            "/chat/paramvr/vrcOpen" -> {
                if (value.asBoolean != vrcOpen) {
                    vrcOpen = value.asBoolean
                    updateVrcOpen()
                    sendParameters()
                    connections.target(targetUser).forEach {
                        it.sendChangeableAvatars()
                    }
                }
            }
            else -> {
                // Assume it is an avatar parameter
                name = name.substring(19)
                val param = getParams().find { it.name == name }
                debug("Mutating param $name to $value, DataType = ${param?.dataType}")

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