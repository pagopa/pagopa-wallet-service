package it.pagopa.wallet.domain.migration

import it.pagopa.wallet.domain.wallets.ContractId
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface WalletPaymentManagerRepository {
    fun findByWalletPmId(walletPmId: String): Flux<WalletPaymentManager>
    fun findByContractId(contractId: ContractId): Flux<WalletPaymentManager>
    fun save(association: WalletPaymentManager): Mono<WalletPaymentManager>
}
