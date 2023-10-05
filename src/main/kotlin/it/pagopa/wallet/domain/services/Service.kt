package it.pagopa.wallet.domain.services

import it.pagopa.wallet.domain.common.ServiceId
import it.pagopa.wallet.domain.common.ServiceName
import it.pagopa.wallet.domain.common.ServiceStatus
import java.time.Instant

data class Service(
    val id: ServiceId,
    val name: ServiceName,
    val status: ServiceStatus,
    val lastUpdated: Instant
)
