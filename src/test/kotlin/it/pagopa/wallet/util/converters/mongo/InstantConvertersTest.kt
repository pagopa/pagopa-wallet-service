package it.pagopa.wallet.util.converters.mongo

import java.time.Instant
import java.time.format.DateTimeParseException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InstantConvertersTest {

    @Test
    fun `reading converter converts string to instant correctly`() {
        val value = Instant.now()
        val input = value.toString()

        val actual = InstantReader.convert(input)

        Assertions.assertEquals(value, actual)
    }

    @Test
    fun `reading converter throws DateTimeParseException on invalid input`() {
        val input = "aaa"

        assertThrows<DateTimeParseException> { InstantReader.convert(input) }
    }

    @Test
    fun `writing converter converts instant to string correctly`() {
        val input = Instant.now()
        val expected = input.toString()

        val actual = InstantWriter.convert(input)

        Assertions.assertEquals(actual, expected)
    }
}
