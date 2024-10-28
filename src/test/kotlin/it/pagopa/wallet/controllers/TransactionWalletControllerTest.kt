package it.pagopa.wallet.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateResponseDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.config.OpenTelemetryTestConfiguration
import it.pagopa.wallet.exception.InvalidRequestException
import it.pagopa.wallet.repositories.LoggingEventRepository
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
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@WebFluxTest(TransactionWalletController::class)
@Import(OpenTelemetryTestConfiguration::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class TransactionWalletControllerTest {

    @MockBean private lateinit var walletService: WalletService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    private lateinit var transactionWalletController: TransactionWalletController

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
        transactionWalletController =
            TransactionWalletController(walletService, loggingEventRepository)
    }

    @Test
    fun testCreateWalletPaymentCardsMethod() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())
                        ),
                        Optional.of(webviewPaymentUrl)
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
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
            .isCreated
            .expectBody()
            .json(
                objectMapper.writeValueAsString(
                    WalletTransactionCreateResponseDto()
                        .walletId(WalletTestUtils.WALLET_DOMAIN.id.value)
                        .redirectUrl(
                            "$webviewPaymentUrl#walletId=${WalletTestUtils.WALLET_DOMAIN.id.value}&useDiagnosticTracing=${WalletTestUtils.CREATE_WALLET_REQUEST.useDiagnosticTracing}"
                        )
                )
            )
    }

    @Test
    fun testCreateWalletPaymentAPMMethod() {
        /* preconditions */
        val transactionId = UUID.randomUUID()
        given { walletService.createWalletForTransaction(any(), any(), any(), any(), any()) }
            .willReturn(
                mono {
                    Pair(
                        LoggedAction(
                            WalletTestUtils.WALLET_DOMAIN,
                            WalletAddedEvent(WalletTestUtils.WALLET_DOMAIN.id.value.toString())
                        ),
                        Optional.empty()
                    )
                }
            )
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn(Flux.empty())
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
            .isCreated
            .expectBody()
            .json(
                objectMapper.writeValueAsString(
                    WalletTransactionCreateResponseDto()
                        .walletId(WalletTestUtils.WALLET_DOMAIN.id.value)
                        .redirectUrl(null)
                )
            )
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
                        exchange = mock()
                    )
                    .block()
            }

        assertEquals(
            "Input clientId: [INVALID] is unknown. Handled onboarding channels: [IO]",
            exception.message
        )
    }
}
