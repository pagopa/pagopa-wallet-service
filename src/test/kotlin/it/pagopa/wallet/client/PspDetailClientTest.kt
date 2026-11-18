package it.pagopa.wallet.client

import it.pagopa.generated.ecommerce.paymentmethods.v2.api.PaymentMethodsApi
import it.pagopa.generated.ecommerce.paymentmethodshandler.api.PaymentMethodsHandlerApi
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_APM
import it.pagopa.wallet.config.properties.PaymentMethodsHandlerConfigProperties
import it.pagopa.wallet.exception.RestApiException
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.test.test

class PspDetailClientTest {

    private val paymentMethodsV2Api: PaymentMethodsApi = mock()
    private val paymentMethodsHandlerApi: PaymentMethodsHandlerApi = mock()

    @BeforeEach
    fun setup() {
        reset(paymentMethodsV2Api, paymentMethodsHandlerApi)
    }

    private fun client(enabled: Boolean) =
        PspDetailClient(
            paymentMethodsV2Api,
            paymentMethodsHandlerApi,
            PaymentMethodsHandlerConfigProperties(
                uri = "http://localhost",
                readTimeout = 1000,
                connectionTimeout = 1000,
                apiKey = "apiKey",
                enabled = enabled))

    // ---------------------------------------------------------------------------------------------
    // Legacy v2 branch (enabled = false)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `v2 branch - Calculate fees with stub request must return the psp bundle`() {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(generateV2FeesResponse().toMono())

        client(enabled = false)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectNextMatches { it.idPsp == PSP_ID && it.pspBusinessName == PSP_BUSINESS_NAME }
            .verifyComplete()

        val txIdCaptor = argumentCaptor<String>()
        verify(paymentMethodsV2Api)
            .calculateFees(
                eq(PAYMENT_METHOD_ID_APM.value.toString()), txIdCaptor.capture(), any(), eq(1000))
        assertEquals("", txIdCaptor.firstValue)
        verify(paymentMethodsHandlerApi, org.mockito.kotlin.never())
            .calculateFees(any(), any(), any(), any(), any())
    }

    @Test
    fun `v2 branch - Getting psp details for non existing psp must return an empty mono`() {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(generateV2FeesResponse().toMono())

        client(enabled = false)
            .getPspDetails("nonExistingPsp", PAYMENT_METHOD_ID_APM)
            .test()
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `v2 branch - Should return error when payment-methods request fails`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(Mono.error(response))

        client(enabled = false)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `v2 branch - Should return error when payment-methods request directly throws an exception`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }.willThrow(response)

        client(enabled = false)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    // ---------------------------------------------------------------------------------------------
    // Handler branch (enabled = true)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `handler branch - Calculate fees with stub request must return the mapped psp bundle`() {
        given { paymentMethodsHandlerApi.calculateFees(any(), any(), any(), any(), anyOrNull()) }
            .willReturn(generateHandlerFeesResponse().toMono())

        client(enabled = true)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectNextMatches { it.idPsp == PSP_ID && it.pspBusinessName == PSP_BUSINESS_NAME }
            .verifyComplete()

        val idCaptor = argumentCaptor<String>()
        val xClientIdCaptor = argumentCaptor<String>()
        verify(paymentMethodsHandlerApi)
            .calculateFees(idCaptor.capture(), xClientIdCaptor.capture(), any(), eq(1000), isNull())
        assertEquals(PAYMENT_METHOD_ID_APM.value.toString(), idCaptor.firstValue)
        assertEquals("IO", xClientIdCaptor.firstValue)
        verify(paymentMethodsV2Api, org.mockito.kotlin.never())
            .calculateFees(any(), any(), any(), any())
    }

    @Test
    fun `handler branch - Getting psp details for non existing psp must return an empty mono`() {
        given { paymentMethodsHandlerApi.calculateFees(any(), any(), any(), any(), anyOrNull()) }
            .willReturn(generateHandlerFeesResponse().toMono())

        client(enabled = true)
            .getPspDetails("nonExistingPsp", PAYMENT_METHOD_ID_APM)
            .test()
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `handler branch - Should return error when request fails`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsHandlerApi.calculateFees(any(), any(), any(), any(), anyOrNull()) }
            .willReturn(Mono.error(response))

        client(enabled = true)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `handler branch - Should return error when request directly throws an exception`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsHandlerApi.calculateFees(any(), any(), any(), any(), anyOrNull()) }
            .willThrow(response)

        client(enabled = true)
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    @ParameterizedTest
    @MethodSource("paymentMethodStatusMappings")
    fun `handler branch - Should map handler payment method status into v2 status`(
        handlerStatus:
            it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse.PaymentMethodStatusEnum?,
        expectedV2Status: it.pagopa.generated.ecommerce.paymentmethods.v2.model.PaymentMethodStatus?
    ) {
        given { paymentMethodsHandlerApi.calculateFees(any(), any(), any(), any(), anyOrNull()) }
            .willReturn(generateHandlerFeesResponse(handlerStatus).toMono())

        client(enabled = true)
            .getPspList(PAYMENT_METHOD_ID_APM)
            .test()
            .expectNextMatches { it.paymentMethodStatus == expectedV2Status }
            .verifyComplete()
    }

    companion object {
        private const val PSP_ID = "pspId"
        private const val PSP_BUSINESS_NAME = "pspBusinessName"

        private fun generateV2FeesResponse() =
            it.pagopa.generated.ecommerce.paymentmethods.v2.model
                .CalculateFeeResponse()
                .addBundlesItem(
                    it.pagopa.generated.ecommerce.paymentmethods.v2.model
                        .Bundle()
                        .idPsp(PSP_ID)
                        .pspBusinessName(PSP_BUSINESS_NAME))

        private fun generateHandlerFeesResponse(
            status:
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse.PaymentMethodStatusEnum? =
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                    .PaymentMethodStatusEnum
                    .ENABLED
        ) =
            it.pagopa.generated.ecommerce.paymentmethodshandler.model
                .CalculateFeeResponse()
                .paymentMethodStatus(status)
                .addBundlesItem(
                    it.pagopa.generated.ecommerce.paymentmethodshandler.model
                        .Bundle()
                        .idPsp(PSP_ID)
                        .pspBusinessName(PSP_BUSINESS_NAME))

        @JvmStatic
        fun paymentMethodStatusMappings(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                        .PaymentMethodStatusEnum
                        .ENABLED,
                    it.pagopa.generated.ecommerce.paymentmethods.v2.model.PaymentMethodStatus
                        .ENABLED),
                Arguments.of(
                    it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                        .PaymentMethodStatusEnum
                        .DISABLED,
                    it.pagopa.generated.ecommerce.paymentmethods.v2.model.PaymentMethodStatus
                        .DISABLED),
                Arguments.of(
                    it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                        .PaymentMethodStatusEnum
                        .MAINTENANCE,
                    null),
                Arguments.of(null, null))

        @JvmStatic
        fun errorResponses(): Stream<Arguments> =
            Arrays.stream(org.springframework.http.HttpStatus.values())
                .filter { it.isError }
                .map { WebClientResponseException(it.value(), it.reasonPhrase, null, null, null) }
                .map { Arguments.of(it) }
    }
}
