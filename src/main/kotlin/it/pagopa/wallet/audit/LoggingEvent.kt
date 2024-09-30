package it.pagopa.wallet.audit

import it.pagopa.wallet.domain.applications.ApplicationStatus
import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

@Document("payment-wallets-log-events")
sealed class LoggingEvent(val id: String, val timestamp: String) {
    constructor() : this(UUID.randomUUID().toString(), Instant.now().toString())
}

data class WalletAddedEvent(val walletId: String) : LoggingEvent()

data class WalletMigratedAddedEvent(val walletId: String) : LoggingEvent()

data class WalletDeletedEvent(val walletId: String) : LoggingEvent()

data class SessionWalletCreatedEvent(val walletId: String) : LoggingEvent()

data class WalletApplicationsUpdatedEvent(
    val walletId: String,
    val updatedApplications: List<AuditWalletApplication>
) : LoggingEvent()

data class WalletDetailsAddedEvent(val walletId: String) : LoggingEvent()

data class WalletOnboardCompletedEvent(val walletId: String, val auditWallet: AuditWallet) :
    LoggingEvent()

data class ApplicationCreatedEvent(val serviceId: String) : LoggingEvent()

data class ApplicationStatusChangedEvent(
    val serviceId: String,
    val oldStatus: ApplicationStatus,
    val newStatus: ApplicationStatus
) : LoggingEvent()
