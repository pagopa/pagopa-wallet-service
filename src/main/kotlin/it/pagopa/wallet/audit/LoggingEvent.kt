package it.pagopa.wallet.audit

import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallet-log-events")
sealed class LoggingEvent(val id: String, val timestamp: String) {
    constructor() : this(UUID.randomUUID().toString(), Instant.now().toString())
}

data class WalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletDeletedEvent(val walletId: String) : LoggingEvent()

data class SessionWalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletPatchEvent(val walletId: String) : LoggingEvent()

data class WalletDetailsAddedEvent(val walletId: String) : LoggingEvent()

data class WalletNotificationEvent(
    val walletId: String,
    val validationOperationId: String,
    val validationOperationResult: String,
    val validationOperationTimestamp: String,
    val validationErrorCode: String?,
) : LoggingEvent()

data class ApplicationCreatedEvent(val serviceId: String) : LoggingEvent()

data class ApplicationStatusChangedEvent(
    val serviceId: String,
    val oldStatus: ApplicationStatus,
    val newStatus: ApplicationStatus
) : LoggingEvent()
