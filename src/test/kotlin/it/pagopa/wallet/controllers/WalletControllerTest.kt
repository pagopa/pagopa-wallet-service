package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.ProblemJsonDto
import it.pagopa.generated.wallet.model.WalletCreateResponseDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.services.WalletService
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
        given(walletService.createWallet())
            .willReturn(
                mono { Pair(WalletTestUtils.VALID_WALLET, WalletTestUtils.GATEWAY_REDIRECT_URL) }
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
                    .walletId(WalletTestUtils.VALID_WALLET.id.value)
                    .redirectUrl(WalletTestUtils.GATEWAY_REDIRECT_URL.toString())
            )
    }

    @Test
    fun `return 400 bad request for missing x-user-id header`() = runTest {
        /* preconditions */
        given(walletService.createWallet())
            .willReturn(
                mono { Pair(WalletTestUtils.VALID_WALLET, WalletTestUtils.GATEWAY_REDIRECT_URL) }
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
        given(walletService.createWallet())
            .willReturn(
                mono { Pair(WalletTestUtils.VALID_WALLET, WalletTestUtils.GATEWAY_REDIRECT_URL) }
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
        given(walletService.createWallet())
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
        given(walletService.createWallet())
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
}
