package it.pagopa.wallet.exception

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.WalletId

/** An ADT which represents domain errors during migration phase. */
sealed class MigrationError : Throwable() {
    data class WalletContractIdNotFound(val contractId: ContractId) : MigrationError()
    data class WalletIllegalStateTransition(val walletId: WalletId, val status: WalletStatusDto) :
        MigrationError()
    data class WalletIllegalTransactionDeleteToValidated(val walletId: WalletId) : MigrationError()
    data class WalletAlreadyOnboarded(val walletId: WalletId) : MigrationError()
}
