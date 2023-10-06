package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
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
    val details: WalletDetails?
) {}
