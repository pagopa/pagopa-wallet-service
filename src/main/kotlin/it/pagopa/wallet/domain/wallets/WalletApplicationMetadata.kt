package it.pagopa.wallet.domain.wallets

data class WalletApplicationMetadata(val data: Map<String, String>) {
    enum class Metadata(val value: String) {
        TRANSACTION_ID("transactionId"),
        AMOUNT("amount"),
        PAYMENT_WITH_CONTEXTUAL_ONBOARD("paymentWithContextualOnboard"),
        ONBOARD_BY_MIGRATION("onboardByMigration")
    }

    operator fun plus(entry: Pair<Metadata, String>) =
        WalletApplicationMetadata(this.data + (entry.first.value to entry.second))

    companion object {
        fun empty() = of()
        fun of(vararg data: Pair<String, String>) = WalletApplicationMetadata(data.toMap())
    }
}
