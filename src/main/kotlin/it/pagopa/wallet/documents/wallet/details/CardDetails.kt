package it.pagopa.wallet.documents.wallet.details

data class CardDetails(
    val type: String,
    val bin: String,
    val maskedPan: String,
    val expiryDate: String,
    val brand: String,
    val holder: String
) : WalletDetails {}
