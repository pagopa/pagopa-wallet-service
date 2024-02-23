package it.pagopa.wallet.domain.migration

import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.WalletId

/**
 * Represents the association between the old wallet id of the payment manager and the new wallet
 * id.
 */
data class WalletPaymentManager(
    val walletPmId: String,
    val walletId: WalletId,
    val contractId: ContractId
)
