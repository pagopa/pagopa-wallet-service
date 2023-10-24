package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.CreateHostedOrderRequest
import it.pagopa.generated.npg.model.Fields
import it.pagopa.generated.npg.model.Order
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.SessionWalletAddedEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.exception.WalletConflictStatusException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.WalletRepository
import java.time.Instant
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
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
) {

    fun createWallet(
        serviceList: List<it.pagopa.wallet.domain.services.ServiceName>,
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
                npgClient
                    .createNpgOrderBuild(
                        UUID.randomUUID(),
                        CreateHostedOrderRequest()
                            .version("2")
                            .merchantUrl("https://test")
                            .order(Order())
                    )
                    .map { hostedOrderResponse -> hostedOrderResponse to it }
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
