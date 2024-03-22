package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletDetailsAddedEvent
import it.pagopa.wallet.config.WalletMigrationConfig
import it.pagopa.wallet.documents.migration.WalletPaymentManagerDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.migration.WalletPaymentManager
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.exception.MigrationError
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.MongoWalletMigrationRepository
import it.pagopa.wallet.repositories.WalletPaymentManagerRepositoryImpl
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.test.test

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
        given { walletRepository.save(any<Wallet>()) }.willAnswer { Mono.just(it.arguments[0]) }
        given(loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
            Flux.fromIterable(it.arguments[0] as Iterable<*>)
        }
    }

    @Test
    fun `should save a new wallet for Payment Manager Id`() {
        val paymentManagerId = Random().nextLong().toString()
        mockWalletMigration(paymentManagerId) { walletPmDocument, contractId ->
            given { uniqueIdUtils.generateUniqueId() }
                .willAnswer { Mono.just(contractId.contractId) }
            given { mongoWalletMigrationRepository.findByWalletPmId(any()) }
                .willAnswer { Flux.empty<WalletPaymentManagerDocument>() }
            given { mongoWalletMigrationRepository.save(any()) }
                .willAnswer { Mono.just(walletPmDocument) }
            given { walletRepository.save(any<Wallet>()) }.willAnswer { Mono.just(it.arguments[0]) }

            migrationService
                .initializeWalletByPaymentManager(paymentManagerId, USER_ID)
                .test()
                .assertNext {
                    assertEquals(USER_ID, it.userId)
                    assertEquals(contractId, it.contractId)
                    assertEquals(PAYMENT_METHOD_ID_CARDS, it.paymentMethodId)
                    argumentCaptor<Iterable<LoggingEvent>> {
                        verify(loggingEventRepository, times(1)).saveAll(capture())
                        assertInstanceOf(WalletAddedEvent::class.java, lastValue.firstOrNull())
                    }
                }
                .verifyComplete()
        }
    }

    @Test
    fun `should return existing Wallet for an already processed Payment Manager Id`() {
        val paymentManagerId = Random().nextLong().toString()

        mockWalletMigration(paymentManagerId) { walletPmDocument, contractId ->
            given { uniqueIdUtils.generateUniqueId() }
                .willAnswer { Mono.just(contractId.contractId) }
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

            migrationService
                .initializeWalletByPaymentManager(paymentManagerId, USER_ID)
                .test()
                .assertNext {
                    assertEquals(USER_ID, it.userId)
                    assertEquals(contractId, it.contractId)
                    assertEquals(PAYMENT_METHOD_ID_CARDS, it.paymentMethodId)
                    verify(loggingEventRepository, times(0)).saveAll(any<Iterable<LoggingEvent>>())
                }
                .verifyComplete()
        }
    }

    @Test
    fun `should update card details for existing Wallet migration`() {
        val paymentManagerId = Random().nextLong().toString()
        val cardDetails = generateCardDetails()
        mockWalletMigration(paymentManagerId) { walletPmDocument, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willAnswer { Flux.just(walletPmDocument) }
            given { walletRepository.findById(any<String>()) }
                .willAnswer {
                    Mono.just(walletPmDocument.createWalletTest(USER_ID, WalletStatusDto.CREATED))
                }
            given { walletRepository.save(any<Wallet>()) }.willAnswer { Mono.just(it.arguments[0]) }

            migrationService
                .updateWalletCardDetails(contractId = contractId, cardDetails = cardDetails)
                .test()
                .assertNext { wallet ->
                    assertEquals(WalletStatusDto.VALIDATED, wallet.status)
                    assertEquals(wallet.contractId, contractId)
                    assertInstanceOf(CardDetails::class.java, wallet.details).let {
                        assertEquals(cardDetails.bin, it.bin)
                        assertEquals(cardDetails.brand, it.brand)
                        assertEquals(cardDetails.lastFourDigits, it.lastFourDigits)
                        assertEquals(cardDetails.expiryDate, it.expiryDate)
                        assertEquals(
                            cardDetails.paymentInstrumentGatewayId,
                            it.paymentInstrumentGatewayId
                        )
                    }
                    argumentCaptor<Iterable<LoggingEvent>> {
                        verify(loggingEventRepository, times(1)).saveAll(capture())
                        assertInstanceOf(
                            WalletDetailsAddedEvent::class.java,
                            lastValue.firstOrNull()
                        )
                    }
                }
                .verifyComplete()
        }
    }

    @Test
    fun `should return existing Wallet when retry to updating details of a validated Wallet`() {
        val paymentManagerId = Random().nextLong().toString()
        val cardDetails = generateCardDetails()

        mockWalletMigration(paymentManagerId) { walletPmDocument, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willAnswer { Flux.just(walletPmDocument) }
            given { walletRepository.findById(any<String>()) }
                .willAnswer {
                    walletPmDocument
                        .createWalletTest(USER_ID, WalletStatusDto.VALIDATED)
                        .copy(details = cardDetails.toDocument())
                        .toMono()
                }

            migrationService.updateWalletCardDetails(contractId, cardDetails).test().assertNext {
                wallet ->
                assertEquals(WalletStatusDto.VALIDATED, wallet.status)
                assertEquals(wallet.contractId, contractId)
            }

            verify(loggingEventRepository, times(0)).saveAll(any<Iterable<LoggingEvent>>())
            verify(walletRepository, times(0)).save(any())
        }
    }

    @Test
    fun `should throw not found when update details for non existing contract id`() {
        val contractId = ContractId(UUID.randomUUID().toString())
        given { mongoWalletMigrationRepository.findByContractId(any()) }
            .willAnswer { Flux.empty<WalletPaymentManagerDocument>() }
        migrationService
            .updateWalletCardDetails(contractId, generateCardDetails())
            .test()
            .expectErrorMatches {
                it is MigrationError.WalletContractIdNotFound && it.contractId == contractId
            }
            .verify()
        verify(loggingEventRepository, times(0)).saveAll(any<Iterable<LoggingEvent>>())
    }

    @Test
    fun `should throw invalid state transition when update details for Wallet Error state`() {
        val paymentManagerId = Random().nextLong().toString()
        val cardDetails = generateCardDetails()
        mockWalletMigration(paymentManagerId) { migrationDocument, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willAnswer { Flux.just(migrationDocument) }
            given { walletRepository.findById(any<String>()) }
                .willAnswer {
                    Mono.just(migrationDocument.createWalletTest(USER_ID, WalletStatusDto.ERROR))
                }
            given { walletRepository.save(any<Wallet>()) }.willAnswer { Mono.just(it.arguments[0]) }

            migrationService
                .updateWalletCardDetails(contractId = contractId, cardDetails = cardDetails)
                .test()
                .expectError(MigrationError.WalletIllegalStateTransition::class.java)
                .verify()

            verify(walletRepository, times(0)).save(any())
        }
    }

    @Test
    fun `should throw invalid state transition when updating a validate Wallet with different details`() {
        val paymentManagerId = Random().nextLong().toString()
        val cardDetails = generateCardDetails()

        mockWalletMigration(paymentManagerId) { walletPmDocument, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willAnswer { Flux.just(walletPmDocument) }
            given { walletRepository.findById(any<String>()) }
                .willAnswer {
                    walletPmDocument
                        .createWalletTest(USER_ID, WalletStatusDto.VALIDATED)
                        .copy(details = cardDetails.toDocument())
                        .toMono()
                }

            val differentCardDetails = cardDetails.copy(bin = Bin("456789"))
            migrationService
                .updateWalletCardDetails(contractId, differentCardDetails)
                .test()
                .expectError(MigrationError.WalletIllegalStateTransition::class.java)
                .verify()

            verify(walletRepository, times(0)).save(any())
        }
    }

    @Test
    fun `should thrown ContractIdNotFound when ContractId doesn't exists`() {
        mockWalletMigration { _, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willAnswer { Flux.empty<WalletPaymentManager>() }

            migrationService
                .deleteWallet(contractId)
                .test()
                .expectErrorMatches {
                    it is MigrationError.WalletContractIdNotFound && it.contractId == contractId
                }
                .verify()
        }
    }

    @Test
    fun `should thrown ContractIdNotFound when ContractId exist but associated Wallet doesn't`() {
        mockWalletMigration { walletPmDocument, contractId ->
            given { mongoWalletMigrationRepository.findByContractId(any()) }
                .willReturn(Flux.just(walletPmDocument))
            given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())

            migrationService
                .deleteWallet(contractId)
                .test()
                .expectErrorMatches {
                    it is MigrationError.WalletContractIdNotFound && it.contractId == contractId
                }
                .verify()
        }
    }

    companion object {

        private fun mockWalletMigration(
            paymentManagerId: String? = null,
            receiver: (WalletPaymentManagerDocument, ContractId) -> Unit
        ) =
            ContractId(UUID.randomUUID().toString()).let {
                receiver(
                    generateWalletPaymentManagerDocument(
                        paymentManagerId ?: Random().nextLong().toString(),
                        it
                    ),
                    it
                )
            }

        private fun generateWalletPaymentManagerDocument(
            paymentManagerId: String,
            contractId: ContractId
        ) =
            WalletPaymentManagerDocument(
                walletPmId = paymentManagerId,
                walletId = UUID.randomUUID().toString(),
                contractId = contractId.contractId,
                creationDate = Instant.now()
            )

        private fun generateCardDetails() =
            CardDetails(
                bin = Bin("123456"),
                lastFourDigits = LastFourDigits("7890"),
                expiryDate = ExpiryDate("202212"),
                brand = WalletCardDetailsDto.BrandEnum.VISA,
                paymentInstrumentGatewayId = PaymentInstrumentGatewayId("123")
            )

        private fun WalletPaymentManagerDocument.createWalletTest(
            userId: UserId,
            status: WalletStatusDto
        ) =
            Wallet(
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
