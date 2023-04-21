package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.WalletId
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletIdConvertersTest {
    @Test
    fun `reading converter converts string to id correctly`() {
        val value = UUID.randomUUID()
        val input = value.toString()
        val expected = WalletId(value)

        val actual = WalletIdReader.convert(input)

        assertEquals(expected, actual)
    }

    @Test
    fun `reading converter throws IllegalArgumentException on invalid input`() {
        val input = "aaa"

        assertThrows<IllegalArgumentException> { WalletIdReader.convert(input) }
    }

    @Test
    fun `writing converter converts id to string correctly`() {
        val value = UUID.randomUUID()
        val input = WalletId(value)
        val expected = value.toString()

        val actual = WalletIdWriter.convert(input)

        assertEquals(actual, expected)
    }
}
