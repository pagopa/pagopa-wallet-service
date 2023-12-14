package it.pagopa.wallet.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.APM_SESSION_CREATE_REQUEST
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.SecurityTokenMatchException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.time.Instant
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletController::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class WalletControllerTest {
    @MockBean private lateinit var walletService: WalletService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    @MockBean private lateinit var uniqueIdUtils: UniqueIdUtils

    private lateinit var walletController: WalletController

    @Autowired private lateinit var webClient: WebTestClient

    private val objectMapper =
        JsonMapper.builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()

    private val webviewPaymentUrl = URI.create("https://dev.payment-wallet.pagopa.it/onboarding")

    @BeforeEach
    fun beforeTest() {
        walletController = WalletController(walletService, loggingEventRepository)

        given { uniqueIdUtils.generateUniqueId() }.willReturn(mono { "ABCDEFGHabcdefgh" })
    }

    @Test
    fun testCreateWallet() = runTest {
        /* preconditions */

        given { walletService.createWallet(any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WALLET_DOMAIN,
                            WalletAddedEvent(WALLET_DOMAIN.id.value.toString())
                        ),
                        webviewPaymentUrl
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .json(
                objectMapper.writeValueAsString(
                    WalletCreateResponseDto()
                        .walletId(WALLET_DOMAIN.id.value)
                        .redirectUrl(
                            "$webviewPaymentUrl#walletId=${WALLET_DOMAIN.id.value}&useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}"
                        )
                )
            )
    }

    @Test
    fun testCreateSessionWalletWithCard() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
                .sessionData(
                    SessionWalletCreateResponseCardDataDto()
                        .cardFormFields(
                            listOf(
                                FieldDto()
                                    .id(UUID.randomUUID().toString())
                                    .src(URI.create("https://test.it/h"))
                                    .propertyClass("holder")
                                    .propertyClass("h")
                                    .type("type"),
                            )
                        )
                )
        given { walletService.createSessionWallet(eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(WALLET_DOMAIN, SessionWalletAddedEvent(walletId.toString()))
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .bodyValue(SessionInputCardDataDto().apply { paymentMethodType = "cards" })
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(objectMapper.writeValueAsString(sessionResponseDto))
    }

    @Test
    fun testCreateSessionWalletWithAPM() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
                .sessionData(
                    SessionWalletCreateResponseAPMDataDto().redirectUrl("https://apm-redirect.url")
                )
        given { walletService.createSessionWallet(eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(WALLET_DOMAIN, SessionWalletAddedEvent(walletId.toString()))
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .bodyValue(WalletTestUtils.APM_SESSION_CREATE_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(objectMapper.writeValueAsString(sessionResponseDto))
    }

    @Test
    fun testValidateWallet() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = Instant.now().toString() + "ABCDE"
        val wallet =
            walletDocumentVerifiedWithCardDetails(
                "12345678",
                "0000",
                "12/30",
                "?",
                WalletCardDetailsDto.BrandEnum.MASTERCARD
            )
        val response =
            WalletVerifyRequestsResponseDto()
                .orderId(orderId)
                .details(
                    WalletVerifyRequestCardDetailsDto().type("CARD").iframeUrl("http://iFrameUrl")
                )
        given { walletService.validateWalletSession(orderId, walletId) }
            .willReturn(
                mono {
                    Pair(
                        response,
                        LoggedAction(
                            wallet.toDomain(),
                            WalletDetailsAddedEvent(walletId.toString())
                        )
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        val stringTest = objectMapper.writeValueAsString(response)
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions/${orderId}/validations")
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(stringTest)
    }

    @Test
    fun testDeleteWalletById() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun testGetWalletByIdUser() = runTest {
        /* preconditions */
        val userId = UUID.randomUUID()
        val walletsDto = WalletsDto().addWalletsItem(WalletTestUtils.walletInfoDto())
        val stringTest = objectMapper.writeValueAsString(walletsDto)
        given { walletService.findWalletByUserId(userId) }.willReturn(mono { walletsDto })
        /* test */
        webClient
            .get()
            .uri("/wallets")
            .header("x-user-id", userId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(stringTest)
    }

    @Test
    fun testGetWalletById() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val walletInfo = WalletTestUtils.walletInfoDto()
        val jsonToTest = objectMapper.writeValueAsString(walletInfo)
        given { walletService.findWallet(any()) }.willReturn(mono { walletInfo })
        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(jsonToTest)
    }

    @Test
    fun testGetWalletAuthDataSuccess() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val walletAuthData = WalletTestUtils.walletAuthDataDto()
        val jsonToTest = objectMapper.writeValueAsString(walletAuthData)
        given { walletService.findWalletAuthData(walletId) }.willReturn(mono { walletAuthData })
        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}/auth-data", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(jsonToTest)
    }

    @Test
    fun testGetWalletAuthDataNotFoundException() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        given { walletService.findWalletAuthData(walletId) }
            .willReturn(Mono.error(WalletNotFoundException(walletId)))

        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}/auth-data", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", UUID.randomUUID().toString())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
    }

    @Test
    fun testPatchWalletById() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())

        given { walletService.patchWallet(any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(WALLET_DOMAIN, WalletPatchEvent(WALLET_DOMAIN.id.value.toString()))
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .patch()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .bodyValue(WalletTestUtils.FLUX_PATCH_SERVICES)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun testNotifyWallet() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        val operationId = "validationOperationId"
        given {
                walletService.notifyWallet(
                    eq(WalletId(walletId)),
                    eq(orderId),
                    eq(sessionToken),
                    any()
                )
            }
            .willReturn(
                mono {
                    LoggedAction(
                        WALLET_DOMAIN,
                        WalletNotificationEvent(
                            walletId.toString(),
                            operationId,
                            OperationResultEnum.EXECUTED.value,
                            Instant.now().toString()
                        )
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions/${orderId}/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("Authorization", "Bearer $sessionToken")
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
    }

    @Test
    fun testNotifyWalletSecurityTokenMatchException() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given {
                walletService.notifyWallet(
                    eq(WalletId(walletId)),
                    eq(orderId),
                    eq(sessionToken),
                    any()
                )
            }
            .willReturn(Mono.error(SecurityTokenMatchException()))
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions/${orderId}/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("Authorization", "Bearer $sessionToken")
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT)
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectBody()
    }

    @Test
    fun testNotifyWalletSecurityTokenNotFoundException() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID

        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions/${orderId}/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT)
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectBody()
    }

    @Test
    fun testFindSessionOKResponse() = runTest {
        /* preconditions */
        val xUserId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID

        val findSessionResponseDto =
            SessionWalletRetrieveResponseDto()
                .walletId(walletId.toString())
                .orderId(orderId)
                .isFinalOutcome(true)
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_0)

        given { walletService.findSessionWallet(eq(xUserId), eq(WalletId(walletId)), eq(orderId)) }
            .willReturn(Mono.just(findSessionResponseDto))

        /* test */
        webClient
            .get()
            .uri("/wallets/${walletId}/sessions/${orderId}")
            .header("x-user-id", xUserId.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(objectMapper.writeValueAsString(findSessionResponseDto))
    }
}
