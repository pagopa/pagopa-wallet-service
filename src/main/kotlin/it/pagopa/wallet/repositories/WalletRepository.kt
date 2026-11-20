package it.pagopa.wallet.repositories

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.documents.wallets.Wallet
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.Update
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface WalletRepository : ReactiveCrudRepository<Wallet, String> {

    fun findByUserId(userId: String): Flux<Wallet>

    fun findByIdAndUserId(id: String, userId: String): Mono<Wallet>

    fun findByUserIdAndStatus(userId: String, status: WalletStatusDto): Flux<Wallet>

    fun findByContractId(contract: String): Mono<Wallet>

    /** Updates retry counter without triggering last-modified auditing. */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '\$set': { 'deactivationRetryCount': ?2 }, '\$inc': { 'version': 1 } }")
    fun updateDeactivationRetryCount(id: String, version: Int, retryCount: Int): Mono<Long>

    @Query(value = "{ 'userId' : ?0 , 'details.paymentInstrumentGatewayId' : ?1, 'status' : ?2 }")
    fun findByUserIdAndDetailsPaymentInstrumentGatewayIdForWalletStatus(
        userId: String,
        paymentInstrumentGatewayId: String,
        status: WalletStatusDto
    ): Mono<Wallet>
}
