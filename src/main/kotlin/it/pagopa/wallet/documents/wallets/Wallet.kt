package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.*

@Document("wallets")
data class Wallet(
        val id: String,
        val userId: String,
        val status: String,
        val creationDate: String,
        val updateDate: String,
        val paymentMethodId: String,
        val paymentInstrumentId: String?,
        val contractId: String,
        val applications: List<Application>,
        val details: WalletDetails<*>?
) {

    fun setApplications(
            applications: List<Application>
    ): it.pagopa.wallet.documents.wallets.Wallet = this.copy(applications = applications)

    fun toDomain() =
            Wallet(
                    WalletId(UUID.fromString(id)),
                    UserId(UUID.fromString(userId)),
                    WalletStatusDto.CREATED,
                    Instant.parse(creationDate),
                    Instant.parse(updateDate),
                    PaymentMethodId(UUID.fromString(paymentMethodId)),
                    paymentInstrumentId?.let { PaymentInstrumentId(UUID.fromString(it)) },
                    applications.map { application -> application.toDomain() },
                    ContractId(contractId),
                    details?.toDomain()
            )
}
