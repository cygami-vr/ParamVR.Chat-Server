package chat.paramvr.dao

import chat.paramvr.Config
import java.nio.file.Paths

class DAOConfig : Config(Paths.get("datasource.properties")) {

    override fun populate() {
        populateString(host, "")
        populateString(user, "vrcparameters")
        populateString(passwd, "")
        populateString(schema, "vrcparameters")
        populateInt(port, 3306)
        populateInt(connectionPool, 3)
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