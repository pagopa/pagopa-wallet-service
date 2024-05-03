package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.annotations.AggregateRoot
import it.pagopa.wallet.annotations.AggregateRootId
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.wallets.details.WalletDetails
import it.pagopa.wallet.exception.WalletClientConfigurationException
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
    var details: WalletDetails<*>? = null,
    val clients: Map<Client.Id, Client>,
    val version: Int,
    val creationDate: Instant,
    val updateDate: Instant,
    val onboardingChannel: OnboardingChannel
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Wallet::class.java)
    }

    fun updateUsageForClient(
        clientId: ClientIdDto,
        usageTime: Instant
    ): it.pagopa.wallet.domain.wallets.Wallet {
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

    fun toDocument(): Wallet {
        val wallet =
            Wallet(
                id = this.id.value.toString(),
                userId = this.userId.id.toString(),
                status = this.status.name,
                paymentMethodId = this.paymentMethodId.value.toString(),
                contractId = this.contractId?.contractId,
                validationOperationResult = this.validationOperationResult?.value,
                validationErrorCode = this.validationErrorCode,
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
}
