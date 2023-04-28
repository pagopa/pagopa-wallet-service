package it.pagopa.wallet.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentInstrumentDetailTest {
    @Test
    fun `can instantiate a PaymentInstrumentDetail from valid bin, maskedPan and expiryDate`() {
        val VALID_BIN = "424242"
        val VALID_MASKED_PAN = "424242******5555"
        val VALID_EXPIRY_DATE = "203012"

        val paymentInstrumentDetail =
            PaymentInstrumentDetail(VALID_BIN, VALID_MASKED_PAN, VALID_EXPIRY_DATE)

        assertEquals(VALID_BIN, paymentInstrumentDetail.bin)
        assertEquals(VALID_MASKED_PAN, paymentInstrumentDetail.maskedPan)
        assertEquals(VALID_EXPIRY_DATE, paymentInstrumentDetail.expiryDate)
    }

    @Test
    fun `can't instantiate a PaymentInstrumentDetail from valid bin, maskedPan and invalid expiryDate`() {
        val VALID_BIN = "424242"
        val VALID_MASKED_PAN = "424242******5555"
        val INVALID_EXPIRY_DATE = "12-10"

        assertThrows<IllegalArgumentException> {
            PaymentInstrumentDetail(VALID_BIN, VALID_MASKED_PAN, INVALID_EXPIRY_DATE)
        }
    }

    @Test
    fun `can't instantiate a PaymentInstrumentDetail from valid bin, expiryDate and invalid maskedPan`() {
        val VALID_BIN = "424242"
        val INVALID_MASKED_PAN = "4242425555"
        val VALID_EXPIRY_DATE = "203012"

        assertThrows<IllegalArgumentException> {
            PaymentInstrumentDetail(VALID_BIN, INVALID_MASKED_PAN, VALID_EXPIRY_DATE)
        }
    }

    @Test
    fun `can't instantiate a PaymentInstrumentDetail from valid maskedPan, expiryDate and invalid bin`() {
        val INVALID_BIN = "42424"
        val VALID_MASKED_PAN = "424242******5555"
        val VALID_EXPIRY_DATE = "203012"

        assertThrows<IllegalArgumentException> {
            PaymentInstrumentDetail(INVALID_BIN, VALID_MASKED_PAN, VALID_EXPIRY_DATE)
        }
    }
}
