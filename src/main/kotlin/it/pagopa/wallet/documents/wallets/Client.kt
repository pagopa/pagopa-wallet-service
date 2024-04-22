package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.wallets.Client
import java.time.Instant

data class Client(val status: String, val lastUsage: String?) {
    fun toDomain(): Client =
        Client(Client.Status.valueOf(status), lastUsage = lastUsage?.let { Instant.parse(it) })
}
