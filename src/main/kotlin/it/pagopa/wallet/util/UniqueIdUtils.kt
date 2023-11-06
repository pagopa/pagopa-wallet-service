package it.pagopa.wallet.util

import java.security.SecureRandom
import org.springframework.stereotype.Component

@Component
class UniqueIdUtils {
    private val secureRandom = SecureRandom()

    companion object {
        const val ALPHANUMERICS: String =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"
        const val MAX_LENGTH: Int = 18
    }

    fun generateUniqueId(): String {
        val timestampToString = System.currentTimeMillis().toString()
        val randomStringLength = MAX_LENGTH - timestampToString.length
        return timestampToString + generateRandomString(randomStringLength)
    }

    private fun generateRandomString(length: Int): String {
        val stringBuilder = StringBuilder(length)
        for (i in 0 until length) {
            stringBuilder.append(ALPHANUMERICS[secureRandom.nextInt(ALPHANUMERICS.length)])
        }
        return stringBuilder.toString()
    }
}
