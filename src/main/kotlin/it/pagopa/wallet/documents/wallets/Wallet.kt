package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.*

@Document("wallets")
data class Wallet(
        val userId: String,
        val status: String,
        val paymentMethodId: String,
        val paymentInstrumentId: String?,
        val contractId: String?,
        val validationOperationResult: String?,
        val applications: List<Application>,
        val details: WalletDetails<*>?
) : UuidIdentifiedEntity() {

    constructor(
            id: WalletId,
            userId: String,
            status: String,
            paymentMethodId: String,
            paymentInstrumentId: String?,
            contractId: String?,
            validationOperationResult: String?,
            applications: List<Application>,
            details: WalletDetails<*>?,
            creationDate: Instant,
            updateDate: Instant,
            version: Long
    ) : this(
            userId,
            status,
            paymentMethodId,
            paymentInstrumentId,
            contractId,
            validationOperationResult,
            applications,
            details
    ) {
        this.id = id
        this.creationDate = creationDate
        this.updateDate = updateDate
        this.version = version
    }

    fun setApplications(
            applications: List<Application>
    ): it.pagopa.wallet.documents.wallets.Wallet {
        val wallet = this.copy(applications = applications)
        wallet.id = this.id
        wallet.creationDate = creationDate
        wallet.updateDate = updateDate
        wallet.version = version
        return wallet
    }

    fun toDomain() =
            Wallet(
                    WalletId(id!!.value),
                    UserId(UUID.fromString(userId)),
                    WalletStatusDto.valueOf(status),
                    creationDate,
                    updateDate,
                    PaymentMethodId(UUID.fromString(paymentMethodId)),
                    paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
                    applications.map { application -> application.toDomain() },
                    contractId?.let { ContractId(it) },
                    validationOperationResult?.let {
                        OperationResultEnum.valueOf(validationOperationResult)
                    },
                    details?.toDomain(),
                    version!!
            )
}
