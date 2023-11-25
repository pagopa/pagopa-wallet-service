package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.wallets.Wallet
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface WalletRepository : ReactiveCrudRepository<Wallet, String> {

    fun findByUserId(userId: String): Flux<Wallet>
    fun findByIdAndUserId(id: String, userId: String): Mono<Wallet>
}
