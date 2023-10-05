package it.pagopa.wallet.documents.wallet

import it.pagopa.wallet.documents.wallet.details.WalletDetails
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallets")
data class Wallet(
    val id: String,
    val userId: String,
    val paymentMethodId: String,
    val paymentInstrumentId: String,
    val contractId: String,
    val services: List<WalletService>,
    val details: WalletDetails?
)
