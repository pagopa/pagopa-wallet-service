package it.pagopa.wallet.domain.wallets

data class WalletApplicationMetadata(val data: Map<Metadata, String?>) {
    enum class Metadata(val value: String) {
        TRANSACTION_ID("transactionId"),
        AMOUNT("amount"),
        PAYMENT_WITH_CONTEXTUAL_ONBOARD("paymentWithContextualOnboard"),
        ONBOARD_BY_MIGRATION("onboardByMigration"),
        LAST_USED_IO("lastUsedIO"),
        LAST_USED_CHECKOUT("lastUsedCheckout");

        companion object {
            private val valuesMap: Map<String, Metadata> = values().associateBy { it.value }

            fun fromMetadataValue(value: String): Metadata =
                valuesMap[value] ?: throw IllegalArgumentException("Invalid metadata: $value")
        }
    }

    companion object {
        fun empty() = of()
        fun of(vararg data: Pair<Metadata, String>) =
            WalletApplicationMetadata(data.associate { it.first to it.second })
    }
}
