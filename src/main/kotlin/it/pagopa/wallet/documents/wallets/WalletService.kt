package it.pagopa.wallet.documents.wallets

data class WalletService(
    val id: String,
    val name: String,
    val status: String,
    val lastUpdateDate: String
)
