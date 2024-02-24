package it.pagopa.wallet.domain.wallets

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
    val updateDate: Instant
) {

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
                            app.id.id.toString(),
                            app.status.name,
                            app.creationDate.toString(),
                            app.lastUpdate.toString(),
                            app.metadata.data
                        )
                    },
                details = this.details?.toDocument(),
                version = this.version,
                creationDate = this.creationDate,
                updateDate = this.updateDate
            )

        return wallet
    }
}
