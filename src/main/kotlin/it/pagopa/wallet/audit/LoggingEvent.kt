package it.pagopa.wallet.audit

import it.pagopa.wallet.domain.services.ServiceStatus
import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallet-log-events")
sealed class LoggingEvent(val id: String, val timestamp: String) {
    constructor() : this(UUID.randomUUID().toString(), Instant.now().toString())
}

data class WalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletPatchEvent(val walletId: String) : LoggingEvent()

data class ServiceCreatedEvent(val serviceId: UUID, val serviceName: String) : LoggingEvent()

data class ServiceStatusChangedEvent(
    val serviceId: UUID,
    val serviceName: String,
    val oldStatus: ServiceStatus,
    val newStatus: ServiceStatus
) : LoggingEvent()
