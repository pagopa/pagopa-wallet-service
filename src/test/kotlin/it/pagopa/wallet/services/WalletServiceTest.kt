package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT
import it.pagopa.wallet.WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
import it.pagopa.wallet.WalletTestUtils.ORDER_ID
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.WalletTestUtils.getUniqueId
import it.pagopa.wallet.WalletTestUtils.getValidAPMPaymentMethod
import it.pagopa.wallet.WalletTestUtils.getValidCardsPaymentMethod
import it.pagopa.wallet.WalletTestUtils.newWalletDocumentSaved
import it.pagopa.wallet.WalletTestUtils.walletDocument
import it.pagopa.wallet.WalletTestUtils.walletDocumentEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.WalletTestUtils.walletDocumentValidated
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithAPM
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.WalletTestUtils.walletDocumentWithError
import it.pagopa.wallet.WalletTestUtils.walletDocumentWithSessionWallet
import it.pagopa.wallet.WalletTestUtils.walletDomain
import it.pagopa.wallet.WalletTestUtils.walletDomainEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.stream.Stream
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()
    private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient = mock()
    private val npgClient: NpgClient = mock()
    private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper = mock()
    private val uniqueIdUtils: UniqueIdUtils = mock()
    private val sessionUrlConfig =
        SessionUrlConfig(
            "http://localhost:1234",
            "/esito",
            "/annulla",
            "http://localhost/payment-wallet-notifications/v1/wallets/{walletId}/sessions/{orderId}"
        )

    companion object {
        @JvmStatic
        private fun operationResultAuthError() =
            Stream.of(
                Arguments.of(OperationResult.THREEDS_VALIDATED),
                Arguments.of(OperationResult.DENIED_BY_RISK),
                Arguments.of(OperationResult.THREEDS_FAILED),
                Arguments.of(OperationResult.DECLINED)
            )
    }

    private val walletService: WalletService =
        WalletService(
            walletRepository,
            ecommercePaymentMethodsClient,
            npgClient,
            npgSessionRedisTemplate,
            sessionUrlConfig,
            uniqueIdUtils
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
                        newWalletDocumentSaved().toDomain(),
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }
                    .willAnswer { Mono.just(newWalletDocumentSaved()) }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWallet(
                            listOf(SERVICE_NAME),
                            USER_ID.id,
                            PAYMENT_METHOD_ID_CARDS.value
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
            val uniqueId = getUniqueId()
            val orderId = uniqueId
            val contractId = uniqueId

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val sessionId = UUID.randomUUID().toString()
                val npgFields = Fields().sessionId(sessionId)
                npgFields.fields.addAll(
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
                val sessionResponseDto =
                    SessionWalletCreateResponseDto()
                        .orderId(orderId)
                        .cardFormFields(
                            listOf(
                                FieldDto()
                                    .id(UUID.randomUUID().toString())
                                    .src(URI.create("https://test.it/h"))
                                    .propertyClass("holder")
                                    .propertyClass("h"),
                                FieldDto()
                                    .id(UUID.randomUUID().toString())
                                    .src(URI.create("https://test.it/p"))
                                    .propertyClass("pan")
                                    .propertyClass("p"),
                                FieldDto()
                                    .id(UUID.randomUUID().toString())
                                    .src(URI.create("https://test.it/c"))
                                    .propertyClass("cvv")
                                    .propertyClass("c"),
                            )
                        )
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                var walletDocumentWithSessionWallet =
                    walletDocumentWithSessionWallet(ContractId(contractId))

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
                            mapOf(
                                Pair("walletId", walletDocumentWithSessionWallet.id),
                                Pair("orderId", orderId),
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
                        )
                        .paymentSession(
                            PaymentSession()
                                .actionType(ActionType.VERIFY)
                                .recurrence(
                                    RecurringSettings()
                                        .action(RecurringAction.CONTRACT_CREATION)
                                        .contractId(contractId)
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
                    .willAnswer { mono { npgFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(
                        Mono.just(walletDocumentEmptyServicesNullDetailsNoPaymentInstrument)
                    )

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(walletService.createSessionWallet(WALLET_UUID.value))
                    .expectNext(Pair(sessionResponseDto, expectedLoggedAction))
                    .verifyComplete()
            }
        }
    }

    @Test
    fun `should validate wallet with card data`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"
                val npgGetCardDataResponse =
                    CardDataResponse()
                        .bin("12345678")
                        .expiringDate("12/30")
                        .lastFourDigits("0000")
                        .circuit("MASTERCARD")

                val npgStateResponse =
                    StateResponse()
                        .state(State.GDI_VERIFICATION)
                        .fieldSet(
                            Fields()
                                .sessionId(sessionId)
                                .addFieldsItem(Field().src("http://src.state.url"))
                        )

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())
                val verifyResponse =
                    WalletVerifyRequestsResponseDto()
                        .orderId(orderId)
                        .details(
                            WalletVerifyRequestCardDetailsDto()
                                .type("CARD")
                                .iframeUrl(
                                    Base64.getUrlEncoder()
                                        .encodeToString(
                                            npgStateResponse.fieldSet!!
                                                .fields[0]
                                                .src!!
                                                .toByteArray(StandardCharsets.UTF_8)
                                        )
                                )
                        )

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                val walletDocumentWithCardDetails =
                    walletDocumentVerifiedWithCardDetails(
                        "12345678",
                        "0000",
                        "12/30",
                        "?",
                        WalletCardDetailsDto.BrandEnum.MASTERCARD
                    )

                val expectedLoggedAction =
                    LoggedAction(
                        walletDocumentWithCardDetails.toDomain(),
                        WalletDetailsAddedEvent(WALLET_UUID.value.toString())
                    )

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer {
                        mono { PaymentMethodResponse().name("CARDS").paymentTypeCode("CP") }
                    }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectNext(Pair(verifyResponse, expectedLoggedAction))
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(
                    walletDocumentToSave.details,
                    CardDetails("CARDS", "12345678", "12345678****0000", "12/30", "MASTERCARD", "?")
                )
            }
        }
    }

    @Test
    fun `should throw error when validate wallet with APM`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"

                val npgStateResponse =
                    StateResponse()
                        .state(State.REDIRECTED_TO_EXTERNAL_DOMAIN)
                        .url("http://state.url")

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { mono { getValidAPMPaymentMethod() } }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(NoCardsSessionValidateRequestException::class.java)
                    .verify()
            }
        }
    }

    @Test
    fun `validate should throws SessionNotFoundException`() {
        /* preconditions */
        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"

                given { npgSessionRedisTemplate.findById(orderId.toString()) }.willAnswer { null }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(SessionNotFoundException::class.java)
                    .verify()
            }
        }
    }

    @Test
    fun `validate should throws WalletNotFoundException`() {
        /* preconditions */
        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"

                val npgSession =
                    NpgSession(orderId.toString(), sessionId, "token", WALLET_UUID.value.toString())

                given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())

                given { npgSessionRedisTemplate.findById(orderId.toString()) }
                    .willAnswer { npgSession }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletNotFoundException::class.java)
                    .verify()
            }
        }
    }

    @Test
    fun `validate should throws WalletSessionMismatchException`() {
        /* preconditions */
        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"

                val npgSession =
                    NpgSession(orderId.toString(), sessionId, "token", "testWalletIdWrong")

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(mono { walletDocumentWithSessionWallet })

                given { npgSessionRedisTemplate.findById(orderId.toString()) }
                    .willAnswer { npgSession }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletSessionMismatchException::class.java)
                    .verify()
            }
        }
    }

    @Test
    fun `validate should throws WalletConflictStatusException`() {
        /* preconditions */
        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"

                val npgSession = NpgSession(orderId, sessionId, "token", walletDocument().id)

                given { walletRepository.findById(any<String>()) }
                    .willReturn(mono { walletDocument() })

                given { npgSessionRedisTemplate.findById(orderId.toString()) }
                    .willAnswer { npgSession }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletConflictStatusException::class.java)
                    .verify()
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by wrong state`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"
                val npgGetCardDataResponse =
                    CardDataResponse()
                        .bin("123456")
                        .expiringDate("122030")
                        .lastFourDigits("0000")
                        .circuit("MASTERCARD")
                val npgStateResponse =
                    StateResponse().state(State.READY_FOR_PAYMENT).url("http://state.url")

                val npgSession =
                    NpgSession(orderId.toString(), sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer {
                        mono { PaymentMethodResponse().name("CARDS").paymentTypeCode("CP") }
                    }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by fields null`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"
                val npgGetCardDataResponse =
                    CardDataResponse()
                        .bin("123456")
                        .expiringDate("122030")
                        .lastFourDigits("0000")
                        .circuit("MASTERCARD")
                val npgStateResponse = StateResponse().state(State.GDI_VERIFICATION)

                val npgSession =
                    NpgSession(orderId.toString(), sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer {
                        mono { PaymentMethodResponse().name("CARDS").paymentTypeCode("CP") }
                    }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by fields list empty`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = "orderId"
                val npgGetCardDataResponse =
                    CardDataResponse()
                        .bin("123456")
                        .expiringDate("122030")
                        .lastFourDigits("0000")
                        .circuit("MASTERCARD")
                val npgStateResponse =
                    StateResponse()
                        .state(State.GDI_VERIFICATION)
                        .fieldSet(Fields().sessionId(sessionId))

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer {
                        mono { PaymentMethodResponse().name("CARDS").paymentTypeCode("CP") }
                    }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by first field src null`() {
        /* preconditions */

        val mockedUUID = WALLET_UUID.value
        val mockedInstant = Instant.now()

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"
                val npgGetCardDataResponse =
                    CardDataResponse()
                        .bin("123456")
                        .expiringDate("122030")
                        .lastFourDigits("0000")
                        .circuit("MASTERCARD")
                val npgStateResponse =
                    StateResponse()
                        .state(State.GDI_VERIFICATION)
                        .fieldSet(Fields().sessionId(sessionId).addFieldsItem(Field().id("field")))

                val npgSession =
                    NpgSession(orderId.toString(), sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentWithSessionWallet = walletDocumentWithSessionWallet()

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given {
                        npgClient.confirmPayment(
                            ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                            npgCorrelationId
                        )
                    }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentWithSessionWallet))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer {
                        mono { PaymentMethodResponse().name("CARDS").paymentTypeCode("CP") }
                    }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)
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

                val wallet = walletDocument()
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
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

                val wallet = walletDocument()
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.updateDate.toString()))
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
    fun `should find wallet auth data by ID`() {
        /* preconditions */

        val wallet = walletDocument()
        val walletAuthDataDto = WalletTestUtils.walletAuthDataDto()

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.just(wallet))

        /* test */

        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .expectNext(walletAuthDataDto)
            .verifyComplete()
    }

    @Test
    fun `should throws wallet not found exception when retrieve auth data by ID`() {
        /* preconditions */
        val wallet = walletDocument()

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.empty())
        /* test */

        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .expectError(WalletNotFoundException::class.java)
            .verify()
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
                    LoggedAction(walletDomain(), WalletPatchEvent(WALLET_UUID.value.toString()))

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()
                val walletDocument = walletDocument()
                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocument))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willReturn(Mono.just(walletDocument))

                /* test */
                assertEquals(walletDocument.applications.size, 1)
                assertEquals(walletDocument.applications[0].name, SERVICE_NAME.name)
                assertEquals(
                    walletDocument.applications[0].status,
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

    @Test
    fun `notify wallet should throws wallet not found exception`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"

        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(eq(orderId)) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())
        /* test */

        StepVerifier.create(
                walletService.notifyWallet(
                    WALLET_UUID,
                    orderId,
                    sessionToken,
                    NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
                )
            )
            .expectError(WalletNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `notify wallet should throws session not found exception`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionToken = "token"

        given { npgSessionRedisTemplate.findById(any()) }.willReturn(null)
        /* test */

        StepVerifier.create(
                walletService.notifyWallet(
                    WALLET_UUID,
                    orderId,
                    sessionToken,
                    NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
                )
            )
            .expectError(SessionNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `notify wallet should throws wallet id mismatch exception`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val sessionWalletId = UUID.randomUUID().toString()

        val npgSession = NpgSession(orderId, sessionId, sessionToken, sessionWalletId)
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument()))
        /* test */

        StepVerifier.create(
                walletService.notifyWallet(
                    WALLET_UUID,
                    orderId,
                    sessionToken,
                    NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
                )
            )
            .expectError(WalletSessionMismatchException::class.java)
            .verify()
    }

    @Test
    fun `notify wallet should throws wallet conflict status exception`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"

        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument()))
        /* test */

        StepVerifier.create(
                walletService.notifyWallet(
                    WALLET_UUID,
                    orderId,
                    sessionToken,
                    NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
                )
            )
            .expectError(WalletConflictStatusException::class.java)
            .verify()
    }

    @Test
    fun `notify wallet should set wallet status to ERROR`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument = walletDocumentVerifiedWithAPM()
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))
        val walletDocumentWithError = walletDocumentWithError(notifyRequestDto.operationResult)

        given { walletRepository.save(any()) }.willReturn(Mono.just(walletDocumentWithError))

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentWithError.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.DECLINED.value,
                    notifyRequestDto.timestampOperation.toString()
                )
            )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .expectNext(expectedLoggedAction)
            .verifyComplete()
    }

    @Test
    fun `notify wallet should set wallet status to VALIDATED`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument = walletDocumentVerifiedWithAPM()
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))

        val walletDocumentValidated = walletDocumentValidated(notifyRequestDto.operationResult)

        given { walletRepository.save(any()) }.willReturn(Mono.just(walletDocumentValidated))

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentValidated.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.EXECUTED.value,
                    notifyRequestDto.timestampOperation.toString()
                )
            )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .expectNext(expectedLoggedAction)
            .verifyComplete()
    }

    @Test
    fun `find session should throws session not found exception`() {
        /* preconditions */
        given { npgSessionRedisTemplate.findById(any()) }.willReturn(null)
        /* test */

        StepVerifier.create(walletService.findSessionWallet(USER_ID.id, WALLET_UUID, ORDER_ID))
            .expectError(SessionNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `find session should throws wallet not found exception`() {
        /* preconditions */
        val userId = USER_ID.id
        val walletId = WALLET_UUID.value
        val sessionId = "sessionId"
        val sessionToken = "token"

        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(eq(ORDER_ID)) }.willReturn(npgSession)
        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.empty())
        /* test */

        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectError(WalletNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `find session should throws wallet id mismatch exception`() {
        /* preconditions */
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val sessionWalletId = UUID.randomUUID().toString()

        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, sessionWalletId)
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        given { walletRepository.findByIdAndUserId(any(), any()) }
            .willReturn(Mono.just(walletDocument()))
        /* test */

        StepVerifier.create(walletService.findSessionWallet(userId, WALLET_UUID, ORDER_ID))
            .expectError(WalletSessionMismatchException::class.java)
            .verify()
    }

    @Test
    fun `find session should throws wallet conflict status exception`() {
        /* preconditions */
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"

        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        given { walletRepository.findByIdAndUserId(any(), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocument()))
        /* test */

        StepVerifier.create(walletService.findSessionWallet(userId, WALLET_UUID, ORDER_ID))
            .expectError(WalletConflictStatusException::class.java)
            .verify()
    }

    @Test
    fun `find session should return response with final status and outcome 0`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val walletDocument = walletDocumentValidated()
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocument))

        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .orderId(ORDER_ID)
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_0)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @Test
    fun `find session should return response with final status false and outcome null`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val walletDocument = walletDocumentVerifiedWithAPM()
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocument))

        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(false)
                .walletId(walletId.toString())
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @Test
    fun `find session should return response with final status true and outcome 8 CANCELED_BY_USER`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        val walletDocumentWithError =
            walletDocumentWithError(WalletNotificationRequestDto.OperationResultEnum.CANCELED)

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_8)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @Test
    fun `find session should return response with final status true and outcome 4 PENDING`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        val walletDocumentWithError =
            walletDocumentWithError(WalletNotificationRequestDto.OperationResultEnum.PENDING)

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_4)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @Test
    fun `find session should return response with final status true and outcome 1 GENERIC_ERROR`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        val walletDocumentWithError =
            walletDocumentWithError(WalletNotificationRequestDto.OperationResultEnum.VOIDED)

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("operationResultAuthError")
    fun `find session should return response with final status true and outcome 1 GENERIC_ERROR`(
        operationResult: OperationResult
    ) {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        val walletDocumentWithError =
            walletDocumentWithError(
                WalletNotificationRequestDto.OperationResultEnum.valueOf(operationResult.value)
            )

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }
}
