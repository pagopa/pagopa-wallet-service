package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.wallets.PaymentMethodId
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentMethodIdConvertersTest {
    @Test
    fun `reading converter converts string to id correctly`() {
        val value = UUID.randomUUID()
        val input = value.toString()
        val expected = PaymentMethodId(value)

        val actual = PaymentMethodIdReader.convert(input)

        assertEquals(expected, actual)
    }

    @Test
    fun `reading converter throws IllegalArgumentException on invalid input`() {
        val input = "aaa"

        assertThrows<IllegalArgumentException> { PaymentMethodIdReader.convert(input) }
    }

    @Test
    fun `writing converter converts id to string correctly`() {
        val value = UUID.randomUUID()
        val input = PaymentMethodId(value)
        val expected = value.toString()

        val actual = PaymentMethodIdWriter.convert(input)

        assertEquals(actual, expected)
    }
}
