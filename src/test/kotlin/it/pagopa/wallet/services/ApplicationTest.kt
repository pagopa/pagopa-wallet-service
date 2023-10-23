package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils.CONTRACT_ID
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.WalletTestUtils.walletDocumentEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.WalletTestUtils.walletDomainEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.WalletRepository
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ApplicationTest {
    private val walletRepository: WalletRepository = mock()

    private val walletService: WalletService = WalletService(walletRepository)

    private val mockedUUID = UUID.randomUUID()
    private val mockedInstant = Instant.now()

    @Test
    fun `should save wallet document`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            val expectedLoggedAction =
                LoggedAction(
                    walletDomainEmptyServicesNullDetailsNoPaymentInstrument(),
                    WalletAddedEvent(WALLET_UUID.value.toString())
                )

            given { walletRepository.save(any()) }.willAnswer { Mono.just(it.arguments[0]) }

            /* test */

            StepVerifier.create(
                    walletService.createWallet(
                        listOf(SERVICE_NAME),
                        USER_ID.id,
                        PAYMENT_METHOD_ID.value,
                        CONTRACT_ID.contractId
                    )
                )
                .expectNext(expectedLoggedAction)
                .verifyComplete()
        }
    }

    @Test
    fun `should patch wallet document when adding services`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            print("Mocked instant: ${Instant.now()} $mockedInstant")

            val wallet = walletDomainEmptyServicesNullDetailsNoPaymentInstrument()
            val walletDocumentEmptyServicesNullDetailsNoPaymentInstrument =
                walletDocumentEmptyServicesNullDetailsNoPaymentInstrument()

            val expectedLoggedAction =
                LoggedAction(wallet, WalletPatchEvent(WALLET_UUID.value.toString()))

            val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

            given { walletRepository.findByWalletId(any()) }
                .willReturn(Mono.just(walletDocumentEmptyServicesNullDetailsNoPaymentInstrument))

            given { walletRepository.save(walletArgumentCaptor.capture()) }
                .willAnswer { Mono.just(it.arguments[0]) }

            /* test */
            assertTrue(wallet.applications.isEmpty())

            StepVerifier.create(
                    walletService.patchWallet(
                        WALLET_UUID.value,
                        Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                    )
                )
                .expectNext(expectedLoggedAction)
                .verifyComplete()

            val walletDocumentToSave = walletArgumentCaptor.firstValue
            assertEquals(walletDocumentToSave.applications.size, 1)
        }
    }

    @Test
    fun `should throws wallet not found exception`() {
        /* preconditions */

        given { walletRepository.findByWalletId(any()) }.willReturn(Mono.empty())

        /* test */

        StepVerifier.create(
                walletService.patchWallet(
                    WALLET_UUID.value,
                    Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                )
            )
            .expectError(WalletNotFoundException::class.java)
            .verify()
    }
}
