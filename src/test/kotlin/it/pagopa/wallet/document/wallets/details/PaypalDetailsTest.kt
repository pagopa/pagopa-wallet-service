package it.pagopa.wallet.document.wallets.details

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.documents.wallets.details.PayPalDetails
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PaypalDetailsTest {

    @Test
    fun `can instantiate a WalletPaypalDetailsDto from valid maskedEmail, pspId and pspBusinessName`() {

        val paypalDetailsDocument =
            PayPalDetails(
                maskedEmail = WalletTestUtils.MASKED_EMAIL.value,
                pspId = WalletTestUtils.PSP_ID,
                pspBusinessName = WalletTestUtils.PSP_BUSINESS_NAME,
            )
        val paypalDetailsDomain = paypalDetailsDocument.toDomain()

        Assertions.assertEquals(
            WalletTestUtils.MASKED_EMAIL.value, paypalDetailsDocument.maskedEmail)
        Assertions.assertEquals(WalletTestUtils.PSP_ID, paypalDetailsDocument.pspId)
        Assertions.assertEquals(
            WalletTestUtils.PSP_BUSINESS_NAME, paypalDetailsDocument.pspBusinessName)

        Assertions.assertEquals(WalletTestUtils.MASKED_EMAIL, paypalDetailsDomain.maskedEmail)
        Assertions.assertEquals(WalletTestUtils.PSP_ID, paypalDetailsDomain.pspId)
        Assertions.assertEquals(
            WalletTestUtils.PSP_BUSINESS_NAME, paypalDetailsDomain.pspBusinessName)
    }

    @Test
    fun `can instantiate a WalletPaypalDetailsDto with an idPSP that needs to be remapped from document to domain`() {

        val idPspDocument = "SIGPITM1XXX"
        val idPspDomain = "MOONITMMXXX"
        val paypalDetailsDocument =
            PayPalDetails(
                maskedEmail = WalletTestUtils.MASKED_EMAIL.value,
                pspId = idPspDocument,
                pspBusinessName = WalletTestUtils.PSP_BUSINESS_NAME,
            )
        val paypalDetailsDomain = paypalDetailsDocument.toDomain()

        Assertions.assertEquals(
            WalletTestUtils.MASKED_EMAIL.value, paypalDetailsDocument.maskedEmail)
        Assertions.assertEquals(idPspDocument, paypalDetailsDocument.pspId)
        Assertions.assertEquals(
            WalletTestUtils.PSP_BUSINESS_NAME, paypalDetailsDocument.pspBusinessName)

        Assertions.assertEquals(WalletTestUtils.MASKED_EMAIL, paypalDetailsDomain.maskedEmail)
        Assertions.assertEquals(idPspDomain, paypalDetailsDomain.pspId)
        Assertions.assertEquals(
            WalletTestUtils.PSP_BUSINESS_NAME, paypalDetailsDomain.pspBusinessName)
    }
}
