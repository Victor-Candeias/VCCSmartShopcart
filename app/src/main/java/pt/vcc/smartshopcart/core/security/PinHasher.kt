package pt.vcc.smartshopcart.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/**
 * Hash do PIN com PBKDF2-HMAC-SHA256 e sal aleatório. O resultado é auto-descritivo
 * (`pbkdf2-sha256$<iterações>$<sal>$<hash>`) para permitir mudar parâmetros sem invalidar PINs antigos.
 */
class PinHasher(private val iterations: Int) {

    @Inject constructor() : this(DEFAULT_ITERATIONS)

    private val random = SecureRandom()

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(pin, salt, iterations)
        return listOf(SCHEME, iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(hash))
            .joinToString(SEPARATOR)
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != SCHEME) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val DEFAULT_ITERATIONS = 120_000
        const val SCHEME = "pbkdf2-sha256"
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val SEPARATOR = "$"
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
