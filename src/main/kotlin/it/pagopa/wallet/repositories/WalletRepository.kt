package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.wallets.Wallet
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface WalletRepository : ReactiveCrudRepository<Wallet, String> {

    fun findByWalletId(walletId: String): Mono<Wallet>
}
