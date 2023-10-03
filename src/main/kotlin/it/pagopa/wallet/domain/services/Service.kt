package it.pagopa.wallet.domain.services

data class Service(
    val id: ServiceId,
    val name: ServiceName,
    val status: ServiceStatus
)
