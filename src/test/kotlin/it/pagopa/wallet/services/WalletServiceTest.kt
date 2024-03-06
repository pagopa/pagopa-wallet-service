package it.pagopa.wallet.services

import io.vavr.control.Either
import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.AMOUNT
import it.pagopa.wallet.WalletTestUtils.APM_SESSION_CREATE_REQUEST
import it.pagopa.wallet.WalletTestUtils.APPLICATION_DESCRIPTION
import it.pagopa.wallet.WalletTestUtils.APPLICATION_DOCUMENT
import it.pagopa.wallet.WalletTestUtils.APPLICATION_ID
import it.pagopa.wallet.WalletTestUtils.APPLICATION_METADATA
import it.pagopa.wallet.WalletTestUtils.MASKED_EMAIL
import it.pagopa.wallet.WalletTestUtils.NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT
import it.pagopa.wallet.WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
import it.pagopa.wallet.WalletTestUtils.NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
import it.pagopa.wallet.WalletTestUtils.ORDER_ID
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_APM
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.PSP_ID
import it.pagopa.wallet.WalletTestUtils.TIMESTAMP
import it.pagopa.wallet.WalletTestUtils.TRANSACTION_ID
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_APPLICATION_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.WalletTestUtils.creationDate
import it.pagopa.wallet.WalletTestUtils.getUniqueId
import it.pagopa.wallet.WalletTestUtils.getValidAPMPaymentMethod
import it.pagopa.wallet.WalletTestUtils.getValidCardsPaymentMethod
import it.pagopa.wallet.WalletTestUtils.newWalletDocumentForPaymentWithContextualOnboardToBeSaved
import it.pagopa.wallet.WalletTestUtils.newWalletDocumentToBeSaved
import it.pagopa.wallet.WalletTestUtils.walletDocument
import it.pagopa.wallet.WalletTestUtils.walletDocumentCreatedStatus
import it.pagopa.wallet.WalletTestUtils.walletDocumentCreatedStatusForTransactionWithContextualOnboard
import it.pagopa.wallet.WalletTestUtils.walletDocumentEmptyCreatedStatus
import it.pagopa.wallet.WalletTestUtils.walletDocumentInitializedStatus
import it.pagopa.wallet.WalletTestUtils.walletDocumentInitializedStatusForTransactionWithContextualOnboard
import it.pagopa.wallet.WalletTestUtils.walletDocumentStatusValidatedAPM
import it.pagopa.wallet.WalletTestUtils.walletDocumentStatusValidatedCARD
import it.pagopa.wallet.WalletTestUtils.walletDocumentValidated
import it.pagopa.wallet.WalletTestUtils.walletDocumentValidationRequestedStatus
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithAPM
import it.pagopa.wallet.WalletTestUtils.walletDocumentVerifiedWithCardDetails
import it.pagopa.wallet.WalletTestUtils.walletDocumentWithError
import it.pagopa.wallet.WalletTestUtils.walletDomain
import it.pagopa.wallet.WalletTestUtils.walletDomainEmptyServicesNullDetailsNoPaymentInstrument
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.applications.Application as ApplicationDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.PayPalDetails
import it.pagopa.wallet.domain.applications.Application
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletApplication
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.*
import it.pagopa.wallet.util.JwtTokenUtils
import it.pagopa.wallet.util.TransactionId
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
    private val applicationRepository: ApplicationRepository = mock()
    private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient = mock()
    private val npgClient: NpgClient = mock()
    private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper = mock()
    private val uniqueIdUtils: UniqueIdUtils = mock()
    private val jwtTokenUtils: JwtTokenUtils = mock()
    private val onboardingConfig =
        OnboardingConfig(
            apmReturnUrl = URI.create("http://localhost/onboarding/apm"),
            cardReturnUrl = URI.create("http://localhost/onboarding/creditcard"),
            payPalPSPApiKey = "paypalPSPApiKey"
        )
    private val sessionUrlConfig =
        SessionUrlConfig(
            "http://localhost:1234",
            "/esito",
            "/annulla",
            "http://localhost/payment-wallet-notifications/v1/wallets/{walletId}/sessions/{orderId}",
            "http://localhost/payment-wallet-notifications/v1/transaction/{transactionId}/wallets/{walletId}/sessions/{orderId}/notifications?sessionToken={sessionToken}"
        )

    private val onboardingPaymentWalletCreditCardReturnUrl = "http://localhost/payment/creditcard"

    companion object {

        @JvmStatic
        private fun declinedAuthErrorCodeTestSource() =
            Stream.of(
                Arguments.of("100", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("101", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_7),
                Arguments.of("102", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("104", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("106", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("109", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("110", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("111", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_7),
                Arguments.of("115", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("116", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("117", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("118", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("119", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("120", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("121", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("122", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("123", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("124", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("125", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("126", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("129", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("200", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("202", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("204", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("208", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("209", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("210", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3),
                Arguments.of("413", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("888", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("902", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("903", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2),
                Arguments.of("904", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("906", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("907", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("908", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("909", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("911", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("913", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of("999", SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
                Arguments.of(null, SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1),
            )

        @JvmStatic
        private fun operationResultErrorStatusMethodSource() =
            Stream.of(
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.AUTHORIZED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.DENIED_BY_RISK,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.THREEDS_VALIDATED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.THREEDS_FAILED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.PENDING,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.CANCELED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_8
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.VOIDED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.REFUNDED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                ),
                Arguments.of(
                    WalletNotificationRequestDto.OperationResultEnum.FAILED,
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                ),
            )
    }

    private val walletService: WalletService =
        WalletService(
            walletRepository,
            applicationRepository,
            ecommercePaymentMethodsClient,
            npgClient,
            npgSessionRedisTemplate,
            sessionUrlConfig,
            uniqueIdUtils,
            onboardingConfig,
            jwtTokenUtils,
            onboardingPaymentWalletCreditCardReturnUrl
        )
    private val mockedUUID = WALLET_UUID.value
    private val mockedInstant = creationDate

    @Test
    fun `should save wallet document for CARDS payment method`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use { uuidMockStatic ->
            uuidMockStatic.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use { instantMockStatic ->
                println("Mocked uuid: $mockedUUID")
                println("Mocked instant: $mockedInstant")
                instantMockStatic.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val newWalletDocumentToBeSaved = newWalletDocumentToBeSaved(PAYMENT_METHOD_ID_CARDS)
                val expectedLoggedAction =
                    LoggedAction(
                        newWalletDocumentToBeSaved.toDomain(),
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }
                    .willAnswer { Mono.just(newWalletDocumentToBeSaved) }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWallet(
                            walletApplicationList = listOf(WALLET_APPLICATION_ID),
                            userId = USER_ID.id,
                            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value
                        )
                    )
                    .assertNext { createWalletOutput ->
                        assertEquals(
                            Pair(expectedLoggedAction, onboardingConfig.cardReturnUrl),
                            createWalletOutput
                        )
                    }
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).save(newWalletDocumentToBeSaved)
            }
        }
    }

    @Test
    fun `should save wallet document for APM payment method`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use { uuidMockStatic ->
            uuidMockStatic.`when`<Any> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use { instantMockStatic ->
                instantMockStatic.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val newWalletDocumentToBeSaved = newWalletDocumentToBeSaved(PAYMENT_METHOD_ID_APM)
                val expectedLoggedAction =
                    LoggedAction(
                        newWalletDocumentToBeSaved.toDomain(),
                        WalletAddedEvent(mockedUUID.toString())
                    )

                given { walletRepository.save(any()) }.willAnswer { Mono.just(it.arguments[0]) }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willReturn { Mono.just(getValidAPMPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWallet(
                            walletApplicationList = listOf(WALLET_APPLICATION_ID),
                            userId = USER_ID.id,
                            paymentMethodId = PAYMENT_METHOD_ID_APM.value
                        )
                    )
                    .assertNext { createWalletOutput ->
                        assertEquals(
                            Pair(expectedLoggedAction, onboardingConfig.apmReturnUrl),
                            createWalletOutput
                        )
                    }
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_APM.value.toString())
                verify(walletRepository, times(1)).save(newWalletDocumentToBeSaved)
            }
        }
    }

    @Test
    fun `should save wallet document for payment with contextual onboard for CARDS payment method`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use { uuidMockStatic ->
            uuidMockStatic.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use { instantMockStatic ->
                println("Mocked uuid: $mockedUUID")
                println("Mocked instant: $mockedInstant")
                instantMockStatic.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val newWalletDocumentForPaymentWithContextualOnboardToBeSaved =
                    newWalletDocumentForPaymentWithContextualOnboardToBeSaved(
                        PAYMENT_METHOD_ID_CARDS
                    )
                val expectedLoggedAction =
                    LoggedAction(
                        newWalletDocumentForPaymentWithContextualOnboardToBeSaved.toDomain(),
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }
                    .willAnswer {
                        Mono.just(newWalletDocumentForPaymentWithContextualOnboardToBeSaved)
                    }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWalletForTransaction(
                            userId = USER_ID.id,
                            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value,
                            transactionId = TransactionId(TRANSACTION_ID),
                            amount = AMOUNT
                        )
                    )
                    .assertNext { createWalletOutput ->
                        assertEquals(
                            Pair(
                                expectedLoggedAction,
                                Optional.of(URI.create(onboardingPaymentWalletCreditCardReturnUrl))
                            ),
                            createWalletOutput
                        )
                    }
                    .verifyComplete()
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1))
                    .save(newWalletDocumentForPaymentWithContextualOnboardToBeSaved)
            }
        }
    }

    @Test
    fun `should save wallet document for payment with contextual onboard for APM payment method`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use { uuidMockStatic ->
            uuidMockStatic.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use { instantMockStatic ->
                println("Mocked uuid: $mockedUUID")
                println("Mocked instant: $mockedInstant")
                instantMockStatic.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val newWalletDocumentForPaymentWithContextualOnboardToBeSaved =
                    newWalletDocumentForPaymentWithContextualOnboardToBeSaved(PAYMENT_METHOD_ID_APM)

                val expectedLoggedAction =
                    LoggedAction(
                        newWalletDocumentForPaymentWithContextualOnboardToBeSaved.toDomain(),
                        WalletAddedEvent(WALLET_UUID.value.toString())
                    )

                given { walletRepository.save(any()) }
                    .willAnswer {
                        Mono.just(newWalletDocumentForPaymentWithContextualOnboardToBeSaved)
                    }
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidAPMPaymentMethod()) }

                /* test */

                StepVerifier.create(
                        walletService.createWalletForTransaction(
                            userId = USER_ID.id,
                            paymentMethodId = PAYMENT_METHOD_ID_APM.value,
                            transactionId = TransactionId(TRANSACTION_ID),
                            amount = AMOUNT
                        )
                    )
                    .assertNext { createWalletOutput ->
                        assertEquals(
                            Pair(expectedLoggedAction, Optional.empty<URI>()),
                            createWalletOutput
                        )
                    }
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_APM.value.toString())
                verify(walletRepository, times(1))
                    .save(newWalletDocumentForPaymentWithContextualOnboardToBeSaved)
            }
        }
    }

    @Test
    fun `should create new wallet session with CARD wallet`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)
            val uniqueId = getUniqueId()
            val orderId = uniqueId
            val contractId = uniqueId

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val sessionId = UUID.randomUUID().toString()
                val npgFields =
                    Fields()
                        .sessionId(sessionId)
                        .securityToken("token")
                        .state(WorkflowState.GDI_VERIFICATION)
                        .apply {
                            fields =
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
                        }
                val sessionResponseDto =
                    SessionWalletCreateResponseDto()
                        .orderId(orderId)
                        .sessionData(
                            SessionWalletCreateResponseCardDataDto()
                                .paymentMethodType("cards")
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
                        )

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentCreatedStatus =
                    walletDocumentCreatedStatus(PAYMENT_METHOD_ID_CARDS)
                var walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                val expectedLoggedAction =
                    LoggedAction(
                        walletDocumentInitializedStatus.toDomain(),
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
                                Pair("walletId", walletDocumentCreatedStatus.id),
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

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                given { npgClient.createNpgOrderBuild(any(), any()) }
                    .willAnswer { mono { npgFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentCreatedStatus))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(
                        walletService.createSessionWallet(
                            WALLET_UUID.value,
                            SessionInputCardDataDto()
                        )
                    )
                    .expectNext(Pair(sessionResponseDto, expectedLoggedAction))
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(uniqueIdUtils, times(2)).generateUniqueId()
                verify(npgClient, times(1))
                    .createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                verify(npgSessionRedisTemplate, times(1)).save(npgSession)
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
            }
        }
    }

    @Test
    fun `should create wallet session for transaction with contextual onboard with CARD wallet`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)
            val uniqueId = getUniqueId()
            val orderId = uniqueId
            val contractId = uniqueId

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val sessionId = UUID.randomUUID().toString()
                val npgFields =
                    Fields()
                        .sessionId(sessionId)
                        .securityToken("token")
                        .state(WorkflowState.GDI_VERIFICATION)
                        .apply {
                            fields =
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
                        }
                val sessionResponseDto =
                    SessionWalletCreateResponseDto()
                        .orderId(orderId)
                        .sessionData(
                            SessionWalletCreateResponseCardDataDto()
                                .paymentMethodType("cards")
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
                        )
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentCreatedStatusForTransactionWithContextualOnboard =
                    walletDocumentCreatedStatusForTransactionWithContextualOnboard(
                        PAYMENT_METHOD_ID_CARDS
                    )
                val walletDocumentInitializedStatusForTransactionWithContextualOnboard =
                    walletDocumentInitializedStatusForTransactionWithContextualOnboard(
                        PAYMENT_METHOD_ID_CARDS
                    )

                val expectedLoggedAction =
                    LoggedAction(
                        walletDocumentInitializedStatusForTransactionWithContextualOnboard
                            .toDomain(),
                        SessionWalletAddedEvent(WALLET_UUID.value.toString())
                    )

                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(
                            sessionUrlConfig.trxWithContextualOnboardNotificationUrl
                        )
                        .build(
                            mapOf(
                                Pair("transactionId", TransactionId(TRANSACTION_ID).value()),
                                Pair(
                                    "walletId",
                                    walletDocumentCreatedStatusForTransactionWithContextualOnboard
                                        .id
                                ),
                                Pair("orderId", orderId),
                                Pair("sessionToken", "token")
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
                                .amount(AMOUNT.toString())
                                .currency(WalletService.CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                        )
                        .paymentSession(
                            PaymentSession()
                                .actionType(ActionType.PAY)
                                .recurrence(
                                    RecurringSettings()
                                        .action(RecurringAction.CONTRACT_CREATION)
                                        .contractId(contractId)
                                        .contractType(RecurringContractType.CIT)
                                )
                                .amount(AMOUNT.toString())
                                .language(WalletService.CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                                .captureType(CaptureType.IMPLICIT)
                                .paymentService("CARDS")
                                .resultUrl(resultUrl.toString())
                                .cancelUrl(cancelUrl.toString())
                                .notificationUrl(notificationUrl.toString())
                        )

                given { npgClient.createNpgOrderBuild(any(), any()) }
                    .willAnswer { mono { npgFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(
                        Mono.just(walletDocumentCreatedStatusForTransactionWithContextualOnboard)
                    )

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { jwtTokenUtils.generateJwtTokenForNpgNotifications(any(), any()) }
                    .willAnswer { Either.right<JWTTokenGenerationException, String>("token") }

                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(
                        walletService.createSessionWallet(
                            WALLET_UUID.value,
                            SessionInputCardDataDto()
                        )
                    )
                    .expectNext(Pair(sessionResponseDto, expectedLoggedAction))
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(uniqueIdUtils, times(2)).generateUniqueId()
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(npgClient, times(1))
                    .createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                verify(npgSessionRedisTemplate, times(1)).save(npgSession)
            }
        }
    }

    @Test
    fun `should throw exception if receives a state that is not REDIRECT_TO_EXTERNAL_DOMAIN when creating session for APM`() {
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
                val apmRedirectUrl = "https://apm-url"
                val npgFields =
                    Fields()
                        .sessionId(sessionId)
                        .securityToken("token")
                        .url(apmRedirectUrl)
                        .state(WorkflowState.READY_FOR_PAYMENT)

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                var walletDocumentValidationRequestedStatus =
                    walletDocumentValidationRequestedStatus(PAYMENT_METHOD_ID_APM)

                val walletDocumentCreatedStatus = walletDocumentCreatedStatus(PAYMENT_METHOD_ID_APM)

                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                        .build(
                            mapOf(
                                Pair("walletId", walletDocumentCreatedStatus.id),
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
                                .paymentService("PAYPAL")
                                .resultUrl(resultUrl.toString())
                                .cancelUrl(cancelUrl.toString())
                                .notificationUrl(notificationUrl.toString())
                        )

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidAPMPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                given { npgClient.createNpgOrderBuild(any(), any()) }
                    .willAnswer { mono { npgFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentCreatedStatus))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(
                        walletService.createSessionWallet(
                            WALLET_UUID.value,
                            APM_SESSION_CREATE_REQUEST
                        )
                    )
                    .expectError(NpgClientException::class.java)
                    .verify()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_APM.value.toString())
                verify(uniqueIdUtils, times(2)).generateUniqueId()
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentValidationRequestedStatus)
                verify(npgClient, times(1))
                    .createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                verify(npgSessionRedisTemplate, times(1)).save(npgSession)
            }
        }
    }

    @Test
    fun `should throw exception if receives an empty fields list when creating session for cards`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)
            val uniqueId = getUniqueId()
            val orderId = uniqueId
            val contractId = uniqueId

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)
                val sessionId = UUID.randomUUID().toString()
                val npgFields =
                    Fields()
                        .sessionId(sessionId)
                        .securityToken("token")
                        .state(WorkflowState.READY_FOR_PAYMENT)
                        .apply { fields = listOf() }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidCardsPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                val walletDocumentCreatedStatus = walletDocumentEmptyCreatedStatus()

                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                        .build(
                            mapOf(
                                Pair("walletId", walletDocumentCreatedStatus.id),
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

                given { npgClient.createNpgOrderBuild(any(), any()) }
                    .willAnswer { mono { npgFields } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentCreatedStatus))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(eq(npgSession)) }
                    .willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(
                        walletService.createSessionWallet(
                            WALLET_UUID.value,
                            SessionInputCardDataDto()
                        )
                    )
                    .expectError(NpgClientException::class.java)
                    .verify()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(uniqueIdUtils, times(2)).generateUniqueId()
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentInitializedStatus)
                verify(npgClient, times(1))
                    .createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                verify(npgSessionRedisTemplate, times(1)).save(npgSession)
            }
        }
    }

    @Test
    fun `should create wallet session for transaction with APM`() {
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
                val sessionId = mockedUUID
                val npgCorrelationId = mockedUUID
                val apmRedirectUrl = "https://apm-url"

                val npgFields =
                    Fields()
                        .sessionId(sessionId.toString())
                        .securityToken("token")
                        .url("https://apm-url")
                        .state(WorkflowState.REDIRECTED_TO_EXTERNAL_DOMAIN)

                val sessionResponseDto =
                    SessionWalletCreateResponseDto()
                        .orderId(orderId)
                        .sessionData(
                            SessionWalletCreateResponseAPMDataDto()
                                .redirectUrl(apmRedirectUrl)
                                .paymentMethodType("apm")
                        )

                val npgSession =
                    NpgSession(orderId, sessionId.toString(), "token", WALLET_UUID.value.toString())

                val walletDocumentValidationRequestedStatus =
                    walletDocumentValidationRequestedStatus(PAYMENT_METHOD_ID_APM)
                val walletDocumentCreatedStatus = walletDocumentCreatedStatus(PAYMENT_METHOD_ID_APM)
                val expectedLoggedAction =
                    LoggedAction(
                        walletDocumentValidationRequestedStatus.toDomain(),
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
                                Pair("walletId", walletDocumentCreatedStatus.id),
                                Pair("orderId", orderId),
                            )
                        )

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
                                .paymentService("PAYPAL")
                                .resultUrl(resultUrl.toString())
                                .cancelUrl(cancelUrl.toString())
                                .notificationUrl(notificationUrl.toString())
                        )

                /* Mock response */
                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { Mono.just(getValidAPMPaymentMethod()) }

                given { uniqueIdUtils.generateUniqueId() }.willAnswer { Mono.just(uniqueId) }

                given { npgClient.createNpgOrderBuild(any(), any()) }
                    .willAnswer { mono { npgFields } }

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentCreatedStatus))

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()
                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }
                given { npgSessionRedisTemplate.save(any()) }.willAnswer { mono { npgSession } }
                /* test */
                StepVerifier.create(
                        walletService.createSessionWallet(
                            WALLET_UUID.value,
                            APM_SESSION_CREATE_REQUEST
                        )
                    )
                    .expectNext(Pair(sessionResponseDto, expectedLoggedAction))
                    .verifyComplete()

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_APM.value.toString())
                verify(uniqueIdUtils, times(2)).generateUniqueId()
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentValidationRequestedStatus)
                verify(npgClient, times(1))
                    .createNpgOrderBuild(npgCorrelationId, npgCreateHostedOrderRequest)
                verify(npgSessionRedisTemplate, times(1)).save(npgSession)
            }
        }
    }
    @Test
    fun `should execute validation for wallet CARD`() {
        /* preconditions */

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
                        .state(WorkflowState.GDI_VERIFICATION)
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
                                                .fields!![0]
                                                .src!!
                                                .toByteArray(StandardCharsets.UTF_8)
                                        )
                                )
                        )

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

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

                given { npgClient.getCardData(any(), any()) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given { npgClient.confirmPayment(any(), any()) }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { mono { getValidCardsPaymentMethod() } }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectNext(Pair(verifyResponse, expectedLoggedAction))
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(
                    walletDocumentToSave.details,
                    CardDetails("CARDS", "12345678", "12345678****0000", "12/30", "MASTERCARD", "?")
                )

                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentWithCardDetails)
                verify(npgClient, times(1)).getCardData(sessionId, npgCorrelationId)
                verify(npgClient, times(1))
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        npgCorrelationId
                    )
            }
        }
    }
    @Test
    fun `should throw error when execute validation for wallet APM`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val sessionId = UUID.randomUUID().toString()
                val npgCorrelationId = mockedUUID
                val orderId = Instant.now().toString() + "ABCDE"

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                // Inconsistent wallet type APM
                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_APM)

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { mono { getValidAPMPaymentMethod() } }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(NoCardsSessionValidateRequestException::class.java)
                    .verify()

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_APM.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(0)).save(any())
                verify(npgClient, times(0)).confirmPayment(any(), any())
            }
        }
    }
    @Test
    fun `should throw error when execute validation SessionNotFoundException`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                given { npgSessionRedisTemplate.findById(any()) }.willAnswer { null }
                /* test */
                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(SessionNotFoundException::class.java)
                    .verify()

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
                verify(walletRepository, times(0)).findById(any<String>())
                verify(walletRepository, times(0)).save(any())
                verify(npgClient, times(0)).confirmPayment(any(), any())
            }
        }
    }
    @Test
    fun `should throw error when execute validation WalletNotFoundException`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                given { npgSessionRedisTemplate.findById(any()) }.willAnswer { npgSession }
                given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())

                /* test */
                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletNotFoundException::class.java)
                    .verify()

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(0)).save(any())
                verify(npgClient, times(0)).confirmPayment(any(), any())
            }
        }
    }
    @Test
    fun `should throw error when execute validation WalletSessionMismatchException`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"

                val npgSession = NpgSession(orderId, sessionId, "token", "testWalletIdWrong")

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                given { walletRepository.findById(any<String>()) }
                    .willReturn(mono { walletDocumentInitializedStatus })

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletSessionMismatchException::class.java)
                    .verify()

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(0)).save(any())
                verify(npgClient, times(0)).confirmPayment(any(), any())
            }
        }
    }
    @Test
    fun `should throw error when execute validation WalletConflictStatusException`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val orderId = Instant.now().toString() + "ABCDE"
                val sessionId = "sessionId"
                val walletDocumentValidationRequestedStatus =
                    walletDocumentValidationRequestedStatus(PAYMENT_METHOD_ID_CARDS)
                val npgSession =
                    NpgSession(
                        orderId,
                        sessionId,
                        "token",
                        walletDocumentValidationRequestedStatus.id
                    )

                given { walletRepository.findById(any<String>()) }
                    .willReturn(mono { walletDocumentValidationRequestedStatus })

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(WalletConflictStatusException::class.java)
                    .verify()

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(0)).save(any())
                verify(npgClient, times(0)).confirmPayment(any(), any())
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by wrong state`() {
        /* preconditions */

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
                    StateResponse().state(WorkflowState.READY_FOR_PAYMENT).url("http://state.url")

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                given { npgClient.getCardData(sessionId, npgCorrelationId) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given { npgClient.confirmPayment(any(), any()) }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(orderId) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { mono { getValidCardsPaymentMethod() } }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentToSave)
                verify(npgClient, times(1))
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        npgCorrelationId
                    )
            }
        }
    }
    @Test
    fun `validate should throws BadGatewayException with card data by fields null`() {
        /* preconditions */

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
                val npgStateResponse = StateResponse().state(WorkflowState.GDI_VERIFICATION)

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                given { npgClient.getCardData(any(), any()) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given { npgClient.confirmPayment(any(), any()) }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(any()) }.willAnswer { npgSession }

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
                    .willAnswer { mono { getValidCardsPaymentMethod() } }

                /* test */

                StepVerifier.create(walletService.validateWalletSession(orderId, WALLET_UUID.value))
                    .expectError(BadGatewayException::class.java)
                    .verify()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.status, WalletStatusDto.ERROR.value)

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentToSave)
                verify(npgClient, times(1))
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        npgCorrelationId
                    )
                verify(npgClient, times(1)).getCardData(sessionId, npgCorrelationId)
            }
        }
    }

    @Test
    fun `validate should throws BadGatewayException with card data by fields list empty`() {
        /* preconditions */

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
                        .state(WorkflowState.GDI_VERIFICATION)
                        .fieldSet(Fields().sessionId(sessionId))

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                given { npgClient.getCardData(any(), any()) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given { npgClient.confirmPayment(any(), any()) }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(any()) }.willAnswer { npgSession }

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

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentToSave)
                verify(npgClient, times(1))
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        npgCorrelationId
                    )
                verify(npgClient, times(1)).getCardData(sessionId, npgCorrelationId)
            }
        }
    }
    @Test
    fun `validate should throws BadGatewayException with card data by first field src null`() {
        /* preconditions */

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
                        .state(WorkflowState.GDI_VERIFICATION)
                        .fieldSet(Fields().sessionId(sessionId).addFieldsItem(Field().id("field")))

                val npgSession =
                    NpgSession(orderId, sessionId, "token", WALLET_UUID.value.toString())

                val walletDocumentInitializedStatus =
                    walletDocumentInitializedStatus(PAYMENT_METHOD_ID_CARDS)

                given { npgClient.getCardData(any(), any()) }
                    .willAnswer { mono { npgGetCardDataResponse } }

                given { npgClient.confirmPayment(any(), any()) }
                    .willAnswer { mono { npgStateResponse } }

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocumentInitializedStatus))

                given { npgSessionRedisTemplate.findById(any()) }.willAnswer { npgSession }

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

                verify(npgSessionRedisTemplate, times(1)).findById(orderId)
                verify(ecommercePaymentMethodsClient, times(1))
                    .getPaymentMethodById(PAYMENT_METHOD_ID_CARDS.value.toString())
                verify(walletRepository, times(1)).findById(WALLET_UUID.value.toString())
                verify(walletRepository, times(1)).save(walletDocumentToSave)
                verify(npgClient, times(1))
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        npgCorrelationId
                    )
                verify(npgClient, times(1)).getCardData(sessionId, npgCorrelationId)
            }
        }
    }
    @Test
    fun `should find wallet document with cards`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val wallet = walletDocumentStatusValidatedCARD()
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.id))
                                    .status(ApplicationStatusDto.valueOf(application.status))
                            }
                        )
                        .details(
                            WalletCardDetailsDto()
                                .type((wallet.details as CardDetails).type)
                                .bin((wallet.details as CardDetails).bin)
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
    fun `should find wallet document with paypal with email`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val wallet = walletDocumentStatusValidatedAPM(MASKED_EMAIL.value)
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.id))
                                    .status(ApplicationStatusDto.valueOf(application.status))
                            }
                        )
                        .details(
                            WalletPaypalDetailsDto().maskedEmail(MASKED_EMAIL.value).pspId(PSP_ID)
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
    fun `should find wallet document with paypal without email`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                print("Mocked instant: $mockedInstant")
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val wallet = walletDocumentStatusValidatedAPM(null)

                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.id))
                                    .status(ApplicationStatusDto.valueOf(application.status))
                            }
                        )
                        .details(WalletPaypalDetailsDto().maskedEmail(null).pspId(PSP_ID))

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

                val wallet = walletDocumentStatusValidatedCARD()
                val walletInfoDto =
                    WalletInfoDto()
                        .walletId(UUID.fromString(wallet.id))
                        .status(WalletStatusDto.valueOf(wallet.status))
                        .paymentMethodId(wallet.paymentMethodId)
                        .userId(wallet.userId)
                        .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .creationDate(OffsetDateTime.parse(wallet.updateDate.toString()))
                        .services(
                            wallet.applications.map { application ->
                                ServiceDto()
                                    .name(ServiceNameDto.valueOf(application.id))
                                    .status(ApplicationStatusDto.valueOf(application.status))
                            }
                        )
                        .details(
                            WalletCardDetailsDto()
                                .type((wallet.details as CardDetails).type)
                                .bin((wallet.details as CardDetails).bin)
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
    fun `should find wallet auth data by ID with cards`() {
        /* preconditions */

        val wallet = walletDocumentStatusValidatedCARD()
        val walletAuthDataDto = WalletTestUtils.walletCardAuthDataDto()

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.just(wallet))

        /* test */
        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .expectNext(walletAuthDataDto)
            .verifyComplete()
    }

    @Test
    fun `should find wallet auth data by ID with apm`() {
        /* preconditions */

        val wallet = walletDocumentStatusValidatedAPM(MASKED_EMAIL.value)
        val walletAuthDataDto = WalletTestUtils.walletAPMAuthDataDto()

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.just(wallet))

        /* test */

        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .assertNext { assertEquals(walletAuthDataDto, it) }
            .verifyComplete()
    }

    @Test
    fun `should throw exception if getAuthData is called with null details`() {
        /* preconditions */

        val wallet = walletDocumentStatusValidatedCARD().copy(details = null)

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.just(wallet))

        /* test */

        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should throws wallet not found exception when retrieve auth data by ID`() {
        /* preconditions */
        val wallet = walletDocumentStatusValidatedCARD()

        given { walletRepository.findById(wallet.id) }.willReturn(Mono.empty())
        /* test */

        StepVerifier.create(walletService.findWalletAuthData(WALLET_UUID))
            .expectError(WalletNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should patch wallet document when adding applications with valid statuses`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                print("Mocked instant: ${Instant.now()} $mockedInstant")

                val wallet = walletDomainEmptyServicesNullDetailsNoPaymentInstrument()
                val walletDocumentEmptyServicesNullDetailsNoPaymentInstrument =
                    walletDocumentEmptyCreatedStatus()

                val newWalletApplicationStatus = WalletApplicationStatus.ENABLED
                val expectedLoggedAction =
                    LoggedAction(
                        WalletServiceUpdateData(
                            updatedWallet =
                                wallet
                                    .copy(
                                        applications =
                                            listOf(
                                                WalletApplication(
                                                    WALLET_APPLICATION_ID,
                                                    newWalletApplicationStatus,
                                                    mockedInstant,
                                                    mockedInstant,
                                                    APPLICATION_METADATA
                                                )
                                            ),
                                        updateDate = mockedInstant
                                    )
                                    .toDocument(),
                            successfullyUpdatedApplications =
                                mapOf(WALLET_APPLICATION_ID to newWalletApplicationStatus),
                            applicationsWithUpdateFailed = mapOf()
                        ),
                        WalletPatchEvent(WALLET_UUID.value.toString())
                    )

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(
                        Mono.just(walletDocumentEmptyServicesNullDetailsNoPaymentInstrument)
                    )

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willAnswer { Mono.just(it.arguments[0]) }

                given { applicationRepository.findById(APPLICATION_ID.id) }
                    .willReturn(
                        Mono.just(
                            APPLICATION_DOCUMENT.copy(status = ApplicationStatus.ENABLED.name)
                        )
                    )

                /* test */
                assertTrue(wallet.applications.isEmpty())

                StepVerifier.create(
                        walletService.updateWalletServices(
                            WALLET_UUID.value,
                            listOf(Pair(WALLET_APPLICATION_ID, newWalletApplicationStatus))
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
    fun `should patch wallet document editing application status with valid status`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val newWalletApplicationStatus = WalletApplicationStatus.ENABLED
                val applicationCreationDate = TIMESTAMP

                val expectedLoggedAction =
                    LoggedAction(
                        WalletServiceUpdateData(
                            updatedWallet =
                                walletDomain()
                                    .copy(
                                        applications =
                                            listOf(
                                                WalletApplication(
                                                    WALLET_APPLICATION_ID,
                                                    newWalletApplicationStatus,
                                                    applicationCreationDate,
                                                    mockedInstant,
                                                    APPLICATION_METADATA
                                                )
                                            ),
                                        updateDate = mockedInstant
                                    )
                                    .toDocument(),
                            successfullyUpdatedApplications =
                                mapOf(WALLET_APPLICATION_ID to WalletApplicationStatus.ENABLED),
                            applicationsWithUpdateFailed = mapOf()
                        ),
                        WalletPatchEvent(WALLET_UUID.value.toString())
                    )

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()
                val walletDocument = walletDocument()
                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocument))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willReturn(Mono.just(walletDocument))

                given { applicationRepository.findById(APPLICATION_ID.id) }
                    .willReturn(
                        Mono.just(
                            APPLICATION_DOCUMENT.copy(status = ApplicationStatus.ENABLED.name)
                        )
                    )

                /* test */
                assertEquals(walletDocument.applications.size, 1)
                assertEquals(walletDocument.applications[0].id, APPLICATION_DOCUMENT.id)
                assertEquals(
                    walletDocument.applications[0].status,
                    ApplicationStatus.DISABLED.toString()
                )

                StepVerifier.create(
                        walletService.updateWalletServices(
                            WALLET_UUID.value,
                            listOf(Pair(WALLET_APPLICATION_ID, newWalletApplicationStatus))
                        )
                    )
                    .expectNext(expectedLoggedAction)
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.applications.size, 1)
                assertEquals(walletDocumentToSave.applications[0].id, WALLET_APPLICATION_ID.id)
                assertEquals(
                    walletDocumentToSave.applications[0].status,
                    WalletApplicationStatus.ENABLED.toString()
                )
            }
        }
    }

    @Test
    fun `should keep old applications when patching wallet`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val walletDocument = walletDocument()

                val expectedLoggedAction =
                    LoggedAction(
                        WalletServiceUpdateData(
                            updatedWallet =
                                walletDomain()
                                    .copy(
                                        applications =
                                            walletDocument.applications.map { it.toDomain() },
                                        updateDate = mockedInstant
                                    )
                                    .toDocument(),
                            successfullyUpdatedApplications = mapOf(),
                            applicationsWithUpdateFailed = mapOf()
                        ),
                        WalletPatchEvent(WALLET_UUID.value.toString())
                    )

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()
                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocument))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willReturn(Mono.just(walletDocument))

                given { applicationRepository.findById(APPLICATION_ID.id) }
                    .willReturn(
                        Mono.just(
                            APPLICATION_DOCUMENT.copy(status = ApplicationStatus.ENABLED.name)
                        )
                    )

                /* test */
                assertEquals(walletDocument.applications.size, 1)
                assertEquals(walletDocument.applications[0].id, APPLICATION_ID.id)
                assertEquals(
                    walletDocument.applications[0].status,
                    WalletApplicationStatus.DISABLED.toString()
                )

                StepVerifier.create(walletService.updateWalletServices(WALLET_UUID.value, listOf()))
                    .assertNext { assertEquals(expectedLoggedAction, it) }
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.applications.size, 1)
                assertEquals(walletDocumentToSave.applications[0].id, WALLET_APPLICATION_ID.id)
                assertEquals(
                    walletDocumentToSave.applications[0].status,
                    WalletApplicationStatus.DISABLED.toString()
                )
            }
        }
    }

    @Test
    fun `should patch wallet document editing application status and return applications that could not be changed`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val newWalletApplicationStatus = WalletApplicationStatus.ENABLED

                val disabledApplicationId = ApplicationId("IDPAY")
                val disabledWalletApplicationId = WalletApplicationId("IDPAY")

                val disabledApplication =
                    Application(
                        disabledApplicationId,
                        APPLICATION_DESCRIPTION,
                        ApplicationStatus.INCOMING,
                        Instant.now(),
                        Instant.now(),
                    )

                val walletDocument = walletDocument()
                val applicationCreationDate = TIMESTAMP

                val expectedLoggedAction =
                    LoggedAction(
                        WalletServiceUpdateData(
                            updatedWallet =
                                walletDomain()
                                    .copy(
                                        applications =
                                            listOf(
                                                WalletApplication(
                                                    WALLET_APPLICATION_ID,
                                                    newWalletApplicationStatus,
                                                    applicationCreationDate,
                                                    mockedInstant,
                                                    APPLICATION_METADATA
                                                )
                                            ),
                                        updateDate = mockedInstant
                                    )
                                    .toDocument(),
                            successfullyUpdatedApplications =
                                mapOf(WALLET_APPLICATION_ID to WalletApplicationStatus.ENABLED),
                            applicationsWithUpdateFailed =
                                mapOf(
                                    disabledWalletApplicationId to WalletApplicationStatus.INCOMING
                                )
                        ),
                        WalletPatchEvent(WALLET_UUID.value.toString())
                    )

                val walletArgumentCaptor: KArgumentCaptor<Wallet> = argumentCaptor<Wallet>()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocument))

                given { walletRepository.save(walletArgumentCaptor.capture()) }
                    .willReturn(Mono.just(walletDocument))

                given { applicationRepository.findById(APPLICATION_ID.id) }
                    .willReturn(
                        Mono.just(
                            APPLICATION_DOCUMENT.copy(status = ApplicationStatus.ENABLED.name)
                        )
                    )

                given { applicationRepository.findById(disabledApplication.id.id) }
                    .willReturn(Mono.just(ApplicationDocument.fromDomain(disabledApplication)))

                /* test */
                assertEquals(walletDocument.applications.size, 1)
                assertEquals(walletDocument.applications[0].id, WALLET_APPLICATION_ID.id)
                assertEquals(
                    walletDocument.applications[0].status,
                    WalletApplicationStatus.DISABLED.toString()
                )

                StepVerifier.create(
                        walletService.updateWalletServices(
                            WALLET_UUID.value,
                            listOf(
                                Pair(WALLET_APPLICATION_ID, newWalletApplicationStatus),
                                Pair(
                                    WalletApplicationId(disabledApplication.id.id),
                                    newWalletApplicationStatus
                                )
                            )
                        )
                    )
                    .expectNext(expectedLoggedAction)
                    .verifyComplete()

                val walletDocumentToSave = walletArgumentCaptor.firstValue
                assertEquals(walletDocumentToSave.applications.size, 1)
                assertEquals(walletDocumentToSave.applications[0].id, WALLET_APPLICATION_ID.id)
                assertEquals(
                    walletDocumentToSave.applications[0].status,
                    WalletApplicationStatus.ENABLED.toString()
                )
            }
        }
    }

    @Test
    fun `should throw error when trying to patch application status for unknown service`() {
        /* preconditions */

        mockStatic(UUID::class.java, Mockito.CALLS_REAL_METHODS).use {
            it.`when`<UUID> { UUID.randomUUID() }.thenReturn(mockedUUID)

            mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS).use {
                it.`when`<Instant> { Instant.now() }.thenReturn(mockedInstant)

                val newApplicationStatus = WalletApplicationStatus.ENABLED

                val unknownApplication =
                    ApplicationDocument(
                        APPLICATION_ID.id,
                        APPLICATION_DESCRIPTION.description,
                        ApplicationStatus.INCOMING.toString(),
                        Instant.now().toString(),
                        Instant.now().toString()
                    )

                val walletDocument = walletDocument()

                given { walletRepository.findById(any<String>()) }
                    .willReturn(Mono.just(walletDocument))

                given { applicationRepository.findById(APPLICATION_ID.id) }
                    .willReturn(
                        Mono.just(
                            APPLICATION_DOCUMENT.copy(status = ApplicationStatus.ENABLED.name)
                        )
                    )

                given { applicationRepository.findById(unknownApplication.id) }
                    .willReturn(Mono.empty())

                /* test */

                StepVerifier.create(
                        walletService.updateWalletServices(
                            WALLET_UUID.value,
                            listOf(
                                Pair(WALLET_APPLICATION_ID, newApplicationStatus),
                                Pair(
                                    WalletApplicationId(unknownApplication.id),
                                    newApplicationStatus
                                )
                            )
                        )
                    )
                    .expectError(ApplicationNotFoundException::class.java)
                    .verify()

                Mockito.verify(walletRepository, times(0)).save(any())
            }
        }
    }

    @Test
    fun `should throws wallet not found exception`() {
        /* preconditions */

        given { walletRepository.findById(any<String>()) }.willReturn(Mono.empty())
        /* test */

        StepVerifier.create(
                walletService.updateWalletServices(
                    WALLET_UUID.value,
                    listOf(Pair(WALLET_APPLICATION_ID, WalletApplicationStatus.ENABLED))
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
    fun `notify wallet should set wallet status to ERROR for CARDS`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument =
            walletDocumentVerifiedWithCardDetails(
                "12345678",
                "0000",
                "12/30",
                "?",
                WalletCardDetailsDto.BrandEnum.MASTERCARD
            )
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
                    notifyRequestDto.timestampOperation.toString(),
                    null
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
    fun `notify wallet should set wallet status to VALIDATED for CARDS`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument =
            walletDocumentVerifiedWithCardDetails(
                "12345678",
                "0000",
                "12/30",
                "?",
                WalletCardDetailsDto.BrandEnum.MASTERCARD
            )
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))

        val walletDocumentValidated =
            walletDocument.copy(status = WalletStatusDto.VALIDATED.toString())

        given { walletRepository.save(any()) }.willReturn(Mono.just(walletDocumentValidated))

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentValidated.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.EXECUTED.value,
                    notifyRequestDto.timestampOperation.toString(),
                    null,
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
        val walletDocument =
            walletDocumentVerifiedWithAPM(
                PayPalDetails(maskedEmail = MASKED_EMAIL.value, pspId = "pspId")
            )
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
    fun `notify wallet should set wallet status to ERROR for PAYPAL`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument =
            walletDocumentVerifiedWithAPM(
                PayPalDetails(maskedEmail = MASKED_EMAIL.value, pspId = "pspId")
            )
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))
        val walletDocumentWithError =
            walletDocument.copy(
                validationOperationResult = notifyRequestDto.operationResult.value,
                status = WalletStatusDto.ERROR.value
            )

        given { walletRepository.save(any()) }.willAnswer { mono { it.arguments[0] } }

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentWithError.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.DECLINED.value,
                    notifyRequestDto.timestampOperation.toString(),
                    null,
                )
            )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .assertNext { assertEquals(expectedLoggedAction, it) }
            .verifyComplete()
    }

    @Test
    fun `notify wallet should throw error for unhandled wallet details`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument = walletDocumentVerifiedWithAPM(mock())
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))
        val walletDocumentWithError = walletDocumentWithError(notifyRequestDto.operationResult)

        given { walletRepository.save(any()) }.willAnswer { mono { it.arguments[0] } }

        LoggedAction(
            walletDocumentWithError.toDomain(),
            WalletNotificationEvent(
                WALLET_UUID.value.toString(),
                operationId,
                OperationResult.DECLINED.value,
                notifyRequestDto.timestampOperation.toString(),
                null,
            )
        )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .expectError(InvalidRequestException::class.java)
            .verify()
    }

    @Test
    fun `notify wallet should throw error for PAYPAL wallet and wrong details into notification request`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val walletDocument =
            walletDocumentVerifiedWithAPM(
                PayPalDetails(maskedEmail = MASKED_EMAIL.value, pspId = "pspId")
            )
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT
        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))

        val walletDocumentWithError =
            walletDocument.copy(
                validationOperationResult = notifyRequestDto.operationResult.value,
                status = WalletStatusDto.ERROR.value
            )

        given { walletRepository.save(any()) }.willAnswer { mono { it.arguments[0] } }

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentWithError.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.EXECUTED.value,
                    notifyRequestDto.timestampOperation.toString(),
                    null,
                )
            )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .assertNext { assertEquals(expectedLoggedAction, it) }
            .verifyComplete()
    }

    @Test
    fun `notify wallet should set wallet status to VALIDATED for PAYPAL`() {
        /* preconditions */
        val orderId = "orderId"
        val sessionId = "sessionId"
        val sessionToken = "token"
        val operationId = "validationOperationId"
        val notifyRequestDto = NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS
        val walletDocument =
            walletDocumentVerifiedWithAPM(
                PayPalDetails(maskedEmail = MASKED_EMAIL.value, pspId = "pspId")
            )

        val npgSession = NpgSession(orderId, sessionId, sessionToken, WALLET_UUID.value.toString())
        given { npgSessionRedisTemplate.findById(orderId) }.willReturn(npgSession)
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))
        val walletDocumentValidated =
            walletDocument.copy(
                validationOperationResult = notifyRequestDto.operationResult.value,
                status = WalletStatusDto.VALIDATED.toString()
            )

        given { walletRepository.save(any()) }.willAnswer { mono { it.arguments[0] } }

        val expectedLoggedAction =
            LoggedAction(
                walletDocumentValidated.toDomain(),
                WalletNotificationEvent(
                    WALLET_UUID.value.toString(),
                    operationId,
                    OperationResult.EXECUTED.value,
                    notifyRequestDto.timestampOperation.toString(),
                    null,
                )
            )

        /* test */
        StepVerifier.create(
                walletService.notifyWallet(WALLET_UUID, orderId, sessionToken, notifyRequestDto)
            )
            .assertNext { assertEquals(expectedLoggedAction, it) }
            .verifyComplete()
    }

    @Test
    fun `should update wallet status to DELETED when calling deleteWalletById on valid wallet`() {
        val walletDocument =
            walletDocumentVerifiedWithAPM(
                PayPalDetails(maskedEmail = MASKED_EMAIL.value, pspId = "pspId")
            )

        /* preconditions */
        given { walletRepository.findById(any<String>()) }.willReturn(Mono.just(walletDocument))
        given { walletRepository.save(any()) }.willAnswer { Mono.just(it.arguments[0]) }

        val expectedLoggedAction = LoggedAction(Unit, WalletDeletedEvent(walletDocument.id))

        val expectedUpdatedWallet = walletDocument.copy(status = WalletStatusDto.DELETED.toString())

        /* test */
        StepVerifier.create(
                walletService.deleteWallet(WalletId(UUID.fromString(walletDocument.id)))
            )
            .assertNext { assertEquals(expectedLoggedAction, it) }
            .verifyComplete()

        verify(walletRepository, times(1)).save(expectedUpdatedWallet)
    }

    @ParameterizedTest
    @MethodSource("declinedAuthErrorCodeTestSource")
    fun `find session should return response with final status true mapping DENIED error codes for card wallet`(
        errorCode: String?,
        expectedOutcome: SessionWalletRetrieveResponseDto.OutcomeEnum
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
                operationResultEnum = WalletNotificationRequestDto.OperationResultEnum.DECLINED,
                errorCode = errorCode,
                details =
                    CardDetails(
                        WalletDetailsType.CARDS.name,
                        bin = "12345678",
                        maskedPan = "12345678" + "*".repeat(4) + "1234",
                        expiryDate = "24/12",
                        brand = "VISA",
                        paymentInstrumentGatewayId = "paymentInstrumentGatewayId"
                    ),
            )

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(expectedOutcome)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("declinedAuthErrorCodeTestSource")
    fun `find session should return response with final status true mapping DENIED error codes for apm wallet`(
        errorCode: String?
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
                operationResultEnum = WalletNotificationRequestDto.OperationResultEnum.DECLINED,
                errorCode = errorCode,
                details = PayPalDetails(maskedEmail = "p*******@p*******.it", pspId = "pspId"),
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

    @ParameterizedTest
    @MethodSource("operationResultErrorStatusMethodSource")
    fun `find session should return response with final status true mapping NPG operation result for cards wallet`(
        operationResult: WalletNotificationRequestDto.OperationResultEnum,
        expectedOutcome: SessionWalletRetrieveResponseDto.OutcomeEnum
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
                operationResultEnum = operationResult,
                errorCode = null,
                details =
                    CardDetails(
                        WalletDetailsType.CARDS.name,
                        bin = "12345678",
                        maskedPan = "12345678" + "*".repeat(4) + "1234",
                        expiryDate = "24/12",
                        brand = "VISA",
                        paymentInstrumentGatewayId = "paymentInstrumentGatewayId"
                    ),
            )

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(expectedOutcome)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("operationResultErrorStatusMethodSource")
    fun `find session should return response with final status true mapping NPG operation result for apm wallet`(
        operationResult: WalletNotificationRequestDto.OperationResultEnum,
        expectedOutcome: SessionWalletRetrieveResponseDto.OutcomeEnum
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
                operationResultEnum = operationResult,
                errorCode = null,
                details = PayPalDetails(maskedEmail = "p*******@p*******.it", pspId = "pspId"),
            )

        given { walletRepository.findByIdAndUserId(eq(walletId.toString()), eq(userId.toString())) }
            .willReturn(Mono.just(walletDocumentWithError))
        val responseDto =
            SessionWalletRetrieveResponseDto()
                .isFinalOutcome(true)
                .walletId(walletId.toString())
                .outcome(expectedOutcome)
                .orderId(ORDER_ID)

        /* test */
        StepVerifier.create(walletService.findSessionWallet(userId, WalletId(walletId), ORDER_ID))
            .expectNext(responseDto)
            .verifyComplete()
    }

    @Test
    fun `find session should return response with final status true mapping NPG operation result for apm wallet with DECLINED operation result`() {
        /* preconditions */
        val walletId = WALLET_UUID.value
        val userId = USER_ID.id
        val sessionId = "sessionId"
        val sessionToken = "token"
        val npgSession = NpgSession(ORDER_ID, sessionId, sessionToken, walletId.toString())
        given { npgSessionRedisTemplate.findById(ORDER_ID) }.willReturn(npgSession)
        val walletDocumentWithError =
            walletDocumentWithError(
                operationResultEnum = WalletNotificationRequestDto.OperationResultEnum.DECLINED,
                errorCode = null,
                details = PayPalDetails(maskedEmail = "p*******@p*******.it", pspId = "pspId"),
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
