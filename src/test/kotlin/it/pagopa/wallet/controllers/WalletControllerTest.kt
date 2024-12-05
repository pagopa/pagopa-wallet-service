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
import it.pagopa.wallet.WalletTestUtils.getUniqueId
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.common.tracing.WalletTracing
import it.pagopa.wallet.config.OpenTelemetryTestConfiguration
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.services.LoggingEventSyncWriter
import it.pagopa.wallet.services.WalletApplicationUpdateData
import it.pagopa.wallet.services.WalletEventSinksService
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
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
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletController::class)
@Import(OpenTelemetryTestConfiguration::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class WalletControllerTest {
    @MockBean private lateinit var walletService: WalletService

    @MockBean private lateinit var walletEventSinksService: WalletEventSinksService

    @MockBean private lateinit var uniqueIdUtils: UniqueIdUtils

    @Autowired private lateinit var walletTracing: WalletTracing

    @Autowired private lateinit var walletController: WalletController

    @Autowired private lateinit var webClient: WebTestClient

    @MockBean private lateinit var loggingEventSyncWriter: LoggingEventSyncWriter

    private val webviewPaymentUrl = URI.create("https://dev.payment-wallet.pagopa.it/onboarding")

    private val loggedActionCaptor = argumentCaptor<LoggedAction<*>>()

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
        val pairLoggedActionUri =
            Pair(
                LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString())),
                webviewPaymentUrl
            )
        given { walletService.createWallet(any(), any(), any(), any()) }
            .willReturn(mono { pairLoggedActionUri })
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
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
            .jsonPath("$.walletId")
            .value<String> { walletId ->
                // Assert that the walletId is as expected
                assert(walletId.startsWith(WALLET_DOMAIN.id.value.toString().trim())) {
                    "walletId is not the expected value"
                }
            }
            .jsonPath("$.redirectUrl")
            .value<String> { redirectUrl ->

                // Assert that the redirectUrl starts with the webviewPaymentUrl
                assertTrue(redirectUrl.startsWith("${webviewPaymentUrl}")) {
                    "Redirect URL does not contains the expected walletId in fragment"
                }

                // Assert that the redirectUrl contains the expected base URL
                assertTrue(redirectUrl.contains("#walletId=${WALLET_DOMAIN.id.value}")) {
                    "Redirect URL does not contains the expected walletId in fragment"
                }
                // Check for the presence of other parameters
                assertTrue(
                    redirectUrl.contains(
                        "useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}"
                    )
                ) {
                    "Redirect URL does not contain the expected useDiagnosticTracing parameter"
                }
                assertTrue(
                    redirectUrl.contains(
                        "paymentMethodId=${WalletTestUtils.CREATE_WALLET_REQUEST.paymentMethodId}"
                    )
                ) {
                    "Redirect URL does not contain the expected paymentMethodId parameter"
                }
            }

        verify(walletEventSinksService, times(1)).tryEmitEvent(pairLoggedActionUri.first)
    }

    @Test
    fun testCreateSessionWalletWithCard() = runTest {
        /* preconditions */
        val orderId = getUniqueId()
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
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        given { walletService.createSessionWallet(eq(userId), eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(
                            WALLET_DOMAIN,
                            SessionWalletCreatedEvent(
                                walletId = walletId.toString(),
                                auditWallet = AuditWalletCreated(orderId = orderId)
                            )
                        )
                    )
                }
            )
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
        val orderId = getUniqueId()
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
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        given { walletService.createSessionWallet(eq(userId), eq(walletId), any()) }
            .willReturn(
                mono {
                    Pair(
                        sessionResponseDto,
                        LoggedAction(
                            WALLET_DOMAIN,
                            SessionWalletCreatedEvent(
                                walletId = walletId.toString(),
                                auditWallet = AuditWalletCreated(orderId = orderId)
                            )
                        )
                    )
                }
            )
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
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
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

        given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
            .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .exchange()
            .expectStatus()
            .isNoContent

        verify(loggingEventSyncWriter, times(1)).saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
        val loggedAction = loggedActionCaptor.firstValue
        assertEquals(1, loggedAction.events.size)
        val loggedEvent = loggedAction.events[0] as WalletDeletedEvent
        assertEquals(walletId.value.toString(), loggedEvent.walletId)
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
                        WalletApplicationsUpdatedEvent(
                            WALLET_DOMAIN.id.value.toString(),
                            listOf(
                                AuditWalletApplication(
                                    WALLET_SERVICE_1.name,
                                    WALLET_SERVICE_1.status.name,
                                    Instant.now().toString(),
                                    Instant.now().toString(),
                                    mapOf()
                                )
                            )
                        )
                    )
                }
            )
        given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
            .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }

        /* test */
        webClient
            .put()
            .uri("/wallets/{walletId}/applications", mapOf("walletId" to walletId.value.toString()))
            .header("x-user-id", userId.id.toString())
            .bodyValue(WalletTestUtils.UPDATE_SERVICES_BODY)
            .exchange()
            .expectStatus()
            .isNoContent

        verify(loggingEventSyncWriter, times(1)).saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
        val loggedAction = loggedActionCaptor.firstValue
        assertEquals(1, loggedAction.events.size)
        val loggedEvent = loggedAction.events[0] as WalletApplicationsUpdatedEvent
        assertEquals("PAGOPA", loggedEvent.updatedApplications[0].id)
    }

    @Test
    fun `wallet applications updated with errors returns 409 with both succeeded and failed applications`() {
        val mockedInstant = Instant.now()

        Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

            /* preconditions */
            val walletId = WalletId(UUID.randomUUID())
            val userId = UserId(UUID.randomUUID())
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
                            WalletApplicationsUpdatedEvent(
                                WALLET_DOMAIN.id.value.toString(),
                                listOf(
                                    AuditWalletApplication(
                                        WALLET_SERVICE_1.name,
                                        WALLET_SERVICE_1.status.name,
                                        Instant.now().toString(),
                                        Instant.now().toString(),
                                        mapOf()
                                    )
                                )
                            )
                        )
                    }
                )
            given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
                .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }

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

            verify(loggingEventSyncWriter, times(1))
                .saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
            val loggedAction = loggedActionCaptor.firstValue
            assertEquals(1, loggedAction.events.size)
            val loggedEvent = loggedAction.events[0] as WalletApplicationsUpdatedEvent
            assertEquals("PAGOPA", loggedEvent.updatedApplications[0].id)
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
                    val wallet = WALLET_DOMAIN.copy(status = WalletStatusDto.VALIDATED)
                    LoggedAction(
                        wallet,
                        WalletOnboardCompletedEvent(
                            walletId = wallet.id.toString(),
                            auditWallet =
                                wallet.toAudit().let {
                                    it.validationOperationId =
                                        WalletTestUtils.VALIDATION_OPERATION_ID.toString()
                                    it.validationOperationTimestamp =
                                        WalletTestUtils.TIMESTAMP.toString()
                                    return@let it
                                }
                        )
                    )
                }
            )
        given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
            .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }
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
        verify(loggingEventSyncWriter, times(1)).saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
        val loggedAction = loggedActionCaptor.firstValue
        assertEquals(1, loggedAction.events.size)
        val loggedEvent = loggedAction.events[0] as WalletOnboardCompletedEvent
        assertNull(loggedEvent.auditWallet.validationErrorCode)
        assertEquals("EXECUTED", loggedEvent.auditWallet.validationOperationResult)
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
                    val wallet = WALLET_DOMAIN.copy(status = WalletStatusDto.VALIDATED)
                    LoggedAction(
                        wallet,
                        WalletOnboardCompletedEvent(
                            walletId = wallet.id.toString(),
                            auditWallet =
                                wallet.toAudit().let {
                                    it.validationOperationId =
                                        WalletTestUtils.VALIDATION_OPERATION_ID.toString()
                                    it.validationOperationTimestamp =
                                        WalletTestUtils.TIMESTAMP.toString()
                                    return@let it
                                }
                        )
                    )
                }
            )
        given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
            .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }
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
        verify(loggingEventSyncWriter, times(1)).saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
        val loggedAction = loggedActionCaptor.firstValue
        assertEquals(1, loggedAction.events.size)
        val loggedEvent = loggedAction.events[0] as WalletOnboardCompletedEvent
        assertNull(loggedEvent.auditWallet.validationErrorCode)
        assertEquals("EXECUTED", loggedEvent.auditWallet.validationOperationResult)
    }

    @Test
    fun `notify wallet should return 400 bad request for invalid details`() = runTest {
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
            .willReturn(
                mono {
                    val wallet = WALLET_DOMAIN
                    LoggedAction(
                        wallet,
                        WalletOnboardCompletedEvent(
                            walletId = wallet.id.toString(),
                            auditWallet =
                                wallet.toAudit().let {
                                    it.validationOperationId =
                                        WalletTestUtils.VALIDATION_OPERATION_ID.toString()
                                    it.validationOperationTimestamp =
                                        WalletTestUtils.TIMESTAMP.toString()
                                    return@let it
                                }
                        )
                    )
                }
            )
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
                        val wallet = WALLET_DOMAIN.copy(status = WalletStatusDto.ERROR)
                        LoggedAction(
                            wallet,
                            WalletOnboardCompletedEvent(
                                walletId = wallet.id.toString(),
                                auditWallet =
                                    wallet.toAudit().let {
                                        it.validationOperationId =
                                            WalletTestUtils.VALIDATION_OPERATION_ID.toString()
                                        it.validationOperationTimestamp =
                                            WalletTestUtils.TIMESTAMP.toString()
                                        return@let it
                                    }
                            )
                        )
                    }
                )
            given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
                .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }
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
            verify(loggingEventSyncWriter, times(1))
                .saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
            val loggedAction = loggedActionCaptor.firstValue
            assertEquals(1, loggedAction.events.size)
            val loggedEvent = loggedAction.events[0] as WalletOnboardCompletedEvent
            assertNull(loggedEvent.auditWallet.validationErrorCode)
            assertEquals("EXECUTED", loggedEvent.auditWallet.validationOperationResult)
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
                    val wallet =
                        WALLET_DOMAIN.copy(
                            validationOperationResult = OperationResultEnum.DECLINED,
                            validationErrorCode = "WG001"
                        )
                    LoggedAction(
                        wallet,
                        WalletOnboardCompletedEvent(
                            walletId = wallet.id.toString(),
                            auditWallet =
                                wallet.toAudit().let {
                                    it.validationOperationId =
                                        WalletTestUtils.VALIDATION_OPERATION_ID.toString()
                                    it.validationOperationTimestamp =
                                        WalletTestUtils.TIMESTAMP.toString()
                                    return@let it
                                }
                        )
                    )
                }
            )
        given { loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedActionCaptor.capture()) }
            .willAnswer { Mono.just((it.arguments[0] as LoggedAction<*>).data) }
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
        verify(loggingEventSyncWriter, times(1)).saveEventSyncWithDLQWrite(any<LoggedAction<*>>())
        val loggedAction = loggedActionCaptor.firstValue
        assertEquals(1, loggedAction.events.size)
        val loggedEvent = loggedAction.events[0] as WalletOnboardCompletedEvent
        assertEquals("WG001", loggedEvent.auditWallet.validationErrorCode)
        assertEquals("DECLINED", loggedEvent.auditWallet.validationOperationResult)
    }

    @Test
    fun `notify wallet should return 409 when detect optmistic lock error`() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val orderId = WalletTestUtils.ORDER_ID
        val sessionToken = "sessionToken"
        given { walletService.notifyWallet(eq(WalletId(walletId)), any(), any(), any()) }
            .willReturn(Mono.error(OptimisticLockingFailureException("Optimistic lock error")))
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
