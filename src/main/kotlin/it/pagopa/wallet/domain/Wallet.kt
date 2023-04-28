package it.pagopa.wallet.domain

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
    var status: WalletStatus,
    val creationDate: String,
    var updateDate: String,
    val paymentInstrumentType: PaymentInstrumentType,
    val paymentInstrumentId: PaymentInstrumentId?,
    val contractNumber: String?,
    val gatewaySecurityToken: String,
    val services: List<WalletServiceEnum>,
    val paymentInstrumentDetail: PaymentInstrumentDetail?
) {
    init {
        require(services.isNotEmpty()) { "Wallet services cannot be empty!" }
    }
}
