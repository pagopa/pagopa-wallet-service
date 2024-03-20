package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.model.WalletCardDetailsDto.BrandEnum
import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import it.pagopa.generated.wallet.model.WalletPmCardDetailsRequestDto
import it.pagopa.generated.wallet.model.WalletPmDeleteRequestDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.domain.wallets.details.ExpiryDate
import it.pagopa.wallet.exception.MigrationError
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
import reactor.kotlin.core.publisher.toMono

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
                WalletTestUtils.walletDocument()
                    .copy(
                        userId = (it.arguments[1] as UserId).id.toString(),
                        contractId = WalletTestUtils.CONTRACT_ID.contractId
                    )
                    .toDomain()
                    .toMono()
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
        given { migrationService.updateWalletCardDetails(any(), any()) }
            .willAnswer { WalletTestUtils.walletDocument().toDomain().toMono() }
        val detailsRequest = createDetailRequest(ContractId(UUID.randomUUID().toString()))
        webClient
            .post()
            .uri("/migrations/wallets/updateDetails")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(detailsRequest)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("walletId", WalletTestUtils.WALLET_UUID.value.toString())
            .exists()

        argumentCaptor<CardDetails> {
            verify(migrationService).updateWalletCardDetails(any(), capture())
            assertEquals(lastValue.bin.bin, "123456")
            assertEquals(lastValue.expiryDate, ExpiryDate("202512"))
            assertEquals(lastValue.brand, BrandEnum.VISA)
            assertEquals(lastValue.lastFourDigits.lastFourDigits, "7890")
        }
    }

    @Test
    fun `should return not found when Wallets no existing for given contract id`() {
        val contractId = ContractId(UUID.randomUUID().toString())
        given { migrationService.updateWalletCardDetails(any(), any()) }
            .willAnswer { MigrationError.WalletContractIdNotFound(contractId).toMono<Wallet>() }
        webClient
            .post()
            .uri("/migrations/wallets/updateDetails")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDetailRequest(contractId))
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `should return bad request when trying to update Wallet from illegal state`() {
        val contractId = ContractId(UUID.randomUUID().toString())
        given { migrationService.updateWalletCardDetails(any(), any()) }
            .willAnswer {
                MigrationError.WalletIllegalStateTransition(
                        WalletId.create(),
                        WalletStatusDto.ERROR
                    )
                    .toMono<Wallet>()
            }
        webClient
            .post()
            .uri("/migrations/wallets/updateDetails")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDetailRequest(contractId))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `should return empty body with ok status when delete an existing Wallet`() {
        webClient
            .post()
            .uri("/migrations/wallets/delete")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                WalletPmDeleteRequestDto().newContractIdentifier(UUID.randomUUID().toString())
            )
            .exchange()
            .expectStatus()
            .isOk
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

        private fun createDetailRequest(contractId: ContractId): WalletPmCardDetailsRequestDto =
            WalletPmCardDetailsRequestDto()
                .newContractIdentifier(contractId.contractId)
                .originalContractIdentifier(UUID.randomUUID().toString())
                .cardBin("123456")
                .lastFourDigits("7890")
                .paymentCircuit("VISA")
                .paymentGatewayCardId(UUID.randomUUID().toString())
                .expiryDate("12/25")
    }
}
