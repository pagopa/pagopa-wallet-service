package it.pagopa.wallet.repositories

import it.pagopa.wallet.domain.Wallet
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface WalletRepository : ReactiveCrudRepository<Wallet, String> {

    fun findByContractNumber(contractNumber: String): Mono<Wallet>
}
