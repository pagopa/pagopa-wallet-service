package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import it.pagopa.generated.wallet.model.WalletPmCardDetailsRequestDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.services.MigrationService
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.core.publisher.Mono

@WebFluxTest(MigrationController::class)
@TestPropertySource(locations = ["classpath:application.test.properties"])
class MigrationControllerTest {

    @MockBean private lateinit var migrationService: MigrationService

    @Autowired private lateinit var webClient: WebTestClient

    @Test
    fun `should create Wallet successfully`() {
        val paymentManagerId = Random().nextLong()
        val userId = UUID.randomUUID()
        given { migrationService.initializeWalletByPaymentManager(any(), any()) }
            .willAnswer {
                Mono.just(
                    WalletTestUtils.walletDocument()
                        .copy(
                            userId = (it.arguments[1] as UserId).id.toString(),
                            contractId = WalletTestUtils.CONTRACT_ID.contractId
                        )
                        .toDomain()
                )
            }
        webClient
            .put()
            .uri("/migrations/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WalletPmAssociationRequestDto().walletIdPm(paymentManagerId).userId(userId))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("walletIdPm", paymentManagerId)
            .hasJsonPath()
            .jsonPath("contractId", WalletTestUtils.CONTRACT_ID.contractId)
            .exists()
            .jsonPath("walletId", WalletTestUtils.WALLET_UUID.value.toString())
            .exists()
            .jsonPath("status", WalletStatusDto.CREATED)
            .exists()

        argumentCaptor<String> {
            verify(migrationService).initializeWalletByPaymentManager(capture(), any())
            assertEquals(lastValue, paymentManagerId.toString())
        }
    }

    @Test
    fun `should return bad request on malformed request`() {
        webClient
            .put()
            .uri("/migrations/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(MALFORMED_REQUEST)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `should return wallet id when update its details`() {
        val detailsRequest =
            WalletPmCardDetailsRequestDto()
                .newContractIdentifier(UUID.randomUUID().toString())
                .originalContractIdentifier(UUID.randomUUID().toString())
                .cardBin("123456")
                .panTail("7890")
                .paymentCircuit("VISA")
                .paymentGatewayCardId(UUID.randomUUID().toString())
                .expireDate("12/25")
        webClient
            .put()
            .uri("/migrations/wallets/updateDetails")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(detailsRequest)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<String>()
    }

    companion object {
        val MALFORMED_REQUEST =
            """
            {
                "walletIdPm": "123",
                "wrongField": "123"
            }
        """
                .trimIndent()
    }
}
