package it.pagopa.wallet.domain.wallets.details

enum class WalletDetailsType(val paymentTypeCode: String) {
    CARDS("CP"),
    PAYPAL("PPAL");

    companion object {
        private val lookupMap = WalletDetailsType.entries.groupBy { it.paymentTypeCode }

        fun fromPaymentTypeCode(value: String): WalletDetailsType? = lookupMap[value]?.get(0)
    }
}
