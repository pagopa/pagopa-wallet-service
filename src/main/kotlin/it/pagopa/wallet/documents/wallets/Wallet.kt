package it.pagopa.wallet.documents.wallets

import com.azure.spring.data.cosmos.core.mapping.PartitionKey
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet as WalletDomain
import java.time.Instant
import java.util.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallets")
data class Wallet(
    @PartitionKey val walletId: String,
    val userId: String,
    val status: String,
    val paymentMethodId: String,
    val paymentInstrumentId: String?,
    val contractId: String?,
    val applications: List<Application>,
    val details: WalletDetails<*>?,
    @CreatedDate val creationDate: Instant? = null,
    @LastModifiedDate val updateDate: Instant? = null,
    @Version var version: Long? = null,
    @Id val _id: String? = null,
) {

    fun setApplications(applications: List<Application>): Wallet =
        this.copy(applications = applications, updateDate = Instant.now())

    fun toDomain() =
        WalletDomain(
            WalletId(UUID.fromString(walletId)),
            UserId(UUID.fromString(userId)),
            WalletStatusDto.valueOf(status),
            PaymentMethodId(UUID.fromString(paymentMethodId)),
            paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
            applications.map { application -> application.toDomain() },
            contractId?.let { ContractId(it) },
            details?.toDomain()
        )
}
