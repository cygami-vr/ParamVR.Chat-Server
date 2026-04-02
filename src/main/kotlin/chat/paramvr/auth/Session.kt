package chat.paramvr.auth

import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
data class VrcParametersSession(val userId: Long, val userName: String)

fun RoutingContext.vrcParametersSession(): VrcParametersSession = call.sessions.get()!!
fun RoutingContext.userId() = vrcParametersSession().userId

data class AuthUser(val id: Long, val name: String, var failedLogins: Int, var salt: ByteArray, var saltedHash: ByteArray, val listenKey: String?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthUser

        if (id != other.id) return false
        if (failedLogins != other.failedLogins) return false
        if (name != other.name) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!saltedHash.contentEquals(other.saltedHash)) return false
        if (listenKey != other.listenKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + failedLogins
        result = 31 * result + name.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + saltedHash.contentHashCode()
        result = 31 * result + (listenKey?.hashCode() ?: 0)
        return result
    }
}

fun AuthUser.newSession() = VrcParametersSession(id, name)