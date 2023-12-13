package it.pagopa.wallet.domain.details

import it.pagopa.wallet.documents.wallets.details.PayPalDetails

data class PayPalDetails(val maskedEmail: String?, val pspId: String) :
    WalletDetails<PayPalDetails> {
    override val type: WalletDetailsType
        get() = WalletDetailsType.PAYPAL

    override fun toDocument() = PayPalDetails(maskedEmail?.let { MaskedEmail(maskedEmail) }, pspId)
}
