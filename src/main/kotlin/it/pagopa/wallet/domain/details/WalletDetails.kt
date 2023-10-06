package it.pagopa.wallet.domain.details

/** Extensible interface to handle multiple wallet details typologies, such as CARDS */
sealed interface WalletDetails {
    val type: WalletDetailsType
}
