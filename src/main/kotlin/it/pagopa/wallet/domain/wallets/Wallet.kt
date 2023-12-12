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
    var status: WalletStatusDto = WalletStatusDto.CREATED,
    val paymentMethodId: PaymentMethodId,
    var paymentInstrumentId: PaymentInstrumentId? = null,
    var applications: List<Application> = listOf(),
    var contractId: ContractId? = null,
    var validationOperationResult: OperationResultEnum? = null,
    var details: WalletDetails<*>? = null,
    val version: Int,
    val creationDate: Instant,
    val updateDate: Instant
) {

    fun toDocument(): Wallet {
        val wallet =
            Wallet(
                this.id.value.toString(),
                this.userId.id.toString(),
                this.status.name,
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
                this.details?.toDocument(),
                this.version,
                this.creationDate,
                this.updateDate
            )

        return wallet
    }
}
