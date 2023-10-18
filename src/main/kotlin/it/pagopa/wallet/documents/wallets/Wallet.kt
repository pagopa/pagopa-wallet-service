package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.*

@Document("wallets")
@EnableMongoAuditing
data class Wallet(
        val id: String,
        val userId: String,
        val status: String,
        @CreatedDate var creationDate: Instant = Instant.now(),
        @LastModifiedDate var updateDate: Instant = Instant.now(),
        val paymentMethodId: String,
        val paymentInstrumentId: String?,
        val contractId: String,
        val applications: List<Application>,
        val details: WalletDetails<*>?
) {

    @PersistenceCreator
    constructor(
            id: String,
            userId: String,
            status: String,
            paymentMethodId: String,
            paymentInstrumentId: String?,
            contractId: String,
            applications: List<Application>,
            details: WalletDetails<*>?
    ) : this(
            id,
            userId,
            status,
            Instant.now(),
            Instant.now(),
            paymentMethodId,
            paymentInstrumentId,
            contractId,
            applications,
            details
    )

    fun setApplications(
            applications: List<Application>
    ): it.pagopa.wallet.documents.wallets.Wallet = this.copy(applications = applications, updateDate = Instant.now())

    fun toDomain() =
            Wallet(
                    WalletId(UUID.fromString(id)),
                    UserId(UUID.fromString(userId)),
                    WalletStatusDto.CREATED,
                    creationDate,
                    updateDate,
                    PaymentMethodId(UUID.fromString(paymentMethodId)),
                    paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
                    applications.map { application -> application.toDomain() },
                    ContractId(contractId),
                    details?.toDomain()
            )
}
