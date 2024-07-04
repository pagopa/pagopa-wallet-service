package it.pagopa.wallet.domain.wallets

import io.vavr.control.Either
import io.vavr.control.Either.left
import io.vavr.control.Either.right
import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.annotations.AggregateRoot
import it.pagopa.wallet.annotations.AggregateRootId
import it.pagopa.wallet.documents.wallets.Wallet as WalletDocument
import it.pagopa.wallet.domain.wallets.details.WalletDetails
import it.pagopa.wallet.exception.WalletClientConfigurationException
import it.pagopa.wallet.exception.WalletConflictStatusException
import java.time.Instant
import org.slf4j.LoggerFactory

/**
 * A wallet.
 *
 * <p>
 * A wallet is a triple of payment instrument, userId and service, that is identified by a single
 * wallet id. </p>
 *
 *  <pre>
 *     {@code
 *                            CREATED
 *                               │
 *                               │
 *                               ▼
 *                          INITIALIZED
 *                               │
 *                               │
 *                               ▼
 *         ┌───────────VALIDATION_REQUESTED ────────────┐
 *         │                     │                      │
 *         ▼                     │                      ▼
 *       ERROR                   │               VALIDATION_EXPIRED
 *                               │
 *                               ▼
 *                           VALIDATED
 *                               │
 *                               │
 *                               ▼
 *                            DELETED
 *
 *         }
 *  </pre>
 */
@AggregateRoot
data class Wallet(
    @AggregateRootId val id: WalletId,
    val userId: UserId,
    var status: WalletStatusDto = WalletStatusDto.CREATED,
    val paymentMethodId: PaymentMethodId,
    var applications: List<WalletApplication> = listOf(),
    var contractId: ContractId? = null,
    var validationOperationResult: OperationResultEnum? = null,
    var validationErrorCode: String? = null,
    val errorReason: String? = null,
    var details: WalletDetails<*>? = null,
    val clients: Map<Client.Id, Client>,
    val version: Int,
    val creationDate: Instant,
    val updateDate: Instant,
    val onboardingChannel: OnboardingChannel
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Wallet::class.java)
        val TRANSIENT_STATUSES =
            setOf(
                WalletStatusDto.CREATED,
                WalletStatusDto.INITIALIZED,
                WalletStatusDto.VALIDATION_REQUESTED
            )
    }

    fun error(reason: String?): Wallet {
        return if (TRANSIENT_STATUSES.contains(status)) {
            copy(status = WalletStatusDto.ERROR, errorReason = reason)
        } else {
            this
        }
    }

    fun updateUsageForClient(clientId: ClientIdDto, usageTime: Instant): Wallet {
        val newClients = clients.toMutableMap()
        val client = Client.Id.fromString(clientId.name)
        val clientData = clients[client]

        if (clientData != null) {
            newClients[client] = clientData.copy(lastUsage = usageTime)
        } else {
            if (client is Client.WellKnown) {
                newClients[client] = Client(Client.Status.ENABLED, usageTime)
            } else {
                logger.error(
                    "Missing unknown client {}: requested usage update to wallet with id {}!",
                    id.value,
                    clientId
                )

                throw WalletClientConfigurationException(this.id, client)
            }
        }

        return this.copy(clients = newClients)
    }

    fun expectInStatus(
        vararg allowedStatuses: WalletStatusDto
    ): Either<WalletConflictStatusException, Wallet> {
        val allowedStatusesSet = setOf(*allowedStatuses)
        return if (allowedStatusesSet.contains(status)) {
            right(this)
        } else {
            left(WalletConflictStatusException(id, status, allowedStatusesSet))
        }
    }

    fun toDocument(): WalletDocument {
        val wallet =
            WalletDocument(
                id = this.id.value.toString(),
                userId = this.userId.id.toString(),
                status = this.status.name,
                paymentMethodId = this.paymentMethodId.value.toString(),
                contractId = this.contractId?.contractId,
                validationOperationResult = this.validationOperationResult?.value,
                validationErrorCode = this.validationErrorCode,
                errorReason = this.errorReason,
                applications =
                    this.applications.map { app ->
                        it.pagopa.wallet.documents.wallets.WalletApplication(
                            app.id.id,
                            app.status.name,
                            app.creationDate.toString(),
                            app.updateDate.toString(),
                            app.metadata.data.mapKeys { it.key.value }
                        )
                    },
                details = this.details?.toDocument(),
                clients =
                    this.clients.entries.associate { it.key.name to it.value.toDocument() }.toMap(),
                version = this.version,
                creationDate = this.creationDate,
                updateDate = this.updateDate,
                onboardingChannel = this.onboardingChannel.toString()
            )

        return wallet
    }

    /** Return input application iff it's present and enabled */
    fun getApplication(walletApplicationId: WalletApplicationId): WalletApplication? =
        applications.singleOrNull { application ->
            application.id == walletApplicationId &&
                application.status == WalletApplicationStatus.ENABLED
        }

    /**
     * Return metadata for wallet application, iff application is present, enabled and metadata
     * contains the searched key
     */
    fun getApplicationMetadata(
        walletApplicationId: WalletApplicationId,
        metadata: WalletApplicationMetadata.Metadata
    ): String? = getApplication(walletApplicationId)?.metadata?.data?.get(metadata)
}
