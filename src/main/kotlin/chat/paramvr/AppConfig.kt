package chat.paramvr

import java.nio.file.Files
import java.nio.file.Paths

class AppConfig : Config(Paths.get("vrcparameters-server.properties")) {

    override fun populate() {

        populateBoolean(isProduction, false)
        populateInt(failedLoginLimit, 5)
        populateString(discordInvite, "")


        // ~~~ Following properties are only used if isProduction = true ~~~

        // Used to determine value of Access-Control-Allow-Origin HTTP header
        props.computeIfAbsent(host) { "" }

        // Used to determine if session cookies are marked "secure"
        populateBoolean(useSsl, false)


        // ~~~ Following properties are only used if isProduction = false ~~~

        // Used to determine the port of the embedded Netty server and hyperlinks for image resources
        populateInt(port, 8080)

        // Used to determine value of Access-Control-Allow-Origin HTTP header
        populateInt(originPort, 3000)
    }

    fun isProduction() = getBoolean(isProduction)

    fun useSsl() = isProduction() && getBoolean(useSsl)

    fun getOrigin() = if (isProduction()) "https://${getHost()}" else "http://localhost:${getOriginPort()}"

    fun getHost() = getString(host)

    fun getPort() = getInt(port)

    fun getOriginPort() = getInt(originPort)

    fun getFailedLoginLimit() = getInt(failedLoginLimit)

    fun getDiscordInvite() = getString(discordInvite)

    companion object {
        private const val isProduction = "isProduction"
        private const val useSsl = "useSSL"
        private const val host = "host"
        private const val port = "port"
        private const val originPort = "originPort"
        private const val failedLoginLimit = "failedLoginLimit"
        private const val discordInvite = "discordInvite"
    }
}