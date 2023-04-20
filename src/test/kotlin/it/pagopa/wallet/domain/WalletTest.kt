package it.pagopa.wallet.domain

import java.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletTest {
    @Test
    fun `wallet with empty payment instrument list is invalid`() {
        val paymentInstrumentList = listOf<PaymentInstrument>()

        assertThrows<IllegalArgumentException> {
            Wallet(WalletId(UUID.randomUUID()), paymentInstrumentList)
        }
    }
}
