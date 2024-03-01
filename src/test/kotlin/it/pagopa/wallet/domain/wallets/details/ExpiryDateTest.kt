package it.pagopa.wallet.domain.wallets.details

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ExpiryDateTest {

    private val validExpiryDate = "201012"

    companion object {
        @JvmStatic
        private fun invalidDateStringSource() =
            Stream.of(
                Arguments.of("12/30"),
                Arguments.of("12-10"),
                Arguments.of("203015"),
            )
    }

    @Test
    fun `can instantiate a ExpiryDate from valid expiryDate`() {
        val expiryDate = ExpiryDate(validExpiryDate)
        Assertions.assertEquals(validExpiryDate, expiryDate.expDate)
    }

    @ParameterizedTest
    @MethodSource("invalidDateStringSource")
    fun `can't instantiate a ExpiryDate from valid expiryDate`(date: String) {
        assertThrows<IllegalArgumentException> { ExpiryDate(date) }
    }
}
