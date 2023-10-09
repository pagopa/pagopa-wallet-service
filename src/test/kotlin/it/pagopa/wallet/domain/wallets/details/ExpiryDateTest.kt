package it.pagopa.wallet.domain.wallets.details

import it.pagopa.wallet.domain.details.ExpiryDate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpiryDateTest {

    private val validExpiryDate = "203012"
    private val invalidExpiryDate = "12-10"

    @Test
    fun `can instantiate a ExpiryDate from valid expiryDate`() {
        val expiryDate = ExpiryDate(validExpiryDate)
        Assertions.assertEquals(validExpiryDate, expiryDate.expDate)
    }

    @Test
    fun `can't instantiate a ExpiryDate from valid expiryDate`() {
        assertThrows<IllegalArgumentException> { ExpiryDate(invalidExpiryDate) }
    }
}