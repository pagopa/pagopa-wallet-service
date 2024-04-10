package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.annotations.AggregateRoot
import it.pagopa.wallet.annotations.AggregateRootId
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.wallets.details.WalletDetails
import java.time.Instant

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
    val version: Int,
    val creationDate: Instant,
    val updateDate: Instant,
    val onboardingChannel: OnboardingChannel
) {
    fun updateUsageForClient(
        clientId: ClientIdDto,
        usageTime: Instant
    ): it.pagopa.wallet.domain.wallets.Wallet {
        val newApplications =
            this.applications.map {
                if (it.id == WalletApplicationId("PAGOPA")) {
                    val newMetadata = it.metadata.data.toMutableMap()
                    val lastUsedKeys =
                        setOf(
                            WalletApplicationMetadata.Metadata.LAST_USED_CHECKOUT,
                            WalletApplicationMetadata.Metadata.LAST_USED_IO
                        )
                    val clientLastUsedKey =
                        when (clientId) {
                            ClientIdDto.CHECKOUT ->
                                WalletApplicationMetadata.Metadata.LAST_USED_CHECKOUT
                            ClientIdDto.IO -> WalletApplicationMetadata.Metadata.LAST_USED_IO
                        }
                    newMetadata[clientLastUsedKey] = usageTime.toString()

                    for (key in lastUsedKeys - clientLastUsedKey) {
                        if (!newMetadata.containsKey(key)) {
                            newMetadata[key] = null
                        }
                    }

                    it.copy(metadata = WalletApplicationMetadata(newMetadata))
                } else {
                    it
                }
            }

        return this.copy(applications = newApplications)
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
                version = this.version,
                creationDate = this.creationDate,
                updateDate = this.updateDate,
                onboardingChannel = this.onboardingChannel.toString()
            )

        return wallet
    }
}
