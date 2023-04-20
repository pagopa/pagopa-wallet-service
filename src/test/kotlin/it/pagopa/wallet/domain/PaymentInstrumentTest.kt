package it.pagopa.wallet.domain

import it.pagopa.wallet.WalletTestUtils
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentInstrumentTest {
    @Test
    fun `can construct payment instrument from id with UUID`() {
        val paymentInstrumentId = PaymentInstrumentId(UUID.randomUUID())
        val paymentInstrument =
            PaymentInstrument(paymentInstrumentId, WalletTestUtils.GATEWAY_SECURITY_TOKEN)

        assertEquals(paymentInstrumentId, paymentInstrument.id)
    }
}
