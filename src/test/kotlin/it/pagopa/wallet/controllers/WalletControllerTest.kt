package it.pagopa.wallet.controllers

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.services.WalletService
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

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
    fun testCreateWallet() = runTest {
        /* preconditions */

        /* test */
        webClient
            .post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WalletTestUtils.CREATE_WALLET_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
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
