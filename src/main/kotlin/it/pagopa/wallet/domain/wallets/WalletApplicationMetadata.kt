package it.pagopa.wallet.domain.wallets

data class WalletApplicationMetadata(val data: Map<String, String>) {
    enum class Metadata(val value: String) {
        TRANSACTION_ID("transactionId"),
        AMOUNT("amount"),
        PAYMENT_WITH_CONTEXTUAL_ONBOARD("paymentWithContextualOnboard"),
        ONBOARD_BY_MIGRATION("onboardByMigration")
    }

    companion object {
        fun empty() = of()
        fun of(vararg data: Pair<Metadata, String>) =
            WalletApplicationMetadata(data.associate { it.first.value to it.second })
    }
}
