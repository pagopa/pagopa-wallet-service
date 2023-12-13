package it.pagopa.wallet.domain.details

import it.pagopa.wallet.documents.wallets.details.PayPalDetails

data class PayPalDetails(val maskedEmail: String) : WalletDetails<PayPalDetails> {
    override val type: WalletDetailsType
        get() = WalletDetailsType.PAYPAL

    override fun toDocument() = PayPalDetails(MaskedEmail(maskedEmail))
}
