package it.pagopa.wallet.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.domain.wallets.WalletId
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
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

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
        walletController =
            WalletController(
                walletService,
                loggingEventRepository,
                webviewPaymentUrl,
                uniqueIdUtils
            )

        given { uniqueIdUtils.generateUniqueId() }.willReturn(mono { "ABCDEFGHabcdefgh" })
    }

    @Test
    fun testCreateWallet() = runTest {
        /* preconditions */

        given { walletService.createWallet(any(), any(), any()) }
            .willReturn(
                mono {
                    LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))
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
    fun testCreateSessionWallet() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val sessionResponseDto =
            SessionWalletCreateResponseDto()
                .orderId("W3948594857645ruey")
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
        given { walletService.createSessionWallet(walletId) }
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
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
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
}
