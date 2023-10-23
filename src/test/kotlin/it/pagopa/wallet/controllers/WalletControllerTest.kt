package it.pagopa.wallet.controllers

import it.pagopa.generated.npg.model.Field
import it.pagopa.generated.npg.model.Fields
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import java.net.URI
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
        walletController =
            WalletController(
                walletService,
                loggingEventRepository,
                URI.create("https://dev.payment-wallet.pagopa.it/onboarding")
            )
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
    }

    @Test
    fun testCreateSessionWallet() = runTest {
        /* preconditions */
        val walletId = UUID.randomUUID()
        val fields = Fields().sessionId(UUID.randomUUID().toString())
        fields.fields.addAll(
            listOf(
                Field()
                    .id(UUID.randomUUID().toString())
                    .src("https://test.it/h")
                    .propertyClass("holder")
                    .propertyClass("h")
                    .type("type"),
                Field()
                    .id(UUID.randomUUID().toString())
                    .src("https://test.it/p")
                    .propertyClass("pan")
                    .propertyClass("p")
                    .type("type"),
                Field()
                    .id(UUID.randomUUID().toString())
                    .src("https://test.it/c")
                    .propertyClass("cvv")
                    .propertyClass("c")
                    .type("type")
            )
        )
        given { walletService.createSessionWallet(walletId) }
            .willReturn(
                mono {
                    Pair(
                        fields,
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
        // val userId = UUID.randomUUID()
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
