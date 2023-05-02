package it.pagopa.wallet.domain

import it.pagopa.generated.wallet.model.WalletCardDetailsDto.BrandEnum
import it.pagopa.wallet.domain.details.CardDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardDetailsTest {
    private val validBin = "424242"
    val validMaskedPan = "424242******5555"
    val validExpiryDate = "203012"
    val invalidBin = "42424"
    val invalidMaskedPan = "4242425555"
    val invalidExpiryDate = "12-10"
    val brand = BrandEnum.MASTERCARD
    val holderName = "holderName"
    val contractNumber = "contractNumber"

    @Test
    fun `can instantiate a CardDetails from valid bin, maskedPan and expiryDate`() {

        val cardDetails =
            CardDetails(
                bin = validBin,
                maskedPan = validMaskedPan,
                expiryDate = validExpiryDate,
                contractNumber = contractNumber,
                brand = brand,
                holderName = holderName
            )

        assertEquals(validBin, cardDetails.bin)
        assertEquals(validMaskedPan, cardDetails.maskedPan)
        assertEquals(validExpiryDate, cardDetails.expiryDate)
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, maskedPan and invalid expiryDate`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = validBin,
                maskedPan = validMaskedPan,
                expiryDate = invalidExpiryDate,
                contractNumber = contractNumber,
                brand = brand,
                holderName = holderName
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, expiryDate and invalid maskedPan`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = validBin,
                maskedPan = invalidMaskedPan,
                expiryDate = validExpiryDate,
                contractNumber = contractNumber,
                brand = brand,
                holderName = holderName
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid maskedPan, expiryDate and invalid bin`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = invalidBin,
                maskedPan = validMaskedPan,
                expiryDate = validExpiryDate,
                contractNumber = contractNumber,
                brand = brand,
                holderName = holderName
            )
        }
    }
}
