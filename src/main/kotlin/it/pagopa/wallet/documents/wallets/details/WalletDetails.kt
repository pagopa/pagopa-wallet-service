package it.pagopa.wallet.documents.wallets.details

sealed interface WalletDetails<T> {
    fun toDomain(): it.pagopa.wallet.domain.details.WalletDetails<T>
}
