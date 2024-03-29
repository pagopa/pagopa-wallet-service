package it.pagopa.wallet.util

import it.pagopa.wallet.annotations.ValueObject
import java.util.*

/**
 * Value object holding a transaction id.
 *
 * @param uuid the transaction id
 */
@ValueObject
data class TransactionId(val trimmedUUIDString: String) {

    companion object {
        private fun fromTrimmedUUIDString(trimmedUUIDString: String): UUID {
            require(trimmedUUIDString.length == 32) {
                "Invalid transaction id: [%s]. Transaction id must be not null and 32 chars length".format(
                    trimmedUUIDString
                )
            }
            val uuid = CharArray(36)
            val trimmedUUID = trimmedUUIDString.toCharArray()
            System.arraycopy(trimmedUUID, 0, uuid, 0, 8)
            System.arraycopy(trimmedUUID, 8, uuid, 9, 4)
            System.arraycopy(trimmedUUID, 12, uuid, 14, 4)
            System.arraycopy(trimmedUUID, 16, uuid, 19, 4)
            System.arraycopy(trimmedUUID, 20, uuid, 24, 12)
            uuid[8] = '-'
            uuid[13] = '-'
            uuid[18] = '-'
            uuid[23] = '-'
            return UUID.fromString(String(uuid))
        }
    }

    fun value(): UUID {
        return fromTrimmedUUIDString(trimmedUUIDString)
    }
}
