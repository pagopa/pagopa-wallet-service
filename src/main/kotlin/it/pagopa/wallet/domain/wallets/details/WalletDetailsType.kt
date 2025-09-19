package it.pagopa.wallet.domain.wallets.details

enum class WalletDetailsType(val paymentTypeCode: String) {
    CARDS("CP"),
    PAYPAL("PPAL")
}
