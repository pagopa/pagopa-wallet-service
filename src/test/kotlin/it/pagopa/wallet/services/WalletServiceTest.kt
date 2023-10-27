package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_DOCUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.WalletTestUtils.getValidCardsPaymentMethod
import it.pagopa.wallet.WalletTestUtils.initializedWalletDomainEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.WalletTestUtils.walletDocumentEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.WalletTestUtils.walletDocumentWithSessionWallet
import it.pagopa.wallet.WalletTestUtils.walletDomainEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.SessionWalletAddedEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.WalletRepository
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.Map
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()
    private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient = mock()
    private val npgClient: NpgClient = mock()
    private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper = mock()
    private val sessionUrlConfig =
        SessionUrlConfig(
            "http://localhost:1234",
            "/esito",
            "/annulla",
            "https://localhost/sessions/{orderId}/outcomes?paymentMethodId={paymentMethodId}"
        )

    private val walletService: WalletService =
        WalletService(
            walletRepository,
            ecommercePaymentMethodsClient,
            npgClient,
            npgSessionRedisTemplate,
            sessionUrlConfig
        )

    private val mockedUUID = UUID.randomUUID()
    private val mockedInstant = Instant.now()

    @Test
    fun `should save wallet document`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val expectedLoggedAction =
                    LoggedAction(
                        initializedWalletDomainEmptyServicesNullDetailsNoPaymentInstrument(),
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }.willAnswer { Mono.just(it.arguments[0]) }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWallet(
                            listOf(SERVICE_NAME),
                            USER_ID.id,
                            PAYMENT_METHOD_ID.value
                        )
                    )
                    .expectNext(expectedLoggedAction)
                    .verifyComplete()
            }
        }
    }

    @Test
    fun `should create wallet session`() {
        /* preconditions */
        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            val orderId = UUID.randomUUID().toString().replace("-", "").substring(0, 15)
            val customerId = UUID.randomUUID().toString().replace("-", "").substring(0, 15)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val sessionId = UUID.randomUUID().toString()
                val nggFields = Fields().sessionId(sessionId)
                nggFields.fields.addAll(
                    listOf(
                        Field()
                            .id(UUID.randomUUID().toString())
                            .src("https://test.it/h")
                            .propertyClass("holder")
                            .propertyClass("h"),
                        Field()
                            .id(UUID.randomUUID().toString())
                            .src("https://test.it/p")
                            .propertyClass("pan")
                            .propertyClass("p"),
                        Field()
                            .id(UUID.randomUUID().toString())
                            .src("https://test.it/c")
                            .propertyClass("cvv")
                            .propertyClass("c")
                    )
                )
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()
                val walletDocumentEmptyServicesNullDetailsNoPaymentInstrument =
                    walletDocumentEmptyServicesNullDetailsNoPaymentInstrument()

                val expectedLoggedAction =
                    LoggedAction(
                        walletDocumentWithSessionWallet.toDomain(),
                        SessionWalletAddedEvent(WALLET_UUID.value.toString())
                    )

                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                        .build(
                            Map.of(
                                "orderId",
                                orderId,
                                "paymentMethodId",
                                walletDocumentEmptyServicesNullDetailsNoPaymentInstrument
                                    .paymentMethodId
                            )
                        )

                val npgCorrelationId = mockedUUID
                val npgCreateHostedOrderRequest =
                    CreateHostedOrderRequest()
                        .version(WalletService.CREATE_HOSTED_ORDER_REQUEST_VERSION)
                        .merchantUrl(merchantUrl)
                        .order(
                            Order()
                                .orderId(orderId)
                                .amount(WalletService.CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                .currency(WalletService.CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                                .customerId(customerId)
                        )
                        .paymentSession(
                            PaymentSession()
                                .actionType(ActionType.VERIFY)
                                .recurrence(
                                    RecurringSettings()
                                        .action(RecurringAction.CONTRACT_CREATION)
                                        .contractId(
                                            WalletService.CREATE_HOSTED_ORDER_REQUEST_CONTRACT_ID
                                        )
                                        .contractType(RecurringContractType.CIT)
                                )
                                .amount(WalletService.CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                .language(WalletService.CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                                .captureType(CaptureType.IMPLICIT)
                                .paymentService("CARDS")
                                .resultUrl(resultUrl.toString())
                                .cancelUrl(cancelUrl.toString())
                                .notificationUrl(notificationUrl.toString())
                        )

                given {
                        npgClient.createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                    }
                    .willAnswer { mono { nggFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(
                        Mono.just(walletDocumentEmptyServicesNullDetailsNoPaymentInstrument)
                    )

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }

                /* test */
                Hooks.onOperatorDebug()
                StepVerifier.create(walletService.createSessionWallet(WALLET_UUID.value))
                    .expectNext(Pair(nggFields, expectedLoggedAction))
                    .verifyComplete()
            }
        }
    }

    @Test
    fun `should find wallet document`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val wallet = WALLET_DOCUMENT
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.name))
                                    .status(ServiceStatusDto.valueOf(application.status))
                            }
                        )
                        .details(
                            WalletCardDetailsDto()
                                .type((wallet.details as CardDetails).type)
                                .bin((wallet.details as CardDetails).bin)
                                .holder((wallet.details as CardDetails).holder)
                                .expiryDate((wallet.details as CardDetails).expiryDate)
                                .maskedPan((wallet.details as CardDetails).maskedPan)
                        )

                given { walletRepository.findById(any<String>()) }.willAnswer { Mono.just(wallet) }

                /* test */

                StepVerifier.create(walletService.findWallet(WALLET_UUID.value))
                    .expectNext(walletInfoDto)
                    .verifyComplete()
            }
        }
    }

    @Test
    fun `should find wallet document by userId`() {
        /* preconditions */

        val mockedUUID = USER_ID.id
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val wallet = WALLET_DOCUMENT
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.name))
                                    .status(ServiceStatusDto.valueOf(application.status))
                            }
                        )
                        .details(
                            WalletCardDetailsDto()
                                .type((wallet.details as CardDetails).type)
                                .bin((wallet.details as CardDetails).bin)
                                .holder((wallet.details as CardDetails).holder)
                                .expiryDate((wallet.details as CardDetails).expiryDate)
                                .maskedPan((wallet.details as CardDetails).maskedPan)
                        )

                val walletsDto = WalletsDto().addWalletsItem(walletInfoDto)

                given { walletRepository.findByUserId(USER_ID.id.toString()) }
                    .willAnswer { Flux.fromIterable(listOf(wallet)) }

                /* test */

                StepVerifier.create(walletService.findWalletByUserId(USER_ID.id))
                    .expectNext(walletsDto)
                    .verifyComplete()
            }
        }
    }

    @Test
    fun `should patch wallet document when adding services`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                print("Mocked instant: ${Instant.now()} $mockedInstant")

                val wallet = walletDomainEmptyServicesNullDetailsNoPaymentInstrument()
                val walletDocumentEmptyServicesNullDetailsNoPaymentInstrument =
                    walletDocumentEmptyServicesNullDetailsNoPaymentInstrument()

                val expectedLoggedAction =
                    LoggedAction(wallet, WalletPatchEvent(WALLET_UUID.value.toString()))

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(
                        Mono.just(walletDocumentEmptyServicesNullDetailsNoPaymentInstrument)
                    )

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
    }

    @Test
    fun `should patch wallet document editing service status`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val expectedLoggedAction =
                    LoggedAction(WALLET_DOMAIN, WalletPatchEvent(WALLET_UUID.value.toString()))

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(WALLET_DOCUMENT))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willReturn(Mono.just(WALLET_DOCUMENT))

                /* test */
                assertEquals(WALLET_DOCUMENT.applications.size, 1)
                assertEquals(WALLET_DOCUMENT.applications[0].name, SERVICE_NAME.name)
                assertEquals(
                    WALLET_DOCUMENT.applications[0].status,
                    ServiceStatus.DISABLED.toString()
                )

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
                assertEquals(walletDocumentToSave.applications[0].name, SERVICE_NAME.name)
                assertEquals(
                    walletDocumentToSave.applications[0].status,
                    ServiceStatus.ENABLED.toString()
                )
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
