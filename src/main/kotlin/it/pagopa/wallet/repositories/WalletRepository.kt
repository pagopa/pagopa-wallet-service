package it.pagopa.wallet.repositories

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.Wallet
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface WalletRepository : ReactiveCrudRepository<Wallet, String> {

    fun findByUserId(userId: String): Flux<Wallet>
    fun findByIdAndUserId(id: String, userId: String): Mono<Wallet>
    fun findByUserIdAndStatus(userId: String, status: WalletStatusDto): Flux<Wallet>

    @Query(
        value = "{ 'userId' : ?0 , 'details.paymentInstrumentGatewayId' : ?1, 'status' : ?2 }",
        fields = "{ '_id' : 1 }"
    )
    fun findByUserIdAndDetailsPaymentInstrumentGatewayIdForWalletStatus(
        userId: String,
        paymentInstrumentGatewayId: String,
        status: WalletStatusDto
    ): Mono<String>
}
