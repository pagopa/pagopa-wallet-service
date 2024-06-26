package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletDeletedEvent
import it.pagopa.wallet.audit.WalletDetailsAddedEvent
import it.pagopa.wallet.audit.WalletMigratedAddedEvent
import it.pagopa.wallet.common.tracing.Tracing
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
            "Initialize wallet for paymentManagerId: [{}] and userId: [{}]",
            paymentManagerWalletId,
            userId.id
        )
        val now = Instant.now()
        return walletPaymentManagerRepository
            .findByWalletPmId(paymentManagerWalletId)
            .switchIfEmptyDeferred { createMigrationData(paymentManagerWalletId) }
            .flatMap { walletPaymentManager ->
                Tracing.customizeSpan(
                        createWalletByPaymentManager(
                            walletPaymentManager,
                            userId,
                            cardPaymentMethodId,
                            now
                        )
                    ) {
                        setAttribute(
                            Tracing.WALLET_ID,
                            walletPaymentManager.walletId.value.toString()
                        )
                    }
                    .doOnNext { wallet ->
                        logger.info(
                            "Initialized new Wallet for paymentManagerId: [{}] and userId: [{}]. Wallet id: [{}]",
                            paymentManagerWalletId,
                            userId.id,
                            wallet.id.value
                        )
                    }
                    .doOnError {
                        logger.error(
                            "Failure during wallet creation. paymentManagerId: [${walletPaymentManager.walletPmId}], userId: [${userId.id}], wallet id: [${walletPaymentManager.walletId.value}]",
                            it
                        )
                    }
                    .contextWrite { ctx ->
                        ctx.put(MDC_WALLET_ID, walletPaymentManager.walletId.value.toString())
                    }
            }
            .doOnError { logger.error("Failure during wallet's initialization", it) }
            .toMono()
    }

    fun updateWalletCardDetails(contractId: ContractId, cardDetails: CardDetails): Mono<Wallet> {
        logger.info("Updating wallet details for [{}]", cardDetails.lastFourDigits.lastFourDigits)
        val now = Instant.now()
        return findWalletByContractId(contractId)
            .switchIfEmpty(MigrationError.WalletContractIdNotFound(contractId).toMono())
            .flatMap { wallet ->
                Tracing.customizeSpan(updateWalletCardDetails(wallet, cardDetails, now)) {
                        setAttribute(Tracing.WALLET_ID, wallet.id.value.toString())
                    }
                    .doOnNext {
                        logger.info("Details updated for wallet with id: [{}]", it.id.value)
                    }
                    .contextWrite { it.put(MDC_WALLET_ID, wallet.id.value.toString()) }
            }
            .doOnError(MigrationError.WalletContractIdNotFound::class.java) {
                logger.error("Failure during wallet's card details update: contractId not found")
            }
            .doOnError(MigrationError.WalletAlreadyOnboarded::class.java) {
                logger.error(
                    "Failure during wallet's card details update: wallet already onboarded"
                )
            }
            .doOnError({ e -> e !is MigrationError.WalletContractIdNotFound }) {
                logger.error("Failure during wallet's card details update", it)
            }
            .toMono()
    }

    fun deleteWallet(contractId: ContractId): Mono<Wallet> {
        logger.info("Deleting wallet")
        val now = Instant.now()
        return findWalletByContractId(contractId)
            .switchIfEmpty(MigrationError.WalletContractIdNotFound(contractId).toMono())
            .flatMap { wallet ->
                Tracing.customizeSpan(Mono.just(wallet)) {
                        setAttribute(Tracing.WALLET_ID, wallet.id.value.toString())
                    }
                    .map { it.copy(status = WalletStatusDto.DELETED, updateDate = now) }
                    .flatMap { walletRepository.save(it.toDocument()) }
                    .map { LoggedAction(it, WalletDeletedEvent(it.id)) }
                    .flatMap { it.saveEvents(loggingEventRepository) }
                    .map { it.toDomain() }
                    .doOnNext {
                        logger.info("Wallet with id: [{}] deleted successfully", it.id.value)
                    }
                    .contextWrite { it.put(MDC_WALLET_ID, wallet.id.value.toString()) }
            }
            .doOnError(MigrationError.WalletContractIdNotFound::class.java) {
                logger.error("Failure during wallet delete: contractId not found")
            }
            .doOnError({ e -> e !is MigrationError.WalletContractIdNotFound }) {
                logger.error("Failure during wallet delete", it)
            }
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
                    .toMono()
                    .filterWhen {
                        isCardAlreadyOnboarded(it, cardDetails).map { onboarded -> !onboarded }
                    }
                    .switchIfEmpty(Mono.error(MigrationError.WalletAlreadyOnboarded(wallet.id)))
                    .map { it.toDocument() }
                    .flatMap(walletRepository::save)
                    .map { LoggedAction(it.toDomain(), WalletDetailsAddedEvent(it.id)) }
                    .flatMap { it.saveEvents(loggingEventRepository) }
        }

    private fun isCardAlreadyOnboarded(wallet: Wallet, details: CardDetails): Mono<Boolean> {
        return walletRepository
            .findByUserIdAndDetailsPaymentInstrumentGatewayIdForWalletStatus(
                userId = wallet.userId.id.toString(),
                paymentInstrumentGatewayId =
                    details.paymentInstrumentGatewayId.paymentInstrumentGatewayId,
                status = WalletStatusDto.VALIDATED
            )
            .hasElement()
    }

    private fun findWalletByContractId(contractId: ContractId): Mono<Wallet> =
        walletRepository.findByContractId(contractId.contractId).map { it.toDomain() }

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
                    clients =
                        Client.WellKnown.values().associateWith { _ ->
                            Client(Client.Status.ENABLED, null)
                        },
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
            .map { LoggedAction(it.toDomain(), WalletMigratedAddedEvent(it.id)) }
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

    companion object {
        const val MDC_WALLET_ID = "walletId"
    }
}
