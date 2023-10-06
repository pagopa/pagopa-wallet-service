package it.pagopa.wallet.domain.wallets.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto.BrandEnum
import it.pagopa.wallet.domain.details.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardDetailsTest {
    private val validBin = "424242"
    val validMaskedPan = "424242******5555"
    val validExpiryDate = "203012"
    val brand = BrandEnum.MASTERCARD
    val holderName = "holderName"
    val invalidBin = "42424"
    val invalidMaskedPan = "4242425555"
    val invalidExpiryDate = "12-10"

    @Test
    fun `can instantiate a CardDetails from valid bin, maskedPan and expiryDate`() {

        val cardDetails =
            CardDetails(
                bin = Bin(validBin),
                maskedPan = MaskedPan(validMaskedPan),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                holder = CardHolderName(holderName)
            )

        assertEquals(validBin, cardDetails.bin.bin)
        assertEquals(validMaskedPan, cardDetails.maskedPan.maskedPan)
        assertEquals(validExpiryDate, cardDetails.expiryDate.expDate)
        assertEquals(cardDetails.type, WalletDetailsType.CARDS)
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, maskedPan and invalid expiryDate`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(validBin),
                maskedPan = MaskedPan(validMaskedPan),
                expiryDate = ExpiryDate(invalidExpiryDate),
                brand = brand,
                holder = CardHolderName(holderName)
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, expiryDate and invalid maskedPan`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(validBin),
                maskedPan = MaskedPan(invalidMaskedPan),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                holder = CardHolderName(holderName)
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid maskedPan, expiryDate and invalid bin`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(invalidBin),
                maskedPan = MaskedPan(validMaskedPan),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                holder = CardHolderName(holderName)
            )
        }
    }
}
