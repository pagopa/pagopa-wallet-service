package it.pagopa.wallet.domain

import java.util.Date
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
    val creationDate: Date,
    var updateDate: Date,
    val paymentInstrumentType: PaymentInstrumentType,
    val paymentInstrumentId: PaymentInstrumentId?,
    val contractNumber: String?,
    val gatewaySecurityToken: String,
    val services: List<WalletServiceEnum>,
    val paymentInstrumentDetail: PaymentInstrumentDetail?
) {
    init {
        require(services.isNotEmpty()) { "Wallets cannot be empty!" }
    }
}
