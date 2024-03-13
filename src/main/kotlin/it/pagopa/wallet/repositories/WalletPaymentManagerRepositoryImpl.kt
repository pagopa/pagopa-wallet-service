package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.migration.WalletPaymentManagerDocument
import it.pagopa.wallet.domain.migration.WalletPaymentManager
import it.pagopa.wallet.domain.migration.WalletPaymentManagerRepository
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Instant
import java.util.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class WalletPaymentManagerRepositoryImpl(
    @Autowired private val delegate: MongoWalletMigrationRepository
) : WalletPaymentManagerRepository {

    override fun findByWalletPmId(walletPmId: String): Flux<WalletPaymentManager> =
        delegate.findByWalletPmId(walletPmId).map { it.toDomain() }

    override fun findByContractId(contractId: ContractId): Flux<WalletPaymentManager> =
        delegate.findByContractId(contractId.contractId).map { it.toDomain() }

    override fun save(association: WalletPaymentManager): Mono<WalletPaymentManager> =
        delegate.save(association.toDocument()).map { it.toDomain() }

    private fun WalletPaymentManager.toDocument() =
        WalletPaymentManagerDocument(
            walletPmId = walletPmId,
            walletId = walletId.value.toString(),
            contractId = contractId.contractId,
            creationDate = Instant.now()
        )

    private fun WalletPaymentManagerDocument.toDomain() =
        WalletPaymentManager(
            walletPmId = walletPmId,
            walletId = WalletId(UUID.fromString(walletId)),
            contractId = ContractId(contractId)
        )
}

@Repository
interface MongoWalletMigrationRepository :
    ReactiveCrudRepository<WalletPaymentManagerDocument, String> {
    fun findByWalletPmId(walletPmId: String): Flux<WalletPaymentManagerDocument>
    fun findByContractId(contractId: String): Flux<WalletPaymentManagerDocument>
}
