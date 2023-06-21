package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.ProblemJsonDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.ContractIdNotFoundException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.exception.SecurityTokenMatchException
import it.pagopa.wallet.services.WalletService
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletNotifyController::class)
class WalletNotifyControllerTest {
    @MockBean private lateinit var walletService: WalletService

    private lateinit var walletNotifyController: WalletNotifyController

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        walletNotifyController = WalletNotifyController(walletService)
    }

    @Test
    fun `notify ok return no content`() = runTest {
        /* preconditions */

        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willReturn(WalletTestUtils.VALID_WALLET_WITH_CONTRACT_NUMBER_WELL_KNOWN_CREATED)

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-Id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `notify ko return no content`() = runTest {
        /* preconditions */

        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_KO))
            .willReturn(WalletTestUtils.VALID_WALLET_WITH_CONTRACT_NUMBER_WELL_KNOWN_ERROR)

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-Id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_KO)
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `notify return 400 bad request for missing Correlation-Id header`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willReturn(WalletTestUtils.VALID_WALLET_WITH_CONTRACT_NUMBER_WELL_KNOWN_CREATED)

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
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
    fun `notify return 400 bad request for invalid request`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willReturn(WalletTestUtils.VALID_WALLET_WITH_CONTRACT_NUMBER_WELL_KNOWN_CREATED)

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-id", correlationId.toString())
            .bodyValue("{'test':'not-a-field'}")
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
    fun `notify return 502 if service raises BadGatewayException`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willThrow(BadGatewayException("Bad gateway error message"))

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
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
    fun `notify return 500 if service raises InternalServerErrorException`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willThrow(InternalServerErrorException("Internal server error message"))

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
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
    fun `notify return 404 if service raises ContractIdNotFoundException`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willThrow(ContractIdNotFoundException())

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.NOT_FOUND)
            .expectBody(ProblemJsonDto::class.java)
            .isEqualTo(
                WalletTestUtils.buildProblemJson(
                    HttpStatus.NOT_FOUND,
                    "Wallet not found",
                    "Cannot find wallet with specified contract id"
                )
            )
    }

    @Test
    fun `notify return 401 if service raises ContractIdNotFoundException`() = runTest {
        /* preconditions */
        val correlationId = UUID.randomUUID()

        given(walletService.notify(correlationId, WalletTestUtils.NOTIFY_WALLET_REQUEST_OK))
            .willThrow(SecurityTokenMatchException())

        /* test */
        webClient
            .post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Correlation-id", correlationId.toString())
            .bodyValue(WalletTestUtils.NOTIFY_WALLET_REQUEST_OK)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
            .expectBody(ProblemJsonDto::class.java)
            .isEqualTo(
                WalletTestUtils.buildProblemJson(
                    HttpStatus.UNAUTHORIZED,
                    "Security token match failed",
                    "Cannot match Security token"
                )
            )
    }
}
