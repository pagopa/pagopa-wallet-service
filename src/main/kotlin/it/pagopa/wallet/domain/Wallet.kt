package it.pagopa.wallet.domain

import it.pagopa.generated.wallet.model.ServiceDto
import it.pagopa.generated.wallet.model.TypeDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.details.WalletDetails
import org.springframework.data.mongodb.core.mapping.Document

/**
 * A wallet.
 *
 * A wallet is a collection of payment instruments identified by a single wallet id.
 *
 * @throws IllegalArgumentException if the provided payment instrument list is empty
 */
@Document("wallets")
data class Wallet(
    val id: WalletId,
    val userId: String,
    var status: WalletStatusDto,
    val creationDate: String,
    var updateDate: String,
    val paymentInstrumentType: TypeDto,
    val paymentInstrumentId: PaymentInstrumentId?,
    val gatewaySecurityToken: String,
    val services: List<ServiceDto>,
    val details: WalletDetails?
) {
    init {
        require(services.isNotEmpty()) { "Wallet services cannot be empty!" }
    }
}
