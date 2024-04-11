package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.WalletApplicationStatusConflictException
import it.pagopa.wallet.exception.WalletSecurityTokenNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.toOnboardingChannel
import java.net.URI
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@RestController
@Slf4j
@Validated
class WalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : WalletsApi {

    override fun createWallet(
        xUserId: UUID,
        xClientIdDto: ClientIdDto,
        walletCreateRequestDto: Mono<WalletCreateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletCreateResponseDto>> {

        return walletCreateRequestDto
            .flatMap { request ->
                walletService
                    .createWallet(
                        request.applications.map { s -> WalletApplicationId(s) },
                        userId = xUserId,
                        paymentMethodId = request.paymentMethodId,
                        onboardingChannel = xClientIdDto.toOnboardingChannel()
                    )
                    .flatMap { (loggedAction, returnUri) ->
                        loggedAction.saveEvents(loggingEventRepository).map {
                            Triple(it.id.value, request, returnUri)
                        }
                    }
            }
            .map { (walletId, request, returnUri) ->
                WalletCreateResponseDto()
                    .walletId(walletId)
                    .redirectUrl(
                        UriComponentsBuilder.fromUri(returnUri)
                            .fragment(
                                "walletId=${walletId}&useDiagnosticTracing=${request.useDiagnosticTracing}&paymentMethodId=${request.paymentMethodId}"
                            )
                            .build()
                            .toUriString()
                    )
            }
            .map { ResponseEntity.created(URI.create(it.redirectUrl)).body(it) }
    }

    override fun createSessionWallet(
        xUserId: UUID,
        walletId: UUID,
        sessionInputDataDto: Mono<SessionInputDataDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<SessionWalletCreateResponseDto>> {
        return sessionInputDataDto
            .flatMap { walletService.createSessionWallet(UserId(xUserId), WalletId(walletId), it) }
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
        xUserId: UUID,
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        return walletService
            .deleteWallet(WalletId(walletId), UserId(xUserId))
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map { ResponseEntity.noContent().build() }
    }

    override fun getSessionWallet(
        xUserId: UUID,
        walletId: UUID,
        orderId: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<SessionWalletRetrieveResponseDto>> {
        return walletService.findSessionWallet(xUserId, WalletId(walletId), orderId).map {
            ResponseEntity.ok(it)
        }
    }

    override fun getWalletAuthDataById(
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletAuthDataDto>> {
        return walletService.findWalletAuthData(WalletId(walletId)).map { ResponseEntity.ok(it) }
    }

    override fun getWalletById(
        xUserId: UUID,
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletInfoDto>> {
        return walletService.findWallet(walletId, xUserId).map { ResponseEntity.ok(it) }
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
                .map {
                    /*
                     * @formatter:off
                     * Here wallet can have only VALIDATED or ERROR statuses.
                     * For wallet were NPG gives EXECUTED but wallet status is ERROR will be returned a 400 bad request
                     * since it means that NPG notify request is incoherent with onboarded wallet
                     * @formatter:on
                     */
                    if (
                        it.status == WalletStatusDto.ERROR &&
                            it.validationOperationResult ==
                                WalletNotificationRequestDto.OperationResultEnum.EXECUTED
                    ) {
                        ResponseEntity.badRequest().build()
                    } else {
                        ResponseEntity.ok().build()
                    }
                }
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
    override fun updateWalletApplicationsById(
        xUserId: UUID,
        walletId: UUID,
        patchApplicationDto: Mono<WalletApplicationUpdateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> {
        return patchApplicationDto
            .map { it.applications }
            .flatMap { requestedApplications ->
                walletService.updateWalletApplications(
                    WalletId(walletId),
                    UserId(xUserId),
                    requestedApplications.map {
                        Pair(
                            WalletApplicationId(it.name),
                            WalletApplicationStatus.valueOf(it.status.value)
                        )
                    }
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .flatMap {
                return@flatMap if (it.applicationsWithUpdateFailed.isNotEmpty()) {
                    Mono.error(
                        WalletApplicationStatusConflictException(
                            it.successfullyUpdatedApplications,
                            it.applicationsWithUpdateFailed
                        )
                    )
                } else {
                    Mono.just(it)
                }
            }
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
