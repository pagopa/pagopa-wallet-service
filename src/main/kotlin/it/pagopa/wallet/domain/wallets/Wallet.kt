package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.annotations.AggregateRoot
import it.pagopa.wallet.annotations.AggregateRootId
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.details.WalletDetails
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
 *
 *          INITIALIZED
 *              │
 *              │
 *              │
 *              ▼
 *       VERIFY_REQUESTED
 *              │
 *              ├────────► EXPIRED ────────────────────────────────┐
 *              │                                                  │
 *              ▼                                                  │
 *       VERIFY_COMPLETED                                          │
 *              │                                                  │
 *              │                                                  │
 *              ├──────────► EXPIRED ──────────────────────────────┚
 *
 *         }
 *  </pre>
 */
@AggregateRoot
data class Wallet(
    @AggregateRootId val id: WalletId,
    val userId: UserId,
    var status: WalletStatusDto,
    val creationDate: Instant,
    var updateDate: Instant,
    val paymentMethodId: PaymentMethodId,
    val paymentInstrumentId: PaymentInstrumentId?,
    val applications: List<Application>,
    val contractId: ContractId?,
    val validationOperationResult: OperationResultEnum?,
    val details: WalletDetails<*>?
) {
    fun toDocument(): Wallet =
        Wallet(
            this.id.value.toString(),
            this.userId.id.toString(),
            this.status.name,
            this.creationDate.toString(),
            this.updateDate.toString(),
            this.paymentMethodId.value.toString(),
            this.paymentInstrumentId?.value?.toString(),
            this.contractId?.contractId,
            this.validationOperationResult?.value,
            this.applications.map { app ->
                it.pagopa.wallet.documents.wallets.Application(
                    app.id.id.toString(),
                    app.name.name,
                    app.status.name,
                    app.lastUpdate.toString()
                )
            },
            this.details?.toDocument()
        )
}
