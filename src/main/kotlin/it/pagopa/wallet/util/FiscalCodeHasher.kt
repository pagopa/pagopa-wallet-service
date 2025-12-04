package it.pagopa.wallet.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FiscalCodeHasher {
    fun hashFiscalCode(fiscalCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // normalize to uppercase
        val normalizedFiscalCode = fiscalCode.uppercase()
        val hashBytes = digest.digest(normalizedFiscalCode.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
