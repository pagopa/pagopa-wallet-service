package it.pagopa.wallet.domain.wallets.details

data class PayPalDetails(val maskedEmail: MaskedEmail?, val pspId: String) :
    WalletDetails<PayPalDetails> {

    override val type: WalletDetailsType
        get() = WalletDetailsType.PAYPAL

    override fun toDocument() =
        it.pagopa.wallet.documents.wallets.details.PayPalDetails(maskedEmail?.value, pspId)
}
