package it.pagopa.wallet.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.services.WalletService
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletController::class)
class WalletControllerTest {
    @MockBean private lateinit var walletService: WalletService

    private lateinit var walletController: WalletController

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        walletController = WalletController(walletService)
    }

    @Test
    fun `wallet is created successfully`() = runTest {
        /* preconditions */

        given(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .willReturn(
                mono {
                    Pair(
                        WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS,
                        WalletTestUtils.GATEWAY_REDIRECT_URL
                    )
                }
            )

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", WalletTestUtils.USER_ID)
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(WalletCreateResponseDto::class.java)
            .isEqualTo(
                WalletCreateResponseDto()
                    .walletId(WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS.id.value)
                    .redirectUrl(WalletTestUtils.GATEWAY_REDIRECT_URL.toString())
            )
    }

    @Test
    fun `return 400 bad request for missing x-user-id header`() = runTest {
        /* preconditions */
        given(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .willReturn(
                mono {
                    Pair(
                        WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS,
                        WalletTestUtils.GATEWAY_REDIRECT_URL
                    )
                }
            )

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody(ProblemJsonDto::class.java)
            .value {
                assertEquals(HttpStatus.BAD_REQUEST.value(), it.status)
                assertEquals("Bad request", it.title)
            }
    }

    @Test
    fun `return 400 bad request for invalid request`() = runTest {
        /* preconditions */
        given(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .willReturn(
                mono {
                    Pair(
                        WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS,
                        WalletTestUtils.GATEWAY_REDIRECT_URL
                    )
                }
            )

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", WalletTestUtils.USER_ID)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody(ProblemJsonDto::class.java)
            .value {
                assertEquals(HttpStatus.BAD_REQUEST.value(), it.status)
                assertEquals("Bad request", it.title)
            }
    }

    @Test
    fun `return 502 if service raises BadGatewayException`() = runTest {
        /* preconditions */
        given(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .willReturn(Mono.error(BadGatewayException("Bad gateway error message")))

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", WalletTestUtils.USER_ID)
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.BAD_GATEWAY)
            .expectBody(ProblemJsonDto::class.java)
            .isEqualTo(
                WalletTestUtils.buildProblemJson(
                    HttpStatus.BAD_GATEWAY,
                    "Bad Gateway",
                    "Bad gateway error message"
                )
            )
    }

    @Test
    fun `return 500 if service raises InternalServerErrorException`() = runTest {
        /* preconditions */
        given(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .willReturn(Mono.error(InternalServerErrorException("Internal server error message")))

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", WalletTestUtils.USER_ID)
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectBody(ProblemJsonDto::class.java)
            .isEqualTo(
                WalletTestUtils.buildProblemJson(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error",
                    "Internal server error message"
                )
            )
    }

    @Test
    fun `GET wallet return wallet successfully`() = runTest {
        /* preconditions */
        val wallet = WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS
        val walletId = wallet.id
        val walletInfo =
            WalletInfoDto()
                .walletId(wallet.id.value)
                .userId(wallet.userId)
                .status(wallet.status)
                .creationDate(OffsetDateTime.parse(wallet.creationDate))
                .updateDate(OffsetDateTime.parse(wallet.updateDate))
                .paymentInstrumentId(wallet.paymentInstrumentId?.value.toString())
                .services(wallet.services)

        given(walletService.getWallet(walletId.value)).willReturn(Mono.just(walletInfo))
        // workaround for timestamp comparison in received response (timezone and so on)
        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.OK)
            .expectBody()
            .json(objectMapper.writeValueAsString(walletInfo))
    }

    @Test
    fun `GET wallet return 404 for wallet not found`() = runTest {
        /* preconditions */
        val wallet = WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS
        val walletId = wallet.id
        given(walletService.getWallet(walletId.value))
            .willReturn(Mono.error(WalletNotFoundException(walletId)))

        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.NOT_FOUND)
            .expectBody(ProblemJsonDto::class.java)
            .isEqualTo(
                WalletTestUtils.buildProblemJson(
                    HttpStatus.NOT_FOUND,
                    "Wallet not found",
                    "Cannot find wallet with id $walletId"
                )
            )
    }
}
