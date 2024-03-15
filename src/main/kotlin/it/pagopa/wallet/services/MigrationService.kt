package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletDetailsAddedEvent
import it.pagopa.wallet.config.WalletMigrationConfig
import it.pagopa.wallet.domain.migration.WalletPaymentManager
import it.pagopa.wallet.domain.migration.WalletPaymentManagerRepository
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.exception.MigrationError
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.time.Instant
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmptyDeferred
import reactor.kotlin.core.publisher.toMono

@Service
class MigrationService(
    private val walletPaymentManagerRepository: WalletPaymentManagerRepository,
    private val walletRepository: WalletRepository,
    private val loggingEventRepository: LoggingEventRepository,
    private val uniqueIdUtils: UniqueIdUtils,
    walletMigrationConfig: WalletMigrationConfig
) {

    private val cardPaymentMethodId =
        PaymentMethodId(UUID.fromString(walletMigrationConfig.cardPaymentMethodId))
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    fun initializeWalletByPaymentManager(
        paymentManagerWalletId: String,
        userId: UserId
    ): Mono<Wallet> {
        logger.info(
            "Initialize wallet for PaymentManager Id $paymentManagerWalletId and userId: $userId"
        )
        val now = Instant.now()
        return walletPaymentManagerRepository
            .findByWalletPmId(paymentManagerWalletId)
            .switchIfEmptyDeferred { createMigrationData(paymentManagerWalletId) }
            .flatMap { createWalletByPaymentManager(it, userId, cardPaymentMethodId, now) }
            .doOnError { logger.error(it.message, it) }
            .toMono()
    }

    fun updateWalletCardDetails(contractId: ContractId, cardDetails: CardDetails): Mono<Wallet> {
        val now = Instant.now()
        return findWalletByContractId(contractId)
            .flatMap { currentWallet ->
                when (currentWallet.status) {
                    WalletStatusDto.VALIDATED -> Flux.just(currentWallet)
                    WalletStatusDto.ERROR,
                    WalletStatusDto.DELETED ->
                        MigrationError.WalletIllegalStateTransition(
                                currentWallet.id,
                                currentWallet.status
                            )
                            .toMono()
                    else ->
                        currentWallet
                            .copy(
                                details = cardDetails,
                                status = WalletStatusDto.VALIDATED,
                                updateDate = now,
                            )
                            .toDocument()
                            .let(walletRepository::save)
                            .map { LoggedAction(it.toDomain(), WalletDetailsAddedEvent(it.id)) }
                            .flatMap { it.saveEvents(loggingEventRepository) }
                }
            }
            .switchIfEmpty(MigrationError.WalletContractIdNotFound(contractId).toMono())
            .doOnComplete { logger.info("Validated wallet") }
            .toMono()
    }

    private fun findWalletByContractId(contractId: ContractId): Flux<Wallet> =
        walletPaymentManagerRepository
            .findByContractId(contractId)
            .flatMap { walletRepository.findById(it.walletId.value.toString()) }
            .map { it.toDomain() }

    private fun createWalletByPaymentManager(
        migration: WalletPaymentManager,
        userId: UserId,
        paymentMethodId: PaymentMethodId,
        creationTime: Instant
    ): Mono<Wallet> {
        val newWallet =
            Wallet(
                id = migration.walletId,
                userId = userId,
                contractId = migration.contractId,
                status = WalletStatusDto.CREATED,
                paymentMethodId = paymentMethodId,
                creationDate = creationTime,
                updateDate = creationTime,
                version = 0,
            )
        return walletRepository
            .save(newWallet.toDocument())
            .map { LoggedAction(it.toDomain(), WalletAddedEvent(it.id)) }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .onErrorResume(DuplicateKeyException::class.java) {
                walletRepository.findById(migration.walletId.value.toString()).map { it.toDomain() }
            }
    }

    private fun createMigrationData(paymentManagerWalletId: String): Mono<WalletPaymentManager> {
        return uniqueIdUtils
            .generateUniqueId()
            .map {
                WalletPaymentManager(
                    walletPmId = paymentManagerWalletId,
                    walletId = WalletId.create(),
                    contractId = ContractId(it),
                )
            }
            .flatMap { walletPaymentManagerRepository.save(it) }
    }
}
