package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.documents.wallets.details.WalletDetails
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallets")
data class Wallet(
    val id: String,
    val userId: String,
    val paymentMethodId: String,
    val paymentInstrumentId: String?,
    val contractId: String,
    val services: List<WalletService>,
    val details: WalletDetails?
)
