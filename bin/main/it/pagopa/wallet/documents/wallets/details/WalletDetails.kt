package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.wallets.details.WalletDetails

fun interface WalletDetails<T> {
    fun toDomain(): WalletDetails<T>
}
