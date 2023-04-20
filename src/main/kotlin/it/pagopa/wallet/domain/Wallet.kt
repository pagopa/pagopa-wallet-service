package it.pagopa.wallet.domain

/**
 * A wallet.
 *
 * A wallet is a collection of payment instruments identified by a single wallet id.
 *
 * The following assumptions should always hold:
 * - Wallets are non-empty
 * - No two wallets share a payment instrument with the same id (i.e. the relation `wallet <->
 *   paymentInstrument` is 1:n)
 *
 * @throws IllegalArgumentException if the provided payment instrument list is empty
 */
data class Wallet(val id: WalletId, val paymentInstruments: List<PaymentInstrument>) {
    init {
        require(paymentInstruments.isNotEmpty()) { "Wallets cannot be empty!" }
    }
}
