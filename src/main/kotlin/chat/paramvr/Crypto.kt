package chat.paramvr

import java.security.SecureRandom

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Crypto {

    private val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    private const val IT = 120_000
    private val rand = SecureRandom()

    fun hash(pw: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pw, salt, IT, 256)
        return factory.generateSecret(spec).encoded
    }

    fun nextSalt(len: Int): ByteArray {
        val b = ByteArray(len)
        rand.nextBytes(b)
        return b
    }
}