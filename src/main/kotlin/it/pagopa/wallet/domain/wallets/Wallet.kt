package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto
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
    var version: Number? = null
) {

    lateinit var creationDate: Instant
    lateinit var updateDate: Instant

    constructor(
        id: WalletId,
        userId: UserId,
        status: WalletStatusDto,
        paymentMethodId: PaymentMethodId,
        paymentInstrumentId: PaymentInstrumentId?,
        applications: List<Application>,
        contractId: ContractId?,
        validationOperationResult: OperationResultEnum?,
        details: WalletDetails<*>?,
        version: Number?,
        creationDate: Instant,
        updateDate: Instant
    ) : this(
        id,
        userId,
        status,
        paymentMethodId,
        paymentInstrumentId,
        applications,
        contractId,
        validationOperationResult,
        details,
        version
    ) {
        this.version = version
        this.creationDate = creationDate
        this.updateDate = updateDate
    }

    fun deepCopy() =
        it.pagopa.wallet.domain.wallets.Wallet(
            this.id,
            this.userId,
            this.status,
            this.paymentMethodId,
            this.paymentInstrumentId,
            this.applications,
            this.contractId,
            this.validationOperationResult,
            this.details,
            this.version,
            this.creationDate,
            this.updateDate
        )

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
                this.version
            )
        if (this.version != null) {
            // wallet.creationDate =
            // creationDate.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // wallet.updateDate =
            // updateDate.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            wallet.creationDate = this.creationDate
            wallet.updateDate = this.updateDate
        }
        return wallet
    }

    fun applications(applications: List<Application>): it.pagopa.wallet.domain.wallets.Wallet {
        this.applications = applications
        return this
    }

    fun status(status: WalletStatusDto): it.pagopa.wallet.domain.wallets.Wallet {
        this.status = status
        return this
    }

    fun details(details: WalletDetails<*>): it.pagopa.wallet.domain.wallets.Wallet {
        this.details = details
        return this
    }

    fun contractId(contractId: ContractId): it.pagopa.wallet.domain.wallets.Wallet {
        this.contractId = contractId
        return this
    }

    fun validationOperationResult(
        validationOperationResult: WalletNotificationRequestDto.OperationResultEnum?
    ): it.pagopa.wallet.domain.wallets.Wallet {
        this.validationOperationResult = validationOperationResult
        return this
    }
}
