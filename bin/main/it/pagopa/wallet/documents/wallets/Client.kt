package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.wallets.Client

data class Client(val status: String) {
    fun toDomain(): Client = Client(Client.Status.valueOf(status))
}
