package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.wallets.details.MaskedEmail
import it.pagopa.wallet.domain.wallets.details.PayPalDetails

data class PayPalDetails(val maskedEmail: String?, val pspId: String, val pspBusinessName: String) :
    WalletDetails<PayPalDetails> {

    override fun toDomain() =
        PayPalDetails(maskedEmail?.let { MaskedEmail(it) }, normalizePspId(pspId), pspBusinessName)

    private fun normalizePspId(pspId: String): String =
        when (pspId) {
            "SIGPITM1XXX" -> "MOONITMMXXX"
            else -> pspId
        }
}
