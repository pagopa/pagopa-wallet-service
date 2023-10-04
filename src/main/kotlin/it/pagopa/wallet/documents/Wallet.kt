package it.pagopa.wallet.documents

import it.pagopa.wallet.domain.details.WalletDetails

data class Wallet(
    val id: String,
    val userId: String,
    val paymentMethodId: String,
    val paymentInstrumentId: String,
    val contractId: String,
    val details: WalletDetails?
)
