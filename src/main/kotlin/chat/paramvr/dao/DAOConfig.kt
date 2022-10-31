package chat.paramvr.dao

import chat.paramvr.Config
import chat.paramvr.testInt
import java.nio.file.Paths

class DAOConfig : Config(Paths.get("datasource.properties")) {

    override fun populate() {
        props.computeIfAbsent(host) { "" }
        props.computeIfAbsent(user) { "vrcparameters" }
        props.computeIfAbsent(passwd) { "" }
        props.computeIfAbsent(schema) { "vrcparameters" }
        populate(port, "3306") { it.testInt() }
        populate(connectionPool, "3") { it.testInt() }
    }

    fun getHost() = getString(host)

    fun getPort() = getInt(port)

    fun getUser() = getString(user)

    fun getPasswd() = getString(passwd)

    fun getSchema() = getString(schema)

    fun getConnectionPool() = getInt(connectionPool)

    companion object {

        private const val host = "host"
        private const val port = "port"
        private const val user = "user"
        private const val passwd = "passwd"
        private const val schema = "schema"
        private const val connectionPool = "connectionPool"
    }
}