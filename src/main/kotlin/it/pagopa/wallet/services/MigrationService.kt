package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletDeletedEvent
import it.pagopa.wallet.audit.WalletDetailsAddedEvent
import it.pagopa.wallet.config.WalletMigrationConfig
import it.pagopa.wallet.domain.migration.WalletPaymentManager
import it.pagopa.wallet.domain.migration.WalletPaymentManagerRepository
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.exception.ApplicationNotFoundException
import it.pagopa.wallet.exception.MigrationError
import it.pagopa.wallet.repositories.ApplicationRepository
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.time.Duration
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
    private val applicationRepository: ApplicationRepository,
    private val loggingEventRepository: LoggingEventRepository,
    private val uniqueIdUtils: UniqueIdUtils,
    private val walletMigrationConfig: WalletMigrationConfig
) {

    private val cardPaymentMethodId =
        PaymentMethodId(UUID.fromString(walletMigrationConfig.cardPaymentMethodId))
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private val pagoPaApplication by lazy {
        applicationRepository
            .findById(walletMigrationConfig.defaultApplicationId)
            .cache(
                { _ -> Duration.ofDays(1) }, // ttl value
                { _ -> Duration.ZERO }, // ttl error
                { Duration.ZERO } // ttl empty
            )
    }

    fun initializeWalletByPaymentManager(
        paymentManagerWalletId: String,
        userId: UserId
    ): Mono<Wallet> {
        logger.info(
            "Initialize wallet for PaymentManager Id $paymentManagerWalletId and userId: ${userId.id}"
        )
        val now = Instant.now()
        return walletPaymentManagerRepository
            .findByWalletPmId(paymentManagerWalletId)
            .switchIfEmptyDeferred { createMigrationData(paymentManagerWalletId) }
            .flatMap { createWalletByPaymentManager(it, userId, cardPaymentMethodId, now) }
            .doOnError { logger.error("Failure during wallet's initialization", it) }
            .toMono()
    }

    fun updateWalletCardDetails(contractId: ContractId, cardDetails: CardDetails): Mono<Wallet> {
        logger.info("Updating wallet details for ${cardDetails.lastFourDigits}")
        val now = Instant.now()
        return findWalletByContractId(contractId)
            .flatMap { currentWallet -> updateWalletCardDetails(currentWallet, cardDetails, now) }
            .switchIfEmpty(MigrationError.WalletContractIdNotFound(contractId).toMono())
            .doOnNext { logger.info("Wallet details updated for ${cardDetails.lastFourDigits}") }
            .doOnError { logger.error("Failure during wallet's card details update", it) }
            .toMono()
    }

    fun deleteWallet(contractId: ContractId): Mono<Wallet> {
        logger.info("Deleting wallet")
        val now = Instant.now()
        return findWalletByContractId(contractId)
            .switchIfEmpty(MigrationError.WalletContractIdNotFound(contractId).toMono())
            .map { it.copy(status = WalletStatusDto.DELETED, updateDate = now) }
            .flatMap { walletRepository.save(it.toDocument()) }
            .map { LoggedAction(it, WalletDeletedEvent(it.id)) }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { it.toDomain() }
            .doOnNext { logger.info("Deleted wallet successfully") }
            .doOnError { logger.error("Failure during wallet delete", it) }
            .toMono()
    }

    private fun updateWalletCardDetails(
        wallet: Wallet,
        cardDetails: CardDetails,
        updateTime: Instant
    ): Mono<Wallet> =
        when (wallet.status) {
            WalletStatusDto.VALIDATED ->
                if (wallet.details?.equals(cardDetails) == true) wallet.toMono()
                else MigrationError.WalletIllegalStateTransition(wallet.id, wallet.status).toMono()
            WalletStatusDto.ERROR,
            WalletStatusDto.DELETED ->
                MigrationError.WalletIllegalStateTransition(wallet.id, wallet.status).toMono()
            else ->
                wallet
                    .copy(
                        details = cardDetails,
                        status = WalletStatusDto.VALIDATED,
                        updateDate = updateTime,
                    )
                    .toDocument()
                    .let(walletRepository::save)
                    .map { LoggedAction(it.toDomain(), WalletDetailsAddedEvent(it.id)) }
                    .flatMap { it.saveEvents(loggingEventRepository) }
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
        return pagoPaApplication
            .map {
                WalletApplication(
                    WalletApplicationId(it.id),
                    WalletApplicationStatus.ENABLED,
                    creationTime,
                    creationTime,
                    WalletApplicationMetadata.of(
                        WalletApplicationMetadata.Metadata.ONBOARD_BY_MIGRATION to
                            creationTime.toString()
                    )
                )
            }
            .map { application ->
                Wallet(
                    id = migration.walletId,
                    userId = userId,
                    contractId = migration.contractId,
                    status = WalletStatusDto.CREATED,
                    paymentMethodId = paymentMethodId,
                    applications = listOf(application),
                    creationDate = creationTime,
                    updateDate = creationTime,
                    onboardingChannel = OnboardingChannel.IO,
                    version = 0,
                )
            }
            .switchIfEmpty(
                Mono.error(ApplicationNotFoundException(walletMigrationConfig.defaultApplicationId))
            )
            .flatMap { walletRepository.save(it.toDocument()) }
            .map { LoggedAction(it.toDomain(), WalletAddedEvent(it.id)) }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .onErrorResume(DuplicateKeyException::class.java) {
                walletRepository.findById(migration.walletId.value.toString()).map { it.toDomain() }
            }
    }

    private fun createMigrationData(paymentManagerWalletId: String): Mono<WalletPaymentManager> =
        uniqueIdUtils
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
