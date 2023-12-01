package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.*

@Document("wallets")
data class Wallet(
        @Id var id: String,
        val userId: String,
        val status: String,
        val paymentMethodId: String,
        val paymentInstrumentId: String?,
        val contractId: String?,
        val validationOperationResult: String?,
        val applications: List<Application>,
        val details: WalletDetails<*>?,
        @Version var version: Number? = null
) {
    @CreatedDate
    lateinit var creationDate: Instant

    @LastModifiedDate
    lateinit var updateDate: Instant

    constructor(
            walletId: String,
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
            version: Number
    ) : this(
            walletId,
            userId,
            status,
            paymentMethodId,
            paymentInstrumentId,
            contractId,
            validationOperationResult,
            applications,
            details,
            version
    ) {
        this.creationDate = creationDate
        this.updateDate = updateDate
    }

    fun toDomain(): Wallet {
        val wallet =
                Wallet(
                        WalletId(UUID.fromString(this.id)),
                        UserId(UUID.fromString(this.userId)),
                        WalletStatusDto.valueOf(this.status),
                        PaymentMethodId(UUID.fromString(this.paymentMethodId)),
                        this.paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
                        this.applications.map { application -> application.toDomain() },
                        this.contractId?.let { ContractId(it) },
                        this.validationOperationResult?.let {
                            OperationResultEnum.valueOf(this.validationOperationResult)
                        },
                        this.details?.toDomain(),
                        this.version
                )
        if (this.version != null) {
            wallet.creationDate = this.creationDate
            wallet.updateDate = this.updateDate
        }
        return wallet
    }
}
