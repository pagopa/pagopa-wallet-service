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
class Wallet(
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
    @CreatedDate lateinit var creationDate: Instant

    @LastModifiedDate lateinit var updateDate: Instant

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as it.pagopa.wallet.documents.wallets.Wallet

        if (id != other.id) return false
        if (userId != other.userId) return false
        if (status != other.status) return false
        if (paymentMethodId != other.paymentMethodId) return false
        if (paymentInstrumentId != other.paymentInstrumentId) return false
        if (contractId != other.contractId) return false
        if (validationOperationResult != other.validationOperationResult) return false
        if (applications != other.applications) return false
        if (details != other.details) return false
        if (version != other.version) return false
        if (creationDate != other.creationDate) return false
        if (updateDate != other.updateDate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + paymentMethodId.hashCode()
        result = 31 * result + (paymentInstrumentId?.hashCode() ?: 0)
        result = 31 * result + (contractId?.hashCode() ?: 0)
        result = 31 * result + (validationOperationResult?.hashCode() ?: 0)
        result = 31 * result + applications.hashCode()
        result = 31 * result + (details?.hashCode() ?: 0)
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + creationDate.hashCode()
        result = 31 * result + updateDate.hashCode()
        return result
    }
}
