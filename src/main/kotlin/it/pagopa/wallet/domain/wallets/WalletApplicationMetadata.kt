package it.pagopa.wallet.domain.wallets

data class WalletApplicationMetadata(val data: Map<String, String>) {
    enum class Metadata(val value: String) {
        TRANSACTION_ID("transactionId"),
        AMOUNT("amount"),
        PAYMENT_WITH_CONTEXTUAL_ONBOARD("paymentWithContextualOnboard")
    }
}
