package it.pagopa.wallet.domain.details

/** Extensible interface to handle multiple wallet details typologies, such as CARDS */
sealed interface WalletDetails<T : it.pagopa.wallet.documents.wallets.details.WalletDetails> {
    val type: WalletDetailsType

    fun toDocument(): T
}
