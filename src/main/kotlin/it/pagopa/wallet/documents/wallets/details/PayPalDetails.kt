package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.wallets.details.MaskedEmail
import it.pagopa.wallet.domain.wallets.details.PayPalDetails
import it.pagopa.wallet.util.normalizePspId

data class PayPalDetails(val maskedEmail: String?, val pspId: String, val pspBusinessName: String) :
    WalletDetails<PayPalDetails> {

    override fun toDomain() =
        PayPalDetails(maskedEmail?.let { MaskedEmail(it) }, normalizePspId(pspId), pspBusinessName)
}
