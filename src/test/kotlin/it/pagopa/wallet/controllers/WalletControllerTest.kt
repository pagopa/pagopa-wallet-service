package it.pagopa.wallet.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.WalletTestUtils.WALLET_SERVICE_1
import it.pagopa.wallet.WalletTestUtils.WALLET_SERVICE_2
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.common.tracing.WalletTracing
import it.pagopa.wallet.config.OpenTelemetryTestConfiguration
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletApplicationUpdateData
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.test.test

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletController::class)
@Import(OpenTelemetryTestConfiguration::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class WalletControllerTest {
    @MockBean private lateinit var walletService: WalletService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    @MockBean private lateinit var uniqueIdUtils: UniqueIdUtils

    @Autowired private lateinit var walletTracing: WalletTracing

    @Autowired private lateinit var walletController: WalletController

    @Autowired private lateinit var webClient: WebTestClient

    private val webviewPaymentUrl = URI.create("https://dev.payment-wallet.pagopa.it/onboarding")

    private val objectMapper =
        JsonMapper.builder()
            .addModule(JavaTimeModule())
            .addMixIn(
                WalletStatusErrorPatchRequestDto::class.java,
                WalletStatusPatchRequestDto::class.java
            )
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()

    @BeforeEach
    fun beforeTest() {
        given { uniqueIdUtils.generateUniqueId() }.willReturn(mono { "ABCDEFGHabcdefgh" })
        reset(walletTracing)
    }

    @Test
    fun testCreateWallet() = runTest {
        /* preconditions */

        given { walletService.createWallet(any(), any(), any(), any()) }
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
            .header("x-client-id", "IO")
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
                            "$webviewPaymentUrl#walletId=${WALLET_DOMAIN.id.value}&useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}&paymentMethodId=${WalletTestUtils.CREATE_WALLET_REQUEST.paymentMethodId}"
                        )
                )
            )
    }

    @Test
    fun testCreateSessionWalletWithCard() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
                .sessionData(
                    SessionWalletCreateResponseCardDataDto()
                        .paymentMethodType("cards")
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
        given { walletService.createSessionWallet(eq(userId), eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(WALLET_DOMAIN, SessionWalletCreatedEvent(walletId.toString()))
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId.value}/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", userId.id.toString())
            .bodyValue(
                SessionInputCardDataDto()
                    .serializeRootDiscriminator(SessionInputCardDataDto::class, "cards")
            )
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<SessionWalletCreateResponseDto>()
            .consumeWith { assertEquals(sessionResponseDto, it.responseBody) }
    }

    @Test
    fun testCreateSessionWalletWithAPM() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
                .sessionData(
                    SessionWalletCreateResponseAPMDataDto()
                        .paymentMethodType("apm")
                        .redirectUrl("https://apm-redirect.url")
                )
        given { walletService.createSessionWallet(eq(userId), eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(WALLET_DOMAIN, SessionWalletCreatedEvent(walletId.toString()))
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId.value}/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", userId.id.toString())
            .bodyValue(
                // workaround since this class is the request entrypoint and so discriminator
                // mapping annotation is not read during serialization
                ObjectMapper()
                    .writeValueAsString(WalletTestUtils.APM_SESSION_CREATE_REQUEST)
                    .replace("SessionInputPayPalData", "paypal")
            )
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<SessionWalletCreateResponseDto>()
            .consumeWith { assertEquals(sessionResponseDto, it.responseBody) }
    }

    @Test
    fun testValidateWallet() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val orderId = Instant.now().toString() + "ABCDE"
        val userId = UserId(UUID.randomUUID())
        val wallet = walletDocumentVerifiedWithCardDetails("12345678", "0000", "203012", "?", "MC")
        val response =
            WalletVerifyRequestsResponseDto()
                .orderId(orderId)
                .details(
                    WalletVerifyRequestCardDetailsDto().type("CARD").iframeUrl("http://iFrameUrl")
                )
        given { walletService.validateWalletSession(orderId, walletId, userId) }
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
            .uri("/wallets/${walletId.value}/sessions/${orderId}/validations")
            .header("x-user-id", userId.id.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(stringTest)
    }

    @Test
    fun `deleteWalletById returns 204 when wallet is deleted successfully`() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())

        given { walletService.deleteWallet(walletId, userId) }
            .willReturn(
                Mono.just(LoggedAction(Unit, WalletDeletedEvent(walletId.value.toString())))
            )

        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `deleteWalletById returns 400 on invalid wallet id`() = runTest {
        /* preconditions */
        val walletId = "invalidWalletId"

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `deleteWalletById returns 404 on missing wallet`() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())

        given { walletService.deleteWallet(walletId, userId) }
            .willReturn(Mono.error(WalletNotFoundException(walletId)))

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun testGetWalletByIdUser() = runTest {
        /* preconditions */
        val userId = UUID.randomUUID()
        val walletsDto =
            WalletsDto()
                .addWalletsItem(WalletTestUtils.walletInfoDto())
                .addWalletsItem(WalletTestUtils.walletInfoDtoAPM())
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
        val userId = UserId(UUID.randomUUID())
        val walletInfo = WalletTestUtils.walletInfoDto()
        val jsonToTest = objectMapper.writeValueAsString(walletInfo)
        given { walletService.findWallet(eq(walletId.value), eq(userId.id)) }
            .willReturn(mono { walletInfo })
        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(jsonToTest)
    }

    @Test
    fun `get paypal wallet by id`() {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())
        val walletInfo = WalletTestUtils.walletInfoDtoAPM()
        val jsonToTest = objectMapper.writeValueAsString(walletInfo)
        given { walletService.findWallet(eq(walletId.value), eq(userId.id)) }
            .willReturn(mono { walletInfo })

        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(jsonToTest)
    }

    @Test
    fun testGetWalletCardAuthDataSuccess() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val walletAuthData = WalletTestUtils.walletCardAuthDataDto()
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
    fun testGetWalletAPMAuthDataSuccess() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val walletAuthData = WalletTestUtils.walletAPMAuthDataDto()
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
    fun `wallet applications updated with valid statuses returns 204`() {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val userId = UserId(UUID.randomUUID())

        given { walletService.updateWalletApplications(eq(walletId), eq(userId), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        WalletApplicationUpdateData(
                            successfullyUpdatedApplications =
                                mapOf(
                                    WalletApplicationId(WALLET_SERVICE_1.name) to
                                        WalletApplicationStatus.valueOf(
                                            WALLET_SERVICE_1.status.name
                                        )
                                ),
                            applicationsWithUpdateFailed = mapOf(),
                            updatedWallet = WALLET_DOMAIN.toDocument()
                        ),
                        WalletPatchEvent(WALLET_DOMAIN.id.value.toString())
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .put()
            .uri("/wallets/{walletId}/applications", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .bodyValue(WalletTestUtils.UPDATE_SERVICES_BODY)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `wallet applications updated with errors returns 409 with both succeeded and failed applications`() {
        val mockedInstant = Instant.now()

        Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

            /* preconditions */
            val walletId = WalletId(UUID.randomUUID())
            val userId = UserId(java.util.UUID.randomUUID())
            val walletApplicationUpdateData =
                WalletApplicationUpdateData(
                    successfullyUpdatedApplications =
                        mapOf(
                            WalletApplicationId("PAGOPA") to
                                WalletApplicationStatus.valueOf(WALLET_SERVICE_1.status.name)
                        ),
                    applicationsWithUpdateFailed =
                        mapOf(
                            WalletApplicationId("PAGOPA") to
                                WalletApplicationStatus.valueOf(WALLET_SERVICE_2.status.name)
                        ),
                    updatedWallet = WALLET_DOMAIN.toDocument()
                )

            given { walletService.updateWalletApplications(eq(walletId), eq(userId), any()) }
                .willReturn(
                    mono {
                        LoggedAction(
                            walletApplicationUpdateData,
                            WalletPatchEvent(WALLET_DOMAIN.id.value.toString())
                        )
                    }
                )
            given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
                .willReturn(Flux.empty())

            /* test */
            val expectedResponse =
                WalletApplicationsPartialUpdateDto().apply {
                    updatedApplications =
                        walletApplicationUpdateData.successfullyUpdatedApplications.map {
                            WalletApplicationDto()
                                .name(it.key.id)
                                .status(WalletApplicationStatusDto.valueOf(it.value.name))
                        }
                    failedApplications =
                        walletApplicationUpdateData.applicationsWithUpdateFailed.map {
                            WalletApplicationDto()
                                .name(it.key.id)
                                .status(WalletApplicationStatusDto.valueOf(it.value.name))
                        }
                }

            webClient
                .put()
                .uri(
                    "/wallets/{walletId}/applications",
                    mapOf("walletId" to walletId.value.toString())
                )
                .header("x-user-id", userId.id.toString())
                .bodyValue(WalletTestUtils.UPDATE_SERVICES_BODY)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody(WalletApplicationsPartialUpdateDto::class.java)
                .isEqualTo(expectedResponse)
        }
    }

    @Test
    fun `wallet applications updated with unknown application returns 404`() {
        val mockedInstant = Instant.now()

        Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

            /* preconditions */
            val walletId = WalletId(UUID.randomUUID())
            val userId = UserId(UUID.randomUUID())

            given { walletService.updateWalletApplications(eq(walletId), eq(userId), any()) }
                .willReturn(Mono.error(ApplicationNotFoundException(ApplicationId("UNKNOWN").id)))

            /* test */
            val expectedResponse =
                ProblemJsonDto()
                    .status(404)
                    .title("Application not found")
                    .detail("Application with id 'UNKNOWN' not found")

            val walletUpdateRequest =
                WalletApplicationUpdateRequestDto()
                    .applications(listOf(WALLET_SERVICE_1, WALLET_SERVICE_2))

            webClient
                .put()
                .uri(
                    "/wallets/{walletId}/applications",
                    mapOf("walletId" to walletId.value.toString())
                )
                .header("x-user-id", userId.id.toString())
                .bodyValue(walletUpdateRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(ProblemJsonDto::class.java)
                .isEqualTo(expectedResponse)
        }
    }

    @Test
    fun `notify wallet OK for CARDS`() = runTest {
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
                        WALLET_DOMAIN.copy(status = WalletStatusDto.VALIDATED),
                        WalletNotificationEvent(
                            walletId = walletId.toString(),
                            validationOperationId = operationId,
                            validationOperationResult = OperationResultEnum.EXECUTED.value,
                            validationErrorCode = null,
                            validationOperationTimestamp = Instant.now().toString()
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

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.OK,
                        WalletDetailsType.CARDS,
                        WalletStatusDto.VALIDATED,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.EXECUTED.value
                        )
                    )
                )
            )
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

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.SECURITY_TOKEN_MISMATCH,
                        null,
                        null,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.EXECUTED.value
                        )
                    )
                )
            )
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

    @Test
    fun `notify wallet OK for PAYPAL`() = runTest {
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
                        WALLET_DOMAIN.copy(status = WalletStatusDto.VALIDATED),
                        WalletNotificationEvent(
                            walletId.toString(),
                            operationId,
                            OperationResultEnum.EXECUTED.value,
                            Instant.now().toString(),
                            null,
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
            .bodyValue(
                WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
            )
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.OK,
                        WalletDetailsType.CARDS,
                        WalletStatusDto.VALIDATED,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.EXECUTED.value
                        )
                    )
                )
            )
    }

    @Test
    fun `notify wallet should return 400 bad request for invalid details`() = runTest {
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
                            Instant.now().toString(),
                            null,
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
            .bodyValue(
                """
                {
                    "timestampOperation" : "2023-11-24T09:16:15.913748361Z",
                    "operationResult": "EXECUTED",
                    "operationId": "operationId",
                    "details": {
                        "type": "PAYPAL"
                    }
                }
            """
            )
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.BAD_REQUEST
                    )
                )
            )
    }

    @Test
    fun `notify wallet should return 400 bad request when no paypal details are received for paypal wallet`() =
        runTest {
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
                            WALLET_DOMAIN.copy(status = WalletStatusDto.ERROR),
                            WalletNotificationEvent(
                                walletId.toString(),
                                operationId,
                                OperationResultEnum.EXECUTED.value,
                                Instant.now().toString(),
                                null,
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
                .bodyValue(
                    WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
                )
                .exchange()
                .expectStatus()
                .isBadRequest
                .expectBody()

            verify(walletTracing, times(1))
                .traceWalletUpdate(
                    eq(
                        WalletTracing.WalletUpdateResult(
                            WalletTracing.WalletNotificationOutcome.OK,
                            WalletDetailsType.CARDS,
                            WalletStatusDto.ERROR,
                            WalletTracing.GatewayNotificationOutcomeResult(
                                OperationResultEnum.EXECUTED.value
                            )
                        )
                    )
                )
        }

    @Test
    fun `should throw InvalidRequestException creating wallet for unmanaged OnboardingChannel`() =
        runTest {
            /* preconditions */
            val mockClientId: ClientIdDto = mock()
            given(mockClientId.toString()).willReturn("INVALID")
            /* test */
            val exception =
                assertThrows<InvalidRequestException> {
                    walletController
                        .createWallet(
                            xUserId = UUID.randomUUID(),
                            xClientIdDto = mockClientId,
                            walletCreateRequestDto =
                                Mono.just(WalletTestUtils.CREATE_WALLET_REQUEST),
                            exchange = mock()
                        )
                        .block()
                }

            assertEquals(
                "Input clientId: [INVALID] is unknown. Handled onboarding channels: [IO]",
                exception.message
            )
        }

    @Test
    fun `should return 204 when update last wallet usage successfully`() = runTest {
        val wallet = WalletTestUtils.walletDocument()
        val updateRequest =
            Mono.just(
                UpdateWalletUsageRequestDto().clientId(ClientIdDto.IO).usageTime(OffsetDateTime.MIN)
            )

        given { walletService.updateWalletUsage(any(), any(), any()) }.willReturn(Mono.just(wallet))

        walletController
            .updateWalletUsage(
                walletId = UUID.fromString(wallet.id),
                updateWalletUsageRequestDto = updateRequest,
                exchange = mock()
            )
            .test()
            .assertNext {
                assertEquals(HttpStatusCode.valueOf(204), it.statusCode)
                verify(walletService)
                    .updateWalletUsage(
                        eq(UUID.fromString(wallet.id)),
                        eq(ClientIdDto.IO),
                        eq(OffsetDateTime.MIN.toInstant())
                    )
            }
            .verifyComplete()
    }

    @Test
    fun `should return 422 when update last wallet usage for non-configured client`() = runTest {
        val wallet = WalletTestUtils.walletDocument()
        val updateRequest =
            UpdateWalletUsageRequestDto()
                .clientId(ClientIdDto.CHECKOUT)
                .usageTime(OffsetDateTime.MIN)

        val error =
            WalletClientConfigurationException(
                WalletId(UUID.fromString(wallet.id)),
                Client.Unknown("unknownClient")
            )
        given { walletService.updateWalletUsage(any(), any(), any()) }.willReturn(Mono.error(error))

        webClient
            .patch()
            .uri("/wallets/{walletId}/usages", mapOf("walletId" to wallet.id))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isEqualTo(422)
    }

    @Test
    fun `should return 409 when patch error state to wallet in non transient state`() {
        val updateRequest =
            WalletStatusErrorPatchRequestDto()
                .status("ERROR")
                .details(WalletStatusErrorPatchRequestDetailsDto().reason("Any Reason"))
                as WalletStatusPatchRequestDto

        given { walletService.patchWalletStateToError(any(), any()) }
            .willReturn(
                Mono.error(
                    WalletConflictStatusException(
                        WalletId.create(),
                        WalletStatusDto.VALIDATION_REQUESTED,
                        setOf(),
                        WalletDetailsType.CARDS
                    )
                )
            )

        webClient
            .patch()
            .uri("/wallets/{walletId}", mapOf("walletId" to WalletId.create().value.toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                updateRequest.serializeRootDiscriminator(
                    WalletStatusErrorPatchRequestDto::class,
                    "ERROR"
                )
            )
            .exchange()
            .expectStatus()
            .isEqualTo(409)
    }

    @ParameterizedTest
    @MethodSource("it.pagopa.wallet.services.WalletServiceTest#walletTransientState")
    fun `should return 204 when successfully patch wallet error state`(
        walletStatusDto: WalletStatusDto
    ) = runTest {
        val wallet = WalletTestUtils.walletDocument().copy(status = walletStatusDto.name)

        given { walletService.patchWalletStateToError(any(), any()) }.willReturn(Mono.just(wallet))

        val updateRequest =
            WalletStatusErrorPatchRequestDto()
                .status("ERROR")
                .details(WalletStatusErrorPatchRequestDetailsDto().reason("Any Reason"))
                as WalletStatusPatchRequestDto

        webClient
            .patch()
            .uri("/wallets/{walletId}", mapOf("walletId" to wallet.id))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                updateRequest.serializeRootDiscriminator(
                    WalletStatusErrorPatchRequestDto::class,
                    "ERROR"
                )
            )
            .exchange()
            .expectStatus()
            .isEqualTo(204)
    }

    @Test
    fun `notify wallet should return 404 when wallet is not found`() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given { walletService.notifyWallet(eq(WalletId(walletId)), any(), any(), any()) }
            .willReturn(WalletNotFoundException(WalletId(walletId)).toMono())
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
        /* test */
        webClient
            .post()
            .uri("/wallets/${walletId}/sessions/${orderId}/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("Authorization", "Bearer $sessionToken")
            .bodyValue(
                WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
            )
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.WALLET_NOT_FOUND,
                        null,
                        null,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.EXECUTED.value
                        )
                    )
                )
            )
    }

    @Test
    fun `notify wallet should return 409 when wallet status conflicts`() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given { walletService.notifyWallet(eq(WalletId(walletId)), any(), any(), any()) }
            .willReturn(
                WalletConflictStatusException(
                        WalletId(walletId),
                        WalletStatusDto.DELETED,
                        setOf(),
                        WalletDetailsType.CARDS
                    )
                    .toMono()
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
            .bodyValue(
                WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
            )
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.WRONG_WALLET_STATUS,
                        WalletDetailsType.CARDS,
                        WalletStatusDto.DELETED,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.EXECUTED.value
                        )
                    )
                )
            )
    }

    @Test
    fun `notify wallet should fails when NPG request contains errors`() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given { walletService.notifyWallet(eq(WalletId(walletId)), any(), any(), any()) }
            .willReturn(
                WalletConflictStatusException(
                        WalletId(walletId),
                        WalletStatusDto.DELETED,
                        setOf(),
                        WalletDetailsType.CARDS
                    )
                    .toMono()
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
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT_WITH_ERRORS)
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.WRONG_WALLET_STATUS,
                        WalletDetailsType.CARDS,
                        WalletStatusDto.DELETED,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.DECLINED.value,
                            "WG001"
                        )
                    )
                )
            )
    }

    @Test
    fun `notify should fail when NPG request contains errors for CARDS`() = runTest {
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
                        WALLET_DOMAIN.copy(
                            validationOperationResult = OperationResultEnum.DECLINED,
                            validationErrorCode = "WG001"
                        ),
                        WalletNotificationEvent(
                            walletId = walletId.toString(),
                            validationOperationId = operationId,
                            validationOperationResult = OperationResultEnum.DECLINED.value,
                            validationErrorCode = "WG001",
                            validationOperationTimestamp = Instant.now().toString()
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
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT_WITH_ERRORS)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()

        verify(walletTracing, times(1))
            .traceWalletUpdate(
                eq(
                    WalletTracing.WalletUpdateResult(
                        WalletTracing.WalletNotificationOutcome.OK,
                        WalletDetailsType.CARDS,
                        WalletStatusDto.CREATED,
                        WalletTracing.GatewayNotificationOutcomeResult(
                            OperationResultEnum.DECLINED.value,
                            "WG001"
                        )
                    )
                )
            )
    }

    @Test
    fun `notify wallet should return 409 when detect optmistic lock error`() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given { walletService.notifyWallet(eq(WalletId(walletId)), any(), any(), any()) }
            .willReturn(Mono.error(OptimisticLockingFailureException("Optimistic lock error")))
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
            .isEqualTo(409)
            .expectBody()
    }

    // workaround since this class is the request entrypoint and so discriminator
    // mapping annotation is not read during serialization
    private fun <K : Any> Any.serializeRootDiscriminator(
        clazz: KClass<K>,
        discriminatorValue: String
    ): String {
        return objectMapper
            .writeValueAsString(this)
            .replace(clazz.simpleName.toString().replace("Dto", ""), discriminatorValue)
    }
}
