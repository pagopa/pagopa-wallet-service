package it.pagopa.wallet.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.vavr.control.Either
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.wallet.audit.WalletContractIdDeactivationErrorType
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.npg.NpgPspApiKeysConfig
import java.nio.charset.StandardCharsets
import java.util.*
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.*
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class NpgClientTest {
    private val npgWebClient: PaymentServicesApi = mock()
    private val npgPspApiKeysConfig: NpgPspApiKeysConfig = mock()
    private val tracer: Tracer = mock()
    private val spanBuilder: SpanBuilder = mock()
    private val objectMapper = ObjectMapper()
    private val npgClient = NpgClient(npgWebClient, npgPspApiKeysConfig, tracer, objectMapper)

    private val correlationId = UUID.randomUUID()
    private val sessionId = "sessionId"
    private val defaultApiKey = "defaultApiKey"
    private val pspApiKey = "pspApiKey"

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
                            .contractType(RecurringContractType.CIT))
                    .amount(WalletService.CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                    .language(WalletService.CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                    .captureType(CaptureType.IMPLICIT)
                    .paymentService(paymentService.name)
                    .resultUrl("resultUrl")
                    .cancelUrl("cancelUrl")
                    .notificationUrl("notificationUrl"))

    @BeforeEach
    fun setup() {
        given(spanBuilder.setParent(any())).willReturn(spanBuilder)
        given(spanBuilder.setAttribute(any<AttributeKey<Any>>(), any())).willReturn(spanBuilder)
        given(spanBuilder.startSpan()).willReturn(Span.getInvalid())
        given(tracer.spanBuilder(anyString())).willReturn(spanBuilder)
    }

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
                            .propertyClass("c"))
            }

        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any())).willReturn(mono { fields })

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectNext(fields)
            .verifyComplete()
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
    }

    @Test
    fun `Should create payment order build successfully with PayPal`() {
        val pspId = "pspId"
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
                            .propertyClass("c"))
            }

        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.PAYPAL)
        given(npgPspApiKeysConfig[any()]).willReturn(Either.right(pspApiKey))
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), anyOrNull()))
            .willReturn(mono { fields })

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, pspId))
            .expectNext(fields)
            .verifyComplete()
        verify(npgPspApiKeysConfig, times(1))[pspId]
        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, pspApiKey, createHostedOrderRequest)
    }

    @Test
    fun `Should get card data successfully`() {
        val cardDataResponse =
            CardDataResponse()
                .bin("123456")
                .lastFourDigits("0000")
                .expiringDate("122030")
                .circuit("MC")

        // prerequisite
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1BuildCardDataGet(any(), any(), any()))
            .willReturn(mono { cardDataResponse })

        // test and assertions
        StepVerifier.create(npgClient.getCardData(sessionId, correlationId))
            .expectNext(cardDataResponse)
            .verifyComplete()
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
        verify(npgWebClient, times(1))
            .pspApiV1BuildCardDataGet(correlationId, defaultApiKey, sessionId)
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication for getCardData`() {
        // prerequisite
        val span = spy(Span.current())
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1BuildCardDataGet(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        500,
                        "statusText",
                        HttpHeaders.EMPTY,
                        objectMapper.writeValueAsBytes(
                            ServerError()
                                .addErrorsItem(ErrorsInner().code("123").description("error"))),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(npgClient.getCardData(sessionId, correlationId))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1BuildCardDataGet(correlationId, defaultApiKey, sessionId)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey

        verify(spanBuilder)
            .setAttribute(eq(NpgClient.NpgTracing.NPG_CORRELATION_ID_ATTRIBUTE_NAME), anyString())
        verify(span).setStatus(eq(StatusCode.ERROR))
        verify(span).setAttribute(eq(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE), eq(500L))
        verify(span)
            .setAttribute(
                eq(NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME), eq(listOf("123")))
    }

    @Test
    fun `Should validate payment successfully`() {
        val stateResponse = StateResponse().url("http://redirectUrl")

        val confirmPaymentRequest = ConfirmPaymentRequest().sessionId(sessionId).amount("0")

        // prerequisite
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1BuildConfirmPaymentPost(any(), any(), any()))
            .willReturn(mono { stateResponse })

        // test and assertions
        StepVerifier.create(npgClient.confirmPayment(confirmPaymentRequest, correlationId))
            .expectNext(stateResponse)
            .verifyComplete()
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
        verify(npgWebClient, times(1))
            .pspApiV1BuildConfirmPaymentPost(
                correlationId,
                defaultApiKey,
                ConfirmPaymentRequest().amount("0").sessionId(sessionId))
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication for validate`() {
        // prerequisite
        val confirmPaymentRequest = ConfirmPaymentRequest().sessionId(sessionId).amount("0")
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1BuildConfirmPaymentPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        500,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(npgClient.confirmPayment(confirmPaymentRequest, correlationId))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1BuildConfirmPaymentPost(correlationId, defaultApiKey, confirmPaymentRequest)
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        500,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 401 during communication`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        401,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 500 from ecommerce-payment-methods`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        500,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 404 from ecommerce-payment-methods`() {
        // prerequisite
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        404,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 400 from ecommerce-payment-methods`() {
        val createHostedOrderRequest = orderBuildRequest(WalletDetailsType.CARDS)
        val span = spy(Span.current())

        // prerequisite
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1OrdersBuildPost(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        400,
                        "statusText",
                        HttpHeaders.EMPTY,
                        objectMapper.writeValueAsBytes(
                            ClientError()
                                .addErrorsItem(ErrorsInner().code("123").description("error"))),
                        StandardCharsets.UTF_8)))

        // test and assertions
        StepVerifier.create(
                npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest, null))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()

        verify(npgWebClient, times(1))
            .pspApiV1OrdersBuildPost(correlationId, defaultApiKey, createHostedOrderRequest)
        verify(npgPspApiKeysConfig, times(1)).defaultApiKey
        verify(spanBuilder)
            .setAttribute(eq(NpgClient.NpgTracing.NPG_CORRELATION_ID_ATTRIBUTE_NAME), anyString())
        verify(span).setStatus(eq(StatusCode.ERROR))
        verify(span).setAttribute(eq(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE), eq(400L))
        verify(span)
            .setAttribute(
                eq(NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME), eq(listOf("123")))
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 204])
    fun `Should return the successful HTTP response code for contract deactivation`(
        statusCode: Int
    ) {
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(Mono.just(ResponseEntity.status(statusCode).build()))

        StepVerifier.create(
                npgClient.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(NpgClient.ContractDeactivationResult.Success(statusCode))
            .verifyComplete()
    }

    @Test
    fun `Should return 400 as successful response when contract is already deactivated`() {
        val span = spy(Span.current())
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        400,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        objectMapper.writeValueAsBytes(
                            ClientError()
                                .addErrorsItem(
                                    ErrorsInner()
                                        .code(NpgClient.NPG_CONTRACT_ALREADY_DEACTIVATED_ERROR_CODE)
                                        .description("Contract already deactivated"))),
                        StandardCharsets.UTF_8)))

        StepVerifier.create(
                npgClient.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(NpgClient.ContractDeactivationResult.Success(400))
            .verifyComplete()

        verify(span, never()).setStatus(StatusCode.ERROR)
        verify(span, never()).setAttribute(eq(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE), any())
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 500])
    fun `Should parse NPG error details once when contract deactivation fails`(statusCode: Int) {
        val errorMapper = spy(ObjectMapper())
        val client = NpgClient(npgWebClient, npgPspApiKeysConfig, tracer, errorMapper)
        val span = spy(Span.current())
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        statusCode,
                        "NPG error",
                        HttpHeaders.EMPTY,
                        objectMapper.writeValueAsBytes(
                            ClientError()
                                .addErrorsItem(ErrorsInner().description("Missing code"))
                                .addErrorsItem(ErrorsInner().code("NPG_ERROR").description("error"))
                                .addErrorsItem(ErrorsInner().code("SECOND_ERROR"))),
                        StandardCharsets.UTF_8)))

        StepVerifier.create(
                client.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(
                NpgClient.ContractDeactivationResult.Failure(
                    WalletContractIdDeactivationErrorType.KO_RESPONSE_RECEIVED,
                    statusCode,
                    "NPG_ERROR"))
            .verifyComplete()

        when (statusCode) {
            400 -> verify(errorMapper).readValue(any<ByteArray>(), eq(ClientError::class.java))
            500 -> verify(errorMapper).readValue(any<ByteArray>(), eq(ServerError::class.java))
        }
        verify(span).setStatus(StatusCode.ERROR)
        verify(span).setAttribute(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE, statusCode.toLong())
        verify(span)
            .setAttribute(
                NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME,
                listOf("NPG_ERROR", "SECOND_ERROR"))
    }

    @ParameterizedTest
    @CsvSource("400|{}", "500|not JSON", "500|{\"errors\":[]}", delimiter = '|')
    fun `Should preserve HTTP failure for invalid or absent deactivation error codes`(
        statusCode: Int,
        body: String
    ) {
        val span = spy(Span.current())
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        statusCode,
                        "NPG error",
                        HttpHeaders.EMPTY,
                        body.toByteArray(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8)))

        StepVerifier.create(
                npgClient.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(
                NpgClient.ContractDeactivationResult.Failure(
                    WalletContractIdDeactivationErrorType.KO_RESPONSE_RECEIVED, statusCode, ""))
            .verifyComplete()

        verify(span).setStatus(StatusCode.ERROR)
        verify(span).setAttribute(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE, statusCode.toLong())
        verify(span).setAttribute(NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME, emptyList())
    }

    @Test
    fun `Should not treat already deactivated code as success for server errors`() {
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        500,
                        "NPG error",
                        HttpHeaders.EMPTY,
                        objectMapper.writeValueAsBytes(
                            ServerError()
                                .addErrorsItem(
                                    ErrorsInner()
                                        .code(
                                            NpgClient
                                                .NPG_CONTRACT_ALREADY_DEACTIVATED_ERROR_CODE))),
                        StandardCharsets.UTF_8)))

        StepVerifier.create(
                npgClient.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(
                NpgClient.ContractDeactivationResult.Failure(
                    WalletContractIdDeactivationErrorType.KO_RESPONSE_RECEIVED,
                    500,
                    NpgClient.NPG_CONTRACT_ALREADY_DEACTIVATED_ERROR_CODE))
            .verifyComplete()
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 500])
    fun `Should preserve bad gateway mapping and tracing for malformed error responses`(
        statusCode: Int
    ) {
        val span = spy(Span.current())
        given(spanBuilder.startSpan()).willReturn(span)
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(npgWebClient.pspApiV1BuildCardDataGet(any(), any(), any()))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        statusCode,
                        "NPG error",
                        HttpHeaders.EMPTY,
                        "not JSON".toByteArray(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8)))

        StepVerifier.create(npgClient.getCardData(sessionId, correlationId))
            .expectErrorMatches {
                it is NpgClientException &&
                    it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY &&
                    it.message ==
                        "Invalid error response from NPG with status code ${HttpStatus.valueOf(statusCode)}"
            }
            .verify()

        verify(span).setStatus(StatusCode.ERROR)
        verify(span, never()).setAttribute(eq(NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE), any())
        verify(span, never())
            .setAttribute(eq(NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME), any())
    }

    @Test
    fun `Should return communication error details when contract deactivation cannot reach NPG`() {
        given(npgPspApiKeysConfig.defaultApiKey).willReturn(defaultApiKey)
        given(
                npgWebClient.pspApiV1ContractsContractIdDeactivationPostWithHttpInfo(
                    any(), any(), any()))
            .willReturn(Mono.error(IllegalStateException("connection error")))

        StepVerifier.create(
                npgClient.deactivateContract(ContractId("contractId"), correlationId, null))
            .expectNext(
                NpgClient.ContractDeactivationResult.Failure(
                    WalletContractIdDeactivationErrorType.COMMUNICATION_ERROR, 0, ""))
            .verifyComplete()
    }
}
