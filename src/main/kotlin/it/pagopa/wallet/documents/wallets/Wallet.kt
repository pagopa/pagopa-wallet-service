package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import java.time.Instant
import java.util.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document

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
    @Version var version: Int,
    @CreatedDate var creationDate: Instant,
    @LastModifiedDate var updateDate: Instant
) {
    fun toDomain(): Wallet {
        val wallet =
            Wallet(
                id = WalletId(UUID.fromString(this.id)),
                userId = UserId(UUID.fromString(this.userId)),
                status = WalletStatusDto.valueOf(this.status),
                paymentMethodId = PaymentMethodId(UUID.fromString(this.paymentMethodId)),
                paymentInstrumentId =
                    this.paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
                applications = this.applications.map { application -> application.toDomain() },
                contractId = this.contractId?.let { ContractId(it) },
                validationOperationResult =
                    this.validationOperationResult?.let {
                        OperationResultEnum.valueOf(this.validationOperationResult)
                    },
                details = this.details?.toDomain(),
                version = this.version,
                creationDate = this.creationDate,
                updateDate = this.updateDate
            )
        return wallet
    }
}
