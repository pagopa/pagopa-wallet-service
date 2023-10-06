package it.pagopa.wallet.controllers

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
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
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

@OptIn(ExperimentalCoroutinesApi::class)
@WebFluxTest(WalletController::class)
class WalletControllerTest {
    @MockBean private lateinit var walletService: WalletService

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    private lateinit var walletController: WalletController

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        walletController = WalletController(walletService, loggingEventRepository)
    }

    @Test
    fun testCreateWallet() = runTest {
        /* preconditions */

        given { walletService.createWallet(any(), any(), any(), any()) }
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
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isCreated
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
        /* test */
        webClient.get().uri("/wallets").exchange().expectStatus().isOk
    }

    @Test
    fun testGetWalletById() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
        /* test */
        webClient
            .get()
            .uri("/wallets/{walletId}", mapOf("walletId" to walletId.value.toString()))
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun testPatchWalletById() = runTest {
        /* preconditions */
        val walletId = WalletId(UUID.randomUUID())
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
