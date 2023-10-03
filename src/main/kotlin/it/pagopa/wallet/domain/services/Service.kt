package it.pagopa.wallet.domain.services

import java.time.Instant

data class Service(
    val id: ServiceId,
    val name: ServiceName,
    val status: ServiceStatus,
    val lastUpdated: Instant
)
