package it.pagopa.wallet.services

import io.mockk.*
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
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()

    private val walletService: WalletService = WalletService(walletRepository)

    @Test
    fun `should save wallet document`() {
        /* preconditions */

        val mockedUUID = UUID.randomUUID()
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val expectedLoggedAction =
                    LoggedAction(
                        WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT,
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                val f = walletService.createWallet(
                    listOf(SERVICE_NAME),
                    USER_ID.id,
                    PAYMENT_METHOD_ID.value,
                    CONTRACT_ID.contractId
                ).block()

                assertEquals(expectedLoggedAction, f)

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
    }

    @Test
    fun `should patch wallet document when adding services`() {
        /* preconditions */

        val mockedUUID = UUID.randomUUID()
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val expectedLoggedAction =
                    LoggedAction(
                        WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT,
                        WalletPatchEvent(WALLET_UUID.value.toString())
                    )

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(mono { WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT })

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                /* test */
                assertTrue(
                    WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT.services.isEmpty()
                )

                val f = walletService.patchWallet(
                    WALLET_UUID.value,
                    Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                ).block()

                assertEquals(expectedLoggedAction, f)

                StepVerifier.create(
                    walletService.patchWallet(
                        WALLET_UUID.value,
                        Pair(SERVICE_NAME, ServiceStatus.ENABLED)
                    )
                )
                    .expectNext(expectedLoggedAction)
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.services.size, 1)

            }
        }
    }

    @Test
    fun `should patch wallet document editing service status`() {
        /* preconditions */

        val mockedUUID = UUID.randomUUID()
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

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
                    .expectNext(expectedLoggedAction)
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.services.size, 1)
                assertEquals(walletDocumentToSave.services[0].name, SERVICE_NAME.name)
                assertEquals(walletDocumentToSave.services[0].status, ServiceStatus.ENABLED.toString())

            }
        }
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
    }
}
