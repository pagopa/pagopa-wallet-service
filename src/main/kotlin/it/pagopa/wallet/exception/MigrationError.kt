package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.ContractId

sealed class MigrationError : Throwable() {
    data class WalletContractIdNotFound(val contractId: ContractId) : MigrationError()
}
