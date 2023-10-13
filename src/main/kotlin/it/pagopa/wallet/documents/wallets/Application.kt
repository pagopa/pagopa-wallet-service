package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.Application
import java.time.Instant
import java.util.*

data class Application(
    val id: String,
    val name: String,
    val status: String,
    val lastUpdateDate: String
) {
    fun toDomain() =
        Application(
            ServiceId(UUID.fromString(id)),
            ServiceName(name),
            ServiceStatus.valueOf(status),
            Instant.parse(lastUpdateDate)
        )
}
