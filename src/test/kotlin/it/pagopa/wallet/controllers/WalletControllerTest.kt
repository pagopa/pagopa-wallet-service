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
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.ApplicationNotFoundException
import it.pagopa.wallet.exception.SecurityTokenMatchException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.services.WalletServiceUpdateData
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.time.Instant
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
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

    private val webviewPaymentUrl = URI.create("https://dev.payment-wallet.pagopa.it/onboarding")

    private val objectMapper =
        JsonMapper.builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()

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
                            "$webviewPaymentUrl#walletId=${WALLET_DOMAIN.id.value}&useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}&paymentMethodId=${WalletTestUtils.CREATE_WALLET_REQUEST.paymentMethodId}"
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
            .bodyValue(
                // workaround since this class is the request entrypoint and so discriminator
                // mapping annotation is not read during serialization
                ObjectMapper()
                    .writeValueAsString(SessionInputCardDataDto() as SessionInputDataDto)
                    .replace("SessionInputCardData", "cards")
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
        val walletId = UUID.randomUUID()
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
                .sessionData(
                    SessionWalletCreateResponseAPMDataDto()
                        .paymentMethodType("apm")
                        .redirectUrl("https://apm-redirect.url")
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
    fun `deleteWalletById returns 204 when wallet is deleted successfully`() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())

        given { walletService.deleteWallet(walletId) }
            .willReturn(
                Mono.just(LoggedAction(Unit, WalletDeletedEvent(walletId.value.toString())))
            )

        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
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

        given { walletService.deleteWallet(walletId) }
            .willReturn(Mono.error(WalletNotFoundException(walletId)))

        /* test */
        webClient
            .delete()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
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
    fun `get paypal wallet by id`() {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        val walletInfo = WalletTestUtils.walletInfoDtoAPM()
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
    fun `wallet services updated with valid statuses returns 204`() {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())

        given { walletService.updateWalletServices(any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(
                        WalletServiceUpdateData(
                            successfullyUpdatedApplications =
                                mapOf(
                                    WalletApplicationId(WALLET_SERVICE_1.name.name) to
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
            .uri("/wallets/{walletId}/services", mapOf("walletId" to walletId.value.toString()))
            .bodyValue(WalletTestUtils.UPDATE_SERVICES_BODY)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `wallet services updated with errors returns 409 with both succeeded and failed services`() {
        val mockedInstant = Instant.now()

        Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

            /* preconditions */
            val walletId = WalletId(UUID.randomUUID())
            val walletServiceUpdateData =
                WalletServiceUpdateData(
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

            given { walletService.updateWalletServices(any(), any()) }
                .willReturn(
                    mono {
                        LoggedAction(
                            walletServiceUpdateData,
                            WalletPatchEvent(WALLET_DOMAIN.id.value.toString())
                        )
                    }
                )
            given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
                .willReturn(Flux.empty())

            /* test */
            val expectedResponse =
                WalletServicesPartialUpdateDto().apply {
                    updatedServices =
                        walletServiceUpdateData.successfullyUpdatedApplications.map {
                            WalletServiceDto()
                                .name(ServiceNameDto.valueOf(it.key.id))
                                .status(WalletServiceStatusDto.valueOf(it.value.name))
                        }
                    failedServices =
                        walletServiceUpdateData.applicationsWithUpdateFailed.map {
                            ServiceDto()
                                .name(ServiceNameDto.valueOf(it.key.id))
                                .status(ApplicationStatusDto.valueOf(it.value.name))
                        }
                }

            webClient
                .put()
                .uri("/wallets/{walletId}/services", mapOf("walletId" to walletId.value.toString()))
                .bodyValue(WalletTestUtils.UPDATE_SERVICES_BODY)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody(WalletServicesPartialUpdateDto::class.java)
                .isEqualTo(expectedResponse)
        }
    }

    @Test
    fun `wallet services updated with unknown service returns 404`() {
        val mockedInstant = Instant.now()

        Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

            /* preconditions */
            val walletId = WalletId(UUID.randomUUID())

            given { walletService.updateWalletServices(any(), any()) }
                .willReturn(Mono.error(ApplicationNotFoundException(ApplicationId("UNKNOWN").id)))

            /* test */
            val expectedResponse =
                ProblemJsonDto()
                    .status(404)
                    .title("Service not found")
                    .detail("Service with id 'UNKNOWN' not found")

            val walletUpdateRequest =
                WalletServiceUpdateRequestDto().services(listOf(WALLET_SERVICE_1, WALLET_SERVICE_2))

            webClient
                .put()
                .uri("/wallets/{walletId}/services", mapOf("walletId" to walletId.value.toString()))
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
                        WALLET_DOMAIN,
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
                WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
            )
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
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
        }
}
