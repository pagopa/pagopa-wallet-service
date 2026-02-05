package it.pagopa.wallet.audit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

@Document("payment-wallets-log-events")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = WalletAddedEvent::class, name = "WalletAddedEvent"),
    JsonSubTypes.Type(value = WalletMigratedAddedEvent::class, name = "WalletMigratedAddedEvent"),
    JsonSubTypes.Type(value = WalletDeletedEvent::class, name = "WalletDeletedEvent"),
    JsonSubTypes.Type(value = SessionWalletCreatedEvent::class, name = "SessionWalletCreatedEvent"),
    JsonSubTypes.Type(
        value = WalletApplicationsUpdatedEvent::class, name = "WalletApplicationsUpdatedEvent"),
    JsonSubTypes.Type(value = WalletDetailsAddedEvent::class, name = "WalletDetailsAddedEvent"),
    JsonSubTypes.Type(
        value = WalletOnboardCompletedEvent::class, name = "WalletOnboardCompletedEvent"),
    JsonSubTypes.Type(
        value = WalletOnboardReplacedEvent::class, name = "WalletOnboardReplacedEvent"),
    JsonSubTypes.Type(value = ApplicationCreatedEvent::class, name = "ApplicationCreatedEvent"),
    JsonSubTypes.Type(
        value = ApplicationStatusChangedEvent::class, name = "ApplicationStatusChangedEvent"),
)
sealed class LoggingEvent(val id: String, val timestamp: String) {
    constructor() : this(UUID.randomUUID().toString(), Instant.now().toString())
}

data class WalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletMigratedAddedEvent(val walletId: String) : LoggingEvent()

data class WalletDeletedEvent(val walletId: String) : LoggingEvent()

data class SessionWalletCreatedEvent(val walletId: String, val auditWallet: AuditWalletCreated) :
    LoggingEvent()

data class WalletApplicationsUpdatedEvent(
    val walletId: String,
    val updatedApplications: List<AuditWalletApplication>
) : LoggingEvent()

data class WalletDetailsAddedEvent(val walletId: String) : LoggingEvent()

data class WalletOnboardCompletedEvent(
    val walletId: String,
    val auditWallet: AuditWalletCompleted
) : LoggingEvent()

data class WalletOnboardReplacedEvent(val walletId: String, val auditWallet: AuditWalletReplaced) :
    LoggingEvent()

data class ApplicationCreatedEvent(val serviceId: String) : LoggingEvent()

data class ApplicationStatusChangedEvent(
    val serviceId: String,
    val oldStatus: ApplicationStatus,
    val newStatus: ApplicationStatus
) : LoggingEvent()
