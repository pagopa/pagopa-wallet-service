package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.PaymentInstrumentId
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentInstrumentIdConvertersTest {
    @Test
    fun `reading converter converts string to id correctly`() {
        val value = UUID.randomUUID()
        val input = value.toString()
        val expected = PaymentInstrumentId(value)

        val actual = PaymentInstrumentIdReader.convert(input)

        assertEquals(expected, actual)
    }

    @Test
    fun `reading converter throws IllegalArgumentException on invalid input`() {
        val input = "aaa"

        assertThrows<IllegalArgumentException> { PaymentInstrumentIdReader.convert(input) }
    }

    @Test
    fun `writing converter converts id to string correctly`() {
        val value = UUID.randomUUID()
        val input = PaymentInstrumentId(value)
        val expected = value.toString()

        val actual = PaymentInstrumentIdWriter.convert(input)

        assertEquals(actual, expected)
    }
}
