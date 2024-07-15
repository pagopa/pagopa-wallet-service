package it.pagopa.wallet.util

import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TransactionIdTest {

    @Test
    fun `Should generate transactionId domain object successfully`() {
        val uuid = UUID.randomUUID()
        val uuidWithoutDashes = uuid.toString().replace("-", "")
        val transactionId = TransactionId(uuidWithoutDashes)
        assertEquals(uuid, transactionId.uuid())
    }

    @Test
    fun `Should throw exception for invalid transaction id`() {
        val exception = assertThrows<IllegalArgumentException> { TransactionId("a").uuid() }
        assertEquals(
            "Invalid transaction id: [a]. Transaction id must be not null and 32 chars length",
            exception.message
        )
    }
}
