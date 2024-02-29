package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.config.WalletMigrationConfig
import it.pagopa.wallet.documents.migration.WalletPaymentManagerDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.MongoWalletMigrationRepository
import it.pagopa.wallet.repositories.WalletPaymentManagerRepositoryImpl
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.time.Instant
import java.util.Random
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class MigrationServiceTest {
    private val loggingEventRepository: LoggingEventRepository = mock()
    private val walletRepository: WalletRepository = mock()
    private val mongoWalletMigrationRepository: MongoWalletMigrationRepository = mock()
    private val uniqueIdUtils: UniqueIdUtils = mock()

    private val migrationService =
        MigrationService(
            WalletPaymentManagerRepositoryImpl(mongoWalletMigrationRepository),
            walletRepository,
            loggingEventRepository,
            uniqueIdUtils,
            WalletMigrationConfig(PAYMENT_METHOD_ID_CARDS.value.toString()),
        )

    @BeforeEach
    fun setupTest() {
        given(loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
            Flux.fromIterable(it.arguments[0] as Iterable<*>)
        }
    }

    @Test
    fun `should save a new wallet for Payment Manager Id`() {
        val contractId = UUID.randomUUID().toString()
        val paymentManagerId = Random().nextLong().toString()
        val walletPmDocument =
            generateWalletPaymentManagerDocument(paymentManagerId, ContractId(contractId))

        given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(contractId) }
        given { mongoWalletMigrationRepository.findByWalletPmId(any()) }
            .willAnswer { Flux.empty<WalletPaymentManagerDocument>() }
        given { mongoWalletMigrationRepository.save(any()) }
            .willAnswer { Mono.just(walletPmDocument) }
        given { walletRepository.save(any<Wallet>()) }.willAnswer { Mono.just(it.arguments[0]) }

        StepVerifier.create(
                migrationService.initializeWalletByPaymentManager(paymentManagerId, USER_ID)
            )
            .assertNext {
                assertEquals(USER_ID, it.userId)
                assertEquals(contractId, it.contractId!!.contractId)
                assertEquals(PAYMENT_METHOD_ID_CARDS, it.paymentMethodId)
                argumentCaptor<Iterable<LoggingEvent>> {
                    verify(loggingEventRepository, times(1)).saveAll(capture())
                    assertInstanceOf(WalletAddedEvent::class.java, lastValue.firstOrNull())
                }
            }
            .verifyComplete()
    }

    @Test
    fun `should return existing Wallet for an already processed Payment Manager Id`() {
        val contractId = UUID.randomUUID().toString()
        val paymentManagerId = Random().nextLong().toString()
        val walletPmDocument =
            generateWalletPaymentManagerDocument(paymentManagerId, ContractId(contractId))

        given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(contractId) }
        given { mongoWalletMigrationRepository.save(any()) }
            .willAnswer { Mono.just(walletPmDocument) }
        given { mongoWalletMigrationRepository.findByWalletPmId(any()) }
            .willAnswer { Flux.just(walletPmDocument) }
        given { walletRepository.save(any<Wallet>()) }
            .willAnswer { Mono.error<Wallet>(DuplicateKeyException("Duplicate test")) }
        given { walletRepository.findById(any<String>()) }
            .willAnswer {
                Mono.just(walletPmDocument.createWalletTest(USER_ID, WalletStatusDto.CREATED))
            }

        StepVerifier.create(
                migrationService.initializeWalletByPaymentManager(paymentManagerId, USER_ID)
            )
            .assertNext {
                assertEquals(USER_ID, it.userId)
                assertEquals(contractId, it.contractId!!.contractId)
                assertEquals(PAYMENT_METHOD_ID_CARDS, it.paymentMethodId)
                verify(loggingEventRepository, times(0)).saveAll(any<Iterable<LoggingEvent>>())
            }
            .verifyComplete()
    }

    companion object {
        private fun generateWalletPaymentManagerDocument(
            paymentManagerId: String,
            contractId: ContractId
        ): WalletPaymentManagerDocument {
            return WalletPaymentManagerDocument(
                paymentManagerId,
                walletId = UUID.randomUUID().toString(),
                contractId = contractId.contractId,
                Instant.now()
            )
        }

        private fun WalletPaymentManagerDocument.createWalletTest(
            userId: UserId,
            status: WalletStatusDto
        ): Wallet {
            return Wallet(
                id = walletId,
                contractId = contractId,
                userId = userId.id.toString(),
                status = status.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                creationDate = Instant.now(),
                updateDate = Instant.now(),
                applications = emptyList(),
                details = null,
                validationOperationResult = null,
                validationErrorCode = null,
                version = 0
            )
        }
    }
}
