package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.details.WalletDetails
import java.time.Instant

/**
 * A wallet.
 *
 * A wallet is a triple of payment instrument, userId and service, that is identified by a single
 * wallet id.
 */
data class Wallet(
    val id: WalletId,
    val userId: UserId,
    var status: WalletStatusDto,
    val creationDate: Instant,
    var updateDate: Instant,
    val paymentMethodId: PaymentMethodId,
    val paymentInstrumentId: PaymentInstrumentId?,
    val services: List<WalletService>,
    val contractId: ContractId,
    val details: WalletDetails<*>?
) {
    fun toDocument(): Wallet =
        Wallet(
            this.id.value.toString(),
            this.userId.id.toString(),
            this.status.name,
            this.creationDate.toString(),
            this.updateDate.toString(),
            this.paymentMethodId.value.toString(),
            this.paymentInstrumentId?.value.toString(),
            this.contractId.contractId,
            this.services.map { ls ->
                it.pagopa.wallet.documents.wallets.WalletService(
                    ls.id.id.toString(),
                    ls.name.name,
                    ls.status.name,
                    ls.lastUpdate.toString()
                )
            },
            this.details?.toDocument()
        )
}
