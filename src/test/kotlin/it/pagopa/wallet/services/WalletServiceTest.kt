package it.pagopa.wallet.services

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils.CONTRACT_ID
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_DOCUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.WalletRepository
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@OptIn(ExperimentalCoroutinesApi::class)
class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()

    private val walletService: WalletService = WalletService(walletRepository)

    @Test
    fun `should save wallet document`() {
        /* preconditions */

        val mockedUUID = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.answers { mockedUUID }

        val expectedLoggedAction =
            LoggedAction(
                WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT,
                WalletAddedEvent(WALLET_UUID.value.toString())
            )

        given { walletRepository.save(any()) }
            .willReturn(mono { WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT })

        /* test */

        StepVerifier.create(
                walletService.createWallet(
                    listOf(SERVICE_NAME),
                    USER_ID.id,
                    PAYMENT_METHOD_ID.value,
                    CONTRACT_ID.contractId
                )
            )
            .expectNextMatches {
                expectedLoggedAction.data.id == WALLET_UUID &&
                    expectedLoggedAction.data.userId == USER_ID &&
                    expectedLoggedAction.data.services.isEmpty() &&
                    expectedLoggedAction.data.status == WalletStatusDto.CREATED &&
                    expectedLoggedAction.data.contractId == CONTRACT_ID &&
                    expectedLoggedAction.data.details == null &&
                    expectedLoggedAction.data.paymentInstrumentId == null &&
                    expectedLoggedAction.data.paymentMethodId == PAYMENT_METHOD_ID
            }
            .verifyComplete()

        unmockkStatic(UUID::class)
    }

    @Test
    fun `should patch wallet document adding services`() {
        /* preconditions */

        val first_mocked_uuid = UUID.randomUUID()
        val second_mocked_uuid = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.answers { first_mocked_uuid }
        every { UUID.fromString(any()) }.answers { second_mocked_uuid }

        val expectedLoggedAction =
            LoggedAction(
                WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT,
                WalletPatchEvent(WALLET_UUID.value.toString())
            )

        val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

        given { walletRepository.findById(any<String>()) }
            .willReturn(mono { WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT })

        given { walletRepository.save(walletArgumentCaptor.capture()) }
            .willReturn(mono { WALLET_DOCUMENT })

        /* test */
        assert(WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT.services.isEmpty())

        StepVerifier.create(
                walletService.patchWallet(
                    WALLET_UUID.value,
                    Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                )
            )
            .expectNextMatches {
                expectedLoggedAction.data.id == WALLET_UUID &&
                    expectedLoggedAction.data.userId == USER_ID &&
                    expectedLoggedAction.data.services.isEmpty() &&
                    expectedLoggedAction.data.status == WalletStatusDto.CREATED &&
                    expectedLoggedAction.data.contractId == CONTRACT_ID &&
                    expectedLoggedAction.data.details == null &&
                    expectedLoggedAction.data.paymentInstrumentId == null &&
                    expectedLoggedAction.data.paymentMethodId == PAYMENT_METHOD_ID
            }
            .verifyComplete()

        val walletDocumentToSave = walletArgumentCaptor.firstValue
        assertEquals(walletDocumentToSave.services.size, 1)

        unmockkStatic(UUID::class)
    }

    @Test
    fun `should patch wallet document editing service status`() {
        /* preconditions */

        val first_mocked_uuid = UUID.randomUUID()
        val second_mocked_uuid = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.answers { first_mocked_uuid }
        every { UUID.fromString(any()) }.answers { second_mocked_uuid }

        val expectedLoggedAction =
            LoggedAction(WALLET_DOMAIN, WalletPatchEvent(WALLET_UUID.value.toString()))

        val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

        given { walletRepository.findById(any<String>()) }.willReturn(mono { WALLET_DOCUMENT })

        given { walletRepository.save(walletArgumentCaptor.capture()) }
            .willReturn(mono { WALLET_DOCUMENT })

        /* test */
        assertEquals(WALLET_DOCUMENT.services.size, 1)
        assertEquals(WALLET_DOCUMENT.services[0].name, SERVICE_NAME.name)
        assertEquals(WALLET_DOCUMENT.services[0].status, ServiceStatus.DISABLED.toString())

        StepVerifier.create(
                walletService.patchWallet(
                    WALLET_UUID.value,
                    Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                )
            )
            .expectNextMatches {
                expectedLoggedAction.data.id == WALLET_UUID &&
                    expectedLoggedAction.data.userId == USER_ID &&
                    expectedLoggedAction.data.services.isNotEmpty() &&
                    expectedLoggedAction.data.status == WalletStatusDto.CREATED &&
                    expectedLoggedAction.data.contractId == CONTRACT_ID &&
                    expectedLoggedAction.data.details != null &&
                    expectedLoggedAction.data.paymentInstrumentId != null &&
                    expectedLoggedAction.data.paymentMethodId == PAYMENT_METHOD_ID
            }
            .verifyComplete()

        val walletDocumentToSave = walletArgumentCaptor.firstValue
        assertEquals(walletDocumentToSave.services.size, 1)
        assertEquals(walletDocumentToSave.services[0].name, SERVICE_NAME.name)
        assertEquals(walletDocumentToSave.services[0].status, ServiceStatus.ENABLED.toString())

        unmockkStatic(UUID::class)
    }

    @Test
    fun `should throws wallet not found exception`() {
        /* preconditions */

        given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())

        /* test */

        StepVerifier.create(
                walletService.patchWallet(
                    WALLET_UUID.value,
                    Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                )
            )
            .expectError(WalletNotFoundException::class.java)
            .verify()

        unmockkStatic(UUID::class)
    }
}
