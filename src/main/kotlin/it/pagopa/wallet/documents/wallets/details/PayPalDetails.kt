package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.details.MaskedEmail

data class PayPalDetails(val maskedEmail: MaskedEmail?, val pspId: String) :
    WalletDetails<PayPalDetails> {
    override fun toDomain() =
        it.pagopa.wallet.domain.details.PayPalDetails(maskedEmail?.value, pspId)
}
