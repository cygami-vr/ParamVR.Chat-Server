package chat.paramvr

import java.nio.file.Files
import java.nio.file.Paths

class AppConfig : Config(Paths.get("vrcparameters-server.properties")) {

    override fun populate() {
        props.computeIfAbsent(host) { "" }
        populate(port, if (prod) "443" else "80") { it.testInt() }
        populate(originPort, "3000") { it.testInt() }
        populate(sessionTimeout, "14400") { it.testInt() }
        props.computeIfAbsent(keystorePath) { "" }
        populate(failedLoginLimit, "5") { it.testInt() }
        props.computeIfAbsent(discordInvite) { "" }
    }

    fun getHost() = getString(host)

    fun getPort() = getInt(port)

    fun getOriginPort() = getInt(originPort)

    fun getSessionTimeout() = getInt(sessionTimeout)

    fun getKeystorePath() = getString(keystorePath)

    fun hasKeystore(): Boolean {
        val k = getKeystorePath()
        return k.isNotEmpty() && Files.exists(Paths.get(k))
    }

    fun getFailedLoginLimit() = getInt(failedLoginLimit)

    fun getDiscordInvite() = getString(discordInvite)

    companion object {
        private const val host = "host"
        private const val port = "port"
        private const val originPort = "originPort"
        private const val sessionTimeout = "sessionTimeout"
        private const val keystorePath = "keystorePath"
        private const val failedLoginLimit = "failedLoginLimit"
        private const val discordInvite = "discordInvite"
    }
}