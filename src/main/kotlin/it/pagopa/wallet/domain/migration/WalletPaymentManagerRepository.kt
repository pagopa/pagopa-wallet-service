package it.pagopa.wallet.domain.migration

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface WalletPaymentManagerRepository {
    fun findByWalletPmId(walletPmId: String): Flux<WalletPaymentManager>
    fun save(association: WalletPaymentManager): Mono<WalletPaymentManager>
}
