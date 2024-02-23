package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import java.util.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(MigrationController::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class MigrationControllerTest {

    private lateinit var migrationController: MigrationController

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        migrationController = MigrationController()
    }

    @Test
    fun testFakeResponse() {
        webClient
            .put()
            .uri("/migrations/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(REGISTER_WALLET_PM_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("walletIdPm", 123)
            .hasJsonPath()
            .jsonPath("contractId")
            .exists()
            .jsonPath("walletId")
            .exists()
    }

    companion object {
        val REGISTER_WALLET_PM_REQUEST =
            WalletPmAssociationRequestDto().walletIdPm(123).userId(UUID.randomUUID())
    }
}
