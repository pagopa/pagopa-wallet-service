package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import java.time.Instant

data class Application(
    val id: ServiceId,
    val name: ServiceName,
    val status: ServiceStatus,
    val lastUpdate: Instant
)
