package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.details.MaskedEmail
import it.pagopa.wallet.domain.details.PayPalDetails

data class PayPalDetails(val maskedEmail: String?, val pspId: String) :
    WalletDetails<PayPalDetails> {
    override fun toDomain() = PayPalDetails(maskedEmail?.let { MaskedEmail(it) }, pspId)
}
