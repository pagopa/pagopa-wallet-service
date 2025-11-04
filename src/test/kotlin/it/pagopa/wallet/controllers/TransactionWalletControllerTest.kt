package it.pagopa.wallet.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateResponseDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.config.OpenTelemetryTestConfiguration
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.exception.InvalidRequestException
import it.pagopa.wallet.services.WalletEventSinksService
import it.pagopa.wallet.services.WalletService
import java.net.URI
import java.util.*
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@WebFluxTest(TransactionWalletController::class)
@Import(OpenTelemetryTestConfiguration::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class TransactionWalletControllerTest {

    @MockitoBean private lateinit var walletService: WalletService

    @MockitoBean private lateinit var walletEventSinksService: WalletEventSinksService

    private lateinit var transactionWalletController: TransactionWalletController

    @Autowired private lateinit var webClient: WebTestClient

    @Value("\${security.apiKey.primary}") private lateinit var primaryApiKey: String

    private val objectMapper =
        JsonMapper.builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()

    private val webviewPaymentUrl = URI.create("https://dev.payment-wallet.pagopa.it/onboarding")

    @BeforeEach
    fun beforeTest() {
        transactionWalletController =
            TransactionWalletController(walletService, walletEventSinksService, primaryApiKey)
    }

    @Test
    fun testCreateWalletPaymentCardsMethod() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())),
                        Optional.of(webviewPaymentUrl))
                })
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        /* test */
        webClient
            .post()
            .uri("/transactions/${transactionId}/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("x-client-id", "IO")
            .header("webSessionToken", "webSessionToken")
            .bodyValue(WalletTestUtils.CREATE_WALLET_TRANSACTION_REQUEST)
            .header("x-api-key", "primary-key")
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .json(
                objectMapper.writeValueAsString(
                    WalletTransactionCreateResponseDto()
                        .walletId(WalletTestUtils.WALLET_DOMAIN.id.value)
                        .redirectUrl(
                            "$webviewPaymentUrl#walletId=${WalletTestUtils.WALLET_DOMAIN.id.value}&transactionId=${transactionId}&useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}")))
    }

    @Test
    fun testCreateWalletPaymentAPMMethod() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())),
                        Optional.empty())
                })
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        /* test */
        webClient
            .post()
            .uri("/transactions/${transactionId}/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("x-client-id", "IO")
            .header("webSessionToken", "webSessionToken")
            .bodyValue(WalletTestUtils.CREATE_WALLET_TRANSACTION_REQUEST)
            .header("x-api-key", "primary-key")
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .json(
                objectMapper.writeValueAsString(
                    WalletTransactionCreateResponseDto()
                        .walletId(WalletTestUtils.WALLET_DOMAIN.id.value)
                        .redirectUrl(null)))
    }

    @Test
    fun `should throw InvalidRequestException creating wallet for unmanaged OnboardingChannel`() {
        /* preconditions */
        val mockClientId: ClientIdDto = mock()
        given(mockClientId.toString()).willReturn("INVALID")
        /* test */
        val exception =
            assertThrows<InvalidRequestException> {
                transactionWalletController
                    .createWalletForTransaction(
                        xUserId = UUID.randomUUID(),
                        xClientIdDto = mockClientId,
                        transactionId = "",
                        walletTransactionCreateRequestDto =
                            Mono.just(WalletTestUtils.CREATE_WALLET_TRANSACTION_REQUEST),
                        webSessionToken = "webSessionToken",
                        exchange = mock())
                    .block()
            }

        assertEquals(
            "Input clientId: [INVALID] is unknown. Handled onboarding channels: [IO]",
            exception.message)
    }

    @Test
    fun `should return unauthorized if request has not api key header`() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())),
                        Optional.of(webviewPaymentUrl))
                })
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        /* test */
        webClient
            .post()
            .uri("/transactions/${transactionId}/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", UUID.randomUUID().toString())
            .header("x-client-id", "IO")
            .bodyValue(WalletTestUtils.CREATE_WALLET_TRANSACTION_REQUEST)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should return unauthorized if request has wrong api key header`() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())),
                        Optional.of(webviewPaymentUrl))
                })
        given { walletEventSinksService.tryEmitEvent(any<LoggedAction<Wallet>>()) }
            .willAnswer { Mono.just(it.arguments[0]) }
        /* test */
        webClient
            .post()
            .uri("/transactions/${transactionId}/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-api-key", "super-wrong-api-key")
            .bodyValue(WalletTestUtils.CREATE_WALLET_TRANSACTION_REQUEST)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
