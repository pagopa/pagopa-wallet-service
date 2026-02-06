package it.pagopa.wallet.domain.wallets.details

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LastFourDigitsTest {

    private val validLastFourDigits = "5555"
    private val invalidLastFourDigits = "42424242****5555"
    private val invalidLastFourDigits1 = "4242425555"

    @Test
    fun `can instantiate a LastFourDigits from valid lastFourDigits`() {
        val lastFourDigits = LastFourDigits(validLastFourDigits)
        Assertions.assertEquals(validLastFourDigits, lastFourDigits.lastFourDigits)
    }

    @Test
    fun `can't instantiate a LastFourDigits from valid lastFourDigits`() {
        assertThrows<IllegalArgumentException> { LastFourDigits(invalidLastFourDigits) }
        assertThrows<IllegalArgumentException> { LastFourDigits(invalidLastFourDigits1) }
    }
}
