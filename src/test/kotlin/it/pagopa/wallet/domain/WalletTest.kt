package it.pagopa.wallet.domain

import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletTest {
    @Test
    fun `can construct wallet from UUID`() {
        val walletId = WalletId(UUID.randomUUID())
        val paymentInstruments = listOf(PaymentInstrument(PaymentInstrumentId(UUID.randomUUID())))
        val wallet = Wallet(walletId, paymentInstruments)

        assertEquals(walletId, wallet.walletId)
    }
    @Test
    fun `wallet with empty payment instrument list is invalid`() {
        val paymentInstrumentList = listOf<PaymentInstrument>()

        assertThrows<IllegalArgumentException> {
            Wallet(WalletId(UUID.randomUUID()), paymentInstrumentList)
        }
    }
}
