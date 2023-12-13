package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.details.MaskedEmail

data class PayPalDetails(val maskedEmail: MaskedEmail) : WalletDetails<PayPalDetails> {
    override fun toDomain() = it.pagopa.wallet.domain.details.PayPalDetails(maskedEmail.value)
}
