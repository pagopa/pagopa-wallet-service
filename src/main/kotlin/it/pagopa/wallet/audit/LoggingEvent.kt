package it.pagopa.wallet.audit

import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallet-log-events")
sealed class LoggingEvent(val id: UUID, val createdAt: Instant) {
    constructor() : this(UUID.randomUUID(), Instant.now())
}

data class WalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletPatchEvent(val walletId: String) : LoggingEvent()

data class ServiceCreatedEvent(val serviceId: UUID, val serviceName: String) : LoggingEvent()
