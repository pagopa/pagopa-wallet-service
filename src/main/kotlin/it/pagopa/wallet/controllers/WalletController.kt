package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.WalletSecurityTokenNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import java.net.URI
import java.util.*
import kotlin.collections.map
import kotlinx.coroutines.reactor.mono
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@Slf4j
@Validated
class WalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository,
    @Value("\${webview.payment-wallet}") private val webviewPaymentWalletUrl: URI
) : WalletsApi {

    override fun createWallet(
        xUserId: UUID,
        walletCreateRequestDto: Mono<WalletCreateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletCreateResponseDto>> {

        return walletCreateRequestDto
            .flatMap { request ->
                walletService
                    .createWallet(
                        request.services.map { s -> ServiceName(s.name) },
                        userId = xUserId,
                        paymentMethodId = request.paymentMethodId
                    )
                    .flatMap { it.saveEvents(loggingEventRepository) }
                    .map { it.id.value to request.useDiagnosticTracing }
            }
            .map {
                WalletCreateResponseDto()
                    .walletId(it.first)
                    .redirectUrl(
                        UriComponentsBuilder.fromUri(webviewPaymentWalletUrl.toURL().toURI())
                            .fragment("walletId=${it.first}&useDiagnosticTracing=${it.second}")
                            .build()
                            .toUriString()
                    )
            }
            .map { ResponseEntity.created(URI.create(it.redirectUrl)).body(it) }
    }

    override fun createSessionWallet(
        walletId: UUID,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<SessionWalletCreateResponseDto>> {
        return walletService
            .createSessionWallet(walletId)
            .flatMap { (createSessionResponse, walletEvent) ->
                walletEvent.saveEvents(loggingEventRepository).map { createSessionResponse }
            }
            .map { createSessionResponse -> ResponseEntity.ok().body(createSessionResponse) }
    }

    /*
     * @formatter:off
     *
     * Warning kotlin:S6508 - "Unit" should be used instead of "Void"
     * Suppressed because controller interface is generated from openapi descriptor as java code which use Void as return type.
     * Wallet interface is generated using java generator of the following issue with
     * kotlin generator https://github.com/OpenAPITools/openapi-generator/issues/14949
     *
     * @formatter:on
     */
    @SuppressWarnings("kotlin:S6508")
    override fun deleteWalletById(
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        // TODO To be implemented
        return mono { ResponseEntity.noContent().build() }
    }

    override fun getWalletById(
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletInfoDto>> {
        return walletService.findWallet(walletId).map { ResponseEntity.ok(it) }
    }

    override fun getWalletsByIdUser(
        xUserId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletsDto>> {
        return walletService.findWalletByUserId(xUserId).map { ResponseEntity.ok(it) }
    }

    /*
     * @formatter:off
     *
     * Warning kotlin:S6508 - "Unit" should be used instead of "Void"
     * Suppressed because controller interface is generated from openapi descriptor as java code which use Void as return type.
     * Wallet interface is generated using java generator of the following issue with
     * kotlin generator https://github.com/OpenAPITools/openapi-generator/issues/14949
     *
     * @formatter:on
     */
    @SuppressWarnings("kotlin:S6508")
    override fun notifyWallet(
        walletId: UUID,
        orderId: String,
        walletNotificationRequestDto: Mono<WalletNotificationRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        return walletNotificationRequestDto.flatMap { requestDto ->
            getAuthenticationToken(exchange)
                .switchIfEmpty(Mono.error(WalletSecurityTokenNotFoundException()))
                .flatMap { securityToken ->
                    walletService.notifyWallet(
                        WalletId(walletId),
                        orderId,
                        securityToken,
                        requestDto
                    )
                }
                .flatMap { it.saveEvents(loggingEventRepository) }
                .map { ResponseEntity.ok().build() }
        }
    }

    /*
     * @formatter:off
     *
     * Warning kotlin:S6508 - "Unit" should be used instead of "Void"
     * Suppressed because controller interface is generated from openapi descriptor as java code which use Void as return type.
     * Wallet interface is generated using java generator of the following issue with
     * kotlin generator https://github.com/OpenAPITools/openapi-generator/issues/14949
     *
     * @formatter:on
     */
    @SuppressWarnings("kotlin:S6508")
    override fun patchWalletById(
        walletId: UUID,
        patchServiceDto: Flux<PatchServiceDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {

        return patchServiceDto
            .flatMap {
                walletService.patchWallet(
                    walletId,
                    Pair(ServiceName(it.name.name), ServiceStatus.valueOf(it.status.value))
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .collectList()
            .map { ResponseEntity.noContent().build() }
    }

    override fun postWalletValidations(
        walletId: UUID,
        orderId: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletVerifyRequestsResponseDto>> {
        return walletService.validateWalletSession(orderId, walletId).flatMap {
            (response, walletEvent) ->
            walletEvent.saveEvents(loggingEventRepository).map {
                ResponseEntity.ok().body(response)
            }
        }
    }

    private fun getAuthenticationToken(exchange: ServerWebExchange): Mono<String> {
        return Mono.justOrEmpty(
            Optional.ofNullable(exchange.request.headers[HttpHeaders.AUTHORIZATION])
                .orElse(listOf())
                .stream()
                .findFirst()
                .filter { header: String -> header.startsWith("Bearer ") }
                .map { header: String -> header.substring("Bearer ".length) }
        )
    }
}
