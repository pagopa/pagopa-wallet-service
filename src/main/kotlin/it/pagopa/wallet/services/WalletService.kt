package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.SessionWalletAddedEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.PaymentMethodId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.WalletConflictStatusException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.WalletRepository
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.Map
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono

@Service
@Slf4j
class WalletService(
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper,
    @Autowired private val sessionUrlConfig: SessionUrlConfig
) {

    companion object {
        const val CREATE_HOSTED_ORDER_REQUEST_VERSION: String = "2"
        const val CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR: String = "EUR"
        const val CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT: String = "0"
        const val CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA = "ITA"
        const val CREATE_HOSTED_ORDER_REQUEST_CONTRACT_ID = "xxx"
    }

    fun createWallet(
        serviceList: List<ServiceName>,
        userId: UUID,
        paymentMethodId: UUID
    ): Mono<LoggedAction<Wallet>> {

        return ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId.toString())
            .map {
                val creationTime = Instant.now()
                return@map Wallet(
                    WalletId(UUID.randomUUID()),
                    UserId(userId),
                    WalletStatusDto.CREATED,
                    creationTime,
                    creationTime,
                    PaymentMethodId(UUID.fromString(it.id)),
                    paymentInstrumentId = null,
                    listOf(), // TODO Find all services by serviceName
                    contractId = null,
                    details = null
                )
            }
            .flatMap { wallet ->
                walletRepository.save(wallet.toDocument()).map {
                    LoggedAction(wallet, WalletAddedEvent(wallet.id.value.toString()))
                }
            }
    }

    fun createSessionWallet(walletId: UUID): Mono<Pair<Fields, LoggedAction<Wallet>>> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { it.toDomain() }
            .filter { it.status == WalletStatusDto.CREATED }
            .switchIfEmpty { Mono.error(WalletConflictStatusException(WalletId(walletId))) }
            .flatMap {
                ecommercePaymentMethodsClient
                    .getPaymentMethodById(it.paymentMethodId.value.toString())
                    .map { paymentMethod -> paymentMethod to it }
            }
            .flatMap { (paymentMethod, wallet) ->
                val orderId = UUID.randomUUID().toString().replace("-", "").substring(0, 15)
                val customerId = UUID.randomUUID().toString().replace("-", "").substring(0, 15)
                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                        .build(Map.of("orderId", orderId, "paymentMethodId", paymentMethod.id))

                npgClient
                    .createNpgOrderBuild(
                        UUID.randomUUID(),
                        CreateHostedOrderRequest()
                            .version(CREATE_HOSTED_ORDER_REQUEST_VERSION)
                            .merchantUrl(merchantUrl)
                            .order(
                                Order()
                                    .orderId(orderId)
                                    .amount(CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                    .currency(CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                                    .customerId(customerId)
                            )
                            .paymentSession(
                                PaymentSession()
                                    .actionType(ActionType.VERIFY)
                                    .recurrence(
                                        RecurringSettings()
                                            .action(RecurringAction.CONTRACT_CREATION)
                                            .contractId(CREATE_HOSTED_ORDER_REQUEST_CONTRACT_ID)
                                            .contractType(RecurringContractType.CIT)
                                    )
                                    .amount(CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                    .language(CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                                    .captureType(CaptureType.IMPLICIT)
                                    .paymentService(paymentMethod.name)
                                    .resultUrl(resultUrl.toString())
                                    .cancelUrl(cancelUrl.toString())
                                    .notificationUrl(notificationUrl.toString())
                            )
                    )
                    .map { hostedOrderResponse -> hostedOrderResponse to wallet }
            }
            .map { (hostedOrderResponse, wallet) ->
                hostedOrderResponse to
                    Wallet(
                        wallet.id,
                        wallet.userId,
                        WalletStatusDto.INITIALIZED,
                        wallet.creationDate,
                        wallet.updateDate, // TODO update with auto increment with CHK-2028
                        wallet.paymentMethodId,
                        wallet.paymentInstrumentId,
                        wallet.applications,
                        wallet.contractId,
                        wallet.details
                    )
            }
            .flatMap { (hostedOrderResponse, wallet) ->
                walletRepository.save(wallet.toDocument()).map { hostedOrderResponse to wallet }
            }
            .flatMap { (hostedOrderResponse, wallet) ->
                npgSessionRedisTemplate
                    .save(
                        NpgSession(
                            hostedOrderResponse.sessionId,
                            hostedOrderResponse.sessionId,
                            hostedOrderResponse.securityToken.toString(),
                            wallet.id.value.toString()
                        )
                    )
                    .toMono()
                    .map { hostedOrderResponse to wallet }
            }
            .map { (hostedOrderResponse, wallet) ->
                hostedOrderResponse to
                    LoggedAction(wallet, SessionWalletAddedEvent(wallet.id.value.toString()))
            }
    }

    fun patchWallet(
        walletId: UUID,
        service: Pair<ServiceName, ServiceStatus>
    ): Mono<LoggedAction<Wallet>> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { it.toDomain() to updateServiceList(it, service) }
            .flatMap { (oldService, updatedService) ->
                walletRepository.save(updatedService).thenReturn(oldService)
            }
            .map { LoggedAction(it, WalletPatchEvent(it.id.value.toString())) }
    }

    fun findWallet(walletId: UUID): Mono<WalletInfoDto> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { wallet -> toWalletInfoDto(wallet) }
    }

    fun findWalletByUserId(userId: UUID): Mono<WalletsDto> {
        return walletRepository.findByUserId(userId.toString()).collectList().map { toWallets(it) }
    }

    private fun toWallets(walletList: List<it.pagopa.wallet.documents.wallets.Wallet>): WalletsDto =
        WalletsDto().wallets(walletList.map { toWalletInfoDto(it) })

    private fun toWalletInfoDto(wallet: it.pagopa.wallet.documents.wallets.Wallet): WalletInfoDto? =
        WalletInfoDto()
            .walletId(UUID.fromString(wallet.id))
            .status(WalletStatusDto.valueOf(wallet.status))
            .paymentMethodId(wallet.paymentMethodId)
            .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
            .userId(wallet.userId)
            .updateDate(OffsetDateTime.parse(wallet.updateDate))
            .creationDate(OffsetDateTime.parse(wallet.creationDate))
            .services(
                wallet.applications.map { application ->
                    ServiceDto()
                        .name(ServiceNameDto.valueOf(application.name))
                        .status(ServiceStatusDto.valueOf(application.status))
                }
            )
            .details(toWalletInfoDetailsDto(wallet.details))

    private fun toWalletInfoDetailsDto(details: WalletDetails<*>?): WalletInfoDetailsDto? {
        return when (details) {
            is CardDetails ->
                WalletCardDetailsDto()
                    .type(details.type)
                    .bin(details.bin)
                    .holder(details.holder)
                    .expiryDate(details.expiryDate)
                    .maskedPan(details.maskedPan)
            else -> null
        }
    }

    private fun updateServiceList(
        wallet: it.pagopa.wallet.documents.wallets.Wallet,
        dataService: Pair<ServiceName, ServiceStatus>
    ): it.pagopa.wallet.documents.wallets.Wallet {
        val updatedServiceList = wallet.applications.toMutableList()
        when (
            val index = wallet.applications.indexOfFirst { s -> s.name == dataService.first.name }
        ) {
            -1 ->
                updatedServiceList.add(
                    it.pagopa.wallet.documents.wallets.Application(
                        UUID.randomUUID().toString(),
                        dataService.first.name,
                        dataService.second.name,
                        Instant.now().toString()
                    )
                )
            else -> {
                val oldWalletService = updatedServiceList[index]
                updatedServiceList[index] =
                    it.pagopa.wallet.documents.wallets.Application(
                        oldWalletService.id,
                        oldWalletService.name,
                        dataService.second.name,
                        Instant.now().toString()
                    )
            }
        }
        return wallet.setApplications(updatedServiceList)
    }
}
