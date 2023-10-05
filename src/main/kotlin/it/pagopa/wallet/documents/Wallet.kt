package it.pagopa.wallet.documents

import org.springframework.data.mongodb.core.mapping.Document

@Document("wallets")
data class Wallet(
    val id: String,
    val userId: String,
    val paymentMethodId: String,
    val paymentInstrumentId: String,
    val contractId: String,
    val services: List<ServiceWallet>,
    val details: WalletDetails?
)
