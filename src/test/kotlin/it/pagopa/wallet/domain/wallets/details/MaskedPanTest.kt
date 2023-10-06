package it.pagopa.wallet.domain.wallets.details

import it.pagopa.wallet.domain.details.MaskedPan
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MaskedPanTest {

    private val validMaskedPan = "424242******5555"
    private val invalidMaskedPan = "4242425555"

    @Test
    fun `can instantiate a MaskedPan from valid maskedPan`() {
        val maskedPan = MaskedPan(validMaskedPan)
        Assertions.assertEquals(validMaskedPan, maskedPan.maskedPan)
    }

    @Test
    fun `can't instantiate a MaskedPan from valid maskedPan`() {
        assertThrows<IllegalArgumentException> { MaskedPan(invalidMaskedPan) }
    }
}
