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
        val paymentMethodId: PaymentMethodId,
) {
    var status: WalletStatusDto = WalletStatusDto.CREATED
    var creationDate: Instant? = null
    var updateDate: Instant? = null
    var paymentInstrumentId: PaymentInstrumentId? = null
    var applications: List<Application> = listOf()
    var contractId: ContractId? = null
    var validationOperationResult: OperationResultEnum? = null
    var details: WalletDetails<*>? = null
    var version: Long? = null

    constructor(
            id: WalletId,
            userId: UserId,
            statusDto: WalletStatusDto,
            creationDate: Instant,
            updateDate: Instant,
            paymentMethodId: PaymentMethodId,
            paymentInstrumentId: PaymentInstrumentId?,
            applications: List<Application>,
            contractId: ContractId?,
            validationOperationResult: OperationResultEnum?,
            details: WalletDetails<*>?,
            version: Long?
    ) : this(id, userId, paymentMethodId) {
        this.status = statusDto
        this.creationDate = creationDate
        this.updateDate = updateDate
        this.paymentInstrumentId = paymentInstrumentId
        this.applications = applications
        this.contractId = contractId
        this.validationOperationResult = validationOperationResult
        this.details = details
        this.version = version
    }

    fun toDocument(): Wallet {
        return Wallet(
                id,
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
                this.details?.toDocument()
        )
    }

    fun status(status: WalletStatusDto): it.pagopa.wallet.domain.wallets.Wallet {
        this.status = status
        return this
    }

    fun details(details: WalletDetails<*>): it.pagopa.wallet.domain.wallets.Wallet {
        this.details = details
        return this
    }

    /*companion object {
        fun createWallet(
                userId: UserId,
                paymentMethodId: String
        ) = it.pagopa.wallet.domain.wallets.Wallet(
                null,
                userId,
                WalletStatusDto.CREATED,
                null,
                null,
                PaymentMethodId(UUID.fromString(paymentMethodId)),
                paymentInstrumentId = null,
                listOf(), // TODO Find all services by serviceName
                contractId = null,
                validationOperationResult = null,
                details = null,
                version = null
        )
    }*/
}
