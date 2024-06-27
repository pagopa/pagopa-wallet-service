package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.common.tracing.Tracing
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.PspNotFoundException
import it.pagopa.wallet.exception.RestApiException
import it.pagopa.wallet.exception.WalletApplicationStatusConflictException
import it.pagopa.wallet.exception.WalletSecurityTokenNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.toOnboardingChannel
import it.pagopa.wallet.warmup.annotations.WarmupFunction
import it.pagopa.wallet.warmup.utils.WarmupUtils
import java.net.URI
import java.time.Duration
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@RestController
@Slf4j
@Validated
class WalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository,
    private val webClient: WebClient = WebClient.create()
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
            .onErrorMap(PspNotFoundException::class.java) {
                RestApiException(HttpStatus.NOT_FOUND, "Psp not found", it.message.orEmpty())
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

    override fun patchWallet(
        walletId: UUID,
        walletStatusPatchRequestDto: Mono<WalletStatusPatchRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> =
        Tracing.customizeSpan(walletStatusPatchRequestDto) {
                setAttribute(Tracing.WALLET_ID, walletId.toString())
            }
            .cast(WalletStatusErrorPatchRequestDto::class.java)
            .flatMap {
                walletService.patchWalletStateToError(
                    WalletId.of(walletId.toString()),
                    it.details.reason
                )
            }
            .map { ResponseEntity.noContent().build() }

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

    override fun updateWalletUsage(
        walletId: UUID,
        updateWalletUsageRequestDto: Mono<UpdateWalletUsageRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> {
        return updateWalletUsageRequestDto
            .flatMap {
                walletService.updateWalletUsage(walletId, it.clientId, it.usageTime.toInstant())
            }
            .map { ResponseEntity.noContent().build() }
    }

    override fun postWalletValidations(
        xUserId: UUID,
        walletId: UUID,
        orderId: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletVerifyRequestsResponseDto>> {
        return walletService
            .validateWalletSession(orderId, WalletId(walletId), UserId(xUserId))
            .flatMap { (response, walletEvent) ->
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

    @WarmupFunction
    fun createWalletWarmup() {
        webClient
            .post()
            .uri(URI.create(WarmupUtils.WALLETS_URL))
            .contentType(MediaType.APPLICATION_JSON)
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .header(WarmupUtils.CLIENT_ID_HEADER_KEY, "IO")
            .bodyValue(WarmupUtils.walletCreateRequest)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun getWalletsByUserIdWarmup() {
        webClient
            .get()
            .uri(URI.create(WarmupUtils.WALLETS_URL))
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun getWalletByIdWarmup() {
        webClient
            .get()
            .uri(WarmupUtils.WALLETS_ID_RESOURCE_URL, mapOf("walletId" to WarmupUtils.mockedUUID))
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun deleteWalletWarmup() {
        webClient
            .delete()
            .uri(WarmupUtils.WALLETS_ID_RESOURCE_URL, mapOf("walletId" to WarmupUtils.mockedUUID))
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun patchWalletWarmup() {
        webClient
            .patch()
            .uri(WarmupUtils.WALLETS_ID_RESOURCE_URL, mapOf("walletId" to WarmupUtils.mockedUUID))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WarmupUtils.patchWalletStatusErrorRequest)
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun updateWalletApplicationsWarmup() {
        webClient
            .put()
            .uri(
                "${WarmupUtils.WALLETS_ID_RESOURCE_URL}/applications",
                mapOf("walletId" to WarmupUtils.mockedUUID)
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WarmupUtils.walletApplicationUpdateRequestRequest)
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun createSessionWalletWarmup() {
        webClient
            .post()
            .uri(WarmupUtils.WALLETS_SESSIONS_URL, mapOf("walletId" to WarmupUtils.mockedUUID))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WarmupUtils.sessionInputCardDataRequest)
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun getSessionWalletWarmup() {
        webClient
            .get()
            .uri(
                WarmupUtils.WALLETS_SESSIONS_BY_ORDER_ID_URL,
                mapOf("walletId" to WarmupUtils.mockedUUID, "orderId" to WarmupUtils.mockedUUID)
            )
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun postValidationWarmup() {
        webClient
            .post()
            .uri(
                "${WarmupUtils.WALLETS_SESSIONS_BY_ORDER_ID_URL}/validations",
                mapOf("walletId" to WarmupUtils.mockedUUID, "orderId" to WarmupUtils.mockedUUID)
            )
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun postNotificationWarmup() {
        webClient
            .post()
            .uri(
                "${WarmupUtils.WALLETS_SESSIONS_BY_ORDER_ID_URL}/notifications",
                mapOf("walletId" to WarmupUtils.mockedUUID, "orderId" to WarmupUtils.mockedUUID)
            )
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer securityToken")
            .bodyValue(WarmupUtils.walletNotificationRequest)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun getWalletAuthDataWarmup() {
        webClient
            .get()
            .uri(
                "${WarmupUtils.WALLETS_ID_RESOURCE_URL}/auth-data",
                mapOf("walletId" to WarmupUtils.mockedUUID)
            )
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }

    @WarmupFunction
    fun updateWalletUsage() {
        webClient
            .patch()
            .uri(
                "${WarmupUtils.WALLETS_ID_RESOURCE_URL}/usages",
                mapOf("walletId" to WarmupUtils.mockedUUID)
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WarmupUtils.updateWalletUsageRequestDto)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }
}
