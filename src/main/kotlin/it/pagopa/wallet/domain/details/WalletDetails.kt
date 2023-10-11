package it.pagopa.wallet.domain.details

/** Extensible interface to handle multiple wallet details typologies, such as CARDS */
sealed interface WalletDetails<T> {
    val type: WalletDetailsType

    fun toDocument(): it.pagopa.wallet.documents.wallets.details.WalletDetails<T>
}
