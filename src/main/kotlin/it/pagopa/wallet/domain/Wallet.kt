package it.pagopa.wallet.domain

import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

/**
 * A wallet.
 *
 * A wallet is a collection of payment instruments identified by a single wallet id.
 *
 * The following assumptions should always hold:
 * - Wallets are non-empty
 * - No two wallets share a payment instrument with the same id (i.e. the relation `wallet <->
 *   paymentInstrument` is 1:n)
 *
 * @throws IllegalArgumentException if the provided payment instrument list is empty
 */
@Document("wallets")
data class Wallet(val id: WalletId, val userId: String, var status: WalletStatus,
                  val creationDate: Date, var updateDate: Date,
                  val paymentInstrument: PaymentInstrumentType,
                  val paymentInstrumentId: PaymentInstrumentId,
                  val contractNumber: String, val gatewaySecurityToken: String,
                  val services: List<WalletService>, val paymentInstrumentDetail: PaymentInstrumentDetail) {
    init {
        require(services.isNotEmpty()) { "Wallets cannot be empty!" }
    }
}
