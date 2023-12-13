package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.WalletCardDetailsDto.BrandEnum
import it.pagopa.wallet.domain.details.WalletDetailsType
import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.services.WalletService
import java.nio.charset.StandardCharsets
import java.util.*
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.test.StepVerifier

class NpgClientTest {
    private val cardsServicesApi: PaymentServicesApi = mock()
    private val paypalServicesApi: PaymentServicesApi = mock()
    private val npgClient = NpgClient(cardsServicesApi, paypalServicesApi)

    private val correlationId = UUID.randomUUID()
    private val sessionId = "sessionId"

    private fun orderBuildRequest(paymentService: WalletDetailsType) =
        CreateHostedOrderRequest()
            .version("2")
            .merchantUrl("https://test")
            .order(Order().orderId(UUID.randomUUID().toString()))
            .paymentSession(
                PaymentSession()
                    .actionType(ActionType.VERIFY)
                    .recurrence(
                        RecurringSettings()
                            .action(RecurringAction.CONTRACT_CREATION)
                            .contractId("contractId")
                            .contractType(RecurringContractType.CIT)
                    )
                    .amount(WalletService.CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                    .language(WalletService.CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                    .captureType(CaptureType.IMPLICIT)
                    .paymentService(paymentService.name)
                    .resultUrl("resultUrl")
                    .cancelUrl("cancelUrl")
                    .notificationUrl("notificationUrl")
            )

    @Test
    fun `Should create payment order build successfully with cards`() {
        val fields =
            Fields().apply {
                sessionId = UUID.randomUUID().toString()
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

        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willReturn(mono { fields })

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectNext(fields)
            .verifyComplete()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should create payment order build successfully with PayPal`() {
        val fields =
            Fields().apply {
                sessionId = UUID.randomUUID().toString()
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

        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.PAYPAL)
        given(paypalServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willReturn(mono { fields })

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectNext(fields)
            .verifyComplete()

        Mockito.verify(cardsServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should get card data successfully`() {
        val cardDataResponse =
            CardDataResponse()
                .bin("123456")
                .lastFourDigits("0000")
                .expiringDate("122030")
                .circuit(BrandEnum.MASTERCARD.name)

        // prerequisite
        given(cardsServicesApi.pspApiV1BuildCardDataGet(correlationId, sessionId))
            .willReturn(mono { cardDataResponse })

        // test and assertions
        StepVerifier.create(npgClient.getCardData(sessionId, correlationId))
            .expectNext(cardDataResponse)
            .verifyComplete()
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication for getCardData`() {
        // prerequisite

        given(cardsServicesApi.pspApiV1BuildCardDataGet(correlationId, sessionId))
            .willThrow(
                WebClientResponseException.create(
                    500,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.getCardData(sessionId, correlationId))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1BuildCardDataGet(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1BuildCardDataGet(any(), any())
    }

    @Test
    fun `Should validate payment successfully`() {
        val stateResponse = StateResponse().url("http://redirectUrl")

        val confirmPaymentRequest = ConfirmPaymentRequest().sessionId(sessionId).amount("0")

        // prerequisite
        given(
                cardsServicesApi.pspApiV1BuildConfirmPaymentPost(
                    correlationId,
                    ConfirmPaymentRequest().amount("0").sessionId(sessionId)
                )
            )
            .willReturn(mono { stateResponse })

        // test and assertions
        StepVerifier.create(npgClient.confirmPayment(confirmPaymentRequest, correlationId))
            .expectNext(stateResponse)
            .verifyComplete()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1BuildConfirmPaymentPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1BuildConfirmPaymentPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication for validate`() {
        // prerequisite
        val confirmPaymentRequest = ConfirmPaymentRequest().sessionId(sessionId).amount("0")

        given(
                cardsServicesApi.pspApiV1BuildConfirmPaymentPost(
                    correlationId,
                    confirmPaymentRequest
                )
            )
            .willThrow(
                WebClientResponseException.create(
                    500,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.confirmPayment(confirmPaymentRequest, correlationId))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1BuildConfirmPaymentPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1BuildConfirmPaymentPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    500,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 401 during communication`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    401,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 500 from ecommerce-payment-methods`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    500,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 404 from ecommerce-payment-methods`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    404,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 400 from ecommerce-payment-methods`() {
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)

        // prerequisite
        given(cardsServicesApi.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    400,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()

        Mockito.verify(cardsServicesApi, times(1)).pspApiV1OrdersBuildPost(any(), any())
        Mockito.verify(paypalServicesApi, times(0)).pspApiV1OrdersBuildPost(any(), any())
    }
}
