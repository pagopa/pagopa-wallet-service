package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.details.WalletDetails

interface WalletDetails {
    fun toDomain(): WalletDetails
}
