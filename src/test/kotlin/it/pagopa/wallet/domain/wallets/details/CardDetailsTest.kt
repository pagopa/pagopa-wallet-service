package it.pagopa.wallet.domain.wallets.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto.BrandEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardDetailsTest {
    private val validBin = "42424242"
    val validLastFourDigits = "5555"
    val validExpiryDate = "203012"
    val brand = BrandEnum.MASTERCARD
    val paymentInstrumentGatewayId = "paymentInstrumentGatewayId"
    val invalidBin = "42424"
    val invalidLastFourDigits = "4242425555"
    val invalidExpiryDate = "12-10"

    @Test
    fun `can instantiate a CardDetails from valid bin, lastFourDigits and expiryDate`() {

        val cardDetails =
            CardDetails(
                bin = Bin(validBin),
                lastFourDigits = LastFourDigits(validLastFourDigits),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                paymentInstrumentGatewayId = PaymentInstrumentGatewayId(paymentInstrumentGatewayId)
            )

        assertEquals(validBin, cardDetails.bin.bin)
        assertEquals(validLastFourDigits, cardDetails.lastFourDigits.lastFourDigits)
        assertEquals(validExpiryDate, cardDetails.expiryDate.expDate)
        assertEquals(cardDetails.type, WalletDetailsType.CARDS)
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, lastFourDigits and invalid expiryDate`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(validBin),
                lastFourDigits = LastFourDigits(validLastFourDigits),
                expiryDate = ExpiryDate(invalidExpiryDate),
                brand = brand,
                paymentInstrumentGatewayId = PaymentInstrumentGatewayId(paymentInstrumentGatewayId)
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid bin, expiryDate and invalid lastFourDigits`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(validBin),
                lastFourDigits = LastFourDigits(invalidLastFourDigits),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                paymentInstrumentGatewayId = PaymentInstrumentGatewayId(paymentInstrumentGatewayId)
            )
        }
    }

    @Test
    fun `can't instantiate a cardDetails from valid lastFourDigits, expiryDate and invalid bin`() {

        assertThrows<IllegalArgumentException> {
            CardDetails(
                bin = Bin(invalidBin),
                lastFourDigits = LastFourDigits(validLastFourDigits),
                expiryDate = ExpiryDate(validExpiryDate),
                brand = brand,
                paymentInstrumentGatewayId = PaymentInstrumentGatewayId(paymentInstrumentGatewayId)
            )
        }
    }
}
