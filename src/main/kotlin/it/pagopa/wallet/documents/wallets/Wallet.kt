package it.pagopa.wallet.documents.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.Wallet
import java.time.Instant
import java.util.*
import org.springframework.data.mongodb.core.mapping.Document

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
    val services: List<WalletService>,
    val details: WalletDetails?
) {

    fun setServices(services: List<WalletService>): it.pagopa.wallet.documents.wallets.Wallet =
        it.pagopa.wallet.documents.wallets.Wallet(
            this.id,
            this.userId,
            this.status,
            this.creationDate,
            this.updateDate,
            this.paymentMethodId,
            this.paymentInstrumentId,
            this.contractId,
            services,
            this.details
        )

    fun toDomain() =
        Wallet(
            WalletId(UUID.fromString(id)),
            UserId(UUID.fromString(userId)),
            WalletStatusDto.CREATED,
            Instant.parse(creationDate),
            Instant.parse(updateDate),
            PaymentMethodId(UUID.fromString(paymentMethodId)),
            PaymentInstrumentId(UUID.fromString(paymentInstrumentId)),
            services.map { s -> s.toDomain() },
            ContractId(contractId),
            details?.toDomain()
        )
}
