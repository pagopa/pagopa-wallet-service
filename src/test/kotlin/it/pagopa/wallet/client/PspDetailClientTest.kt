package it.pagopa.wallet.client

import it.pagopa.generated.ecommerce.paymentmethods.v2.api.PaymentMethodsApi
import it.pagopa.generated.ecommerce.paymentmethods.v2.model.Bundle
import it.pagopa.generated.ecommerce.paymentmethods.v2.model.CalculateFeeResponse
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_APM
import it.pagopa.wallet.exception.RestApiException
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.test.test

class PspDetailClientTest {

    private val paymentMethodsV2Api: PaymentMethodsApi = mock()
    lateinit var pspDetailClient: PspDetailClient

    @BeforeEach
    fun setup() {
        reset(paymentMethodsV2Api)
        pspDetailClient = PspDetailClient(paymentMethodsV2Api)
    }

    @Test
    fun `Calculate fees with stub request must return the psp bundle`() {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(generateFeesResponse().toMono())

        pspDetailClient
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectNextMatches { it.idPsp == PSP_ID && it.pspBusinessName == PSP_BUSINESS_NAME }
            .verifyComplete()
    }

    @Test
    fun `Getting psp details for non existing psp must return an empty mono`() {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(generateFeesResponse().toMono())

        pspDetailClient
            .getPspDetails("nonExistingPsp", PAYMENT_METHOD_ID_APM)
            .test()
            .verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `Should return error when payment-methods request fails`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }
            .willReturn(Mono.error(response))

        pspDetailClient
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `Should return error when payment-methods request fails directly throws an exception`(
        response: WebClientResponseException
    ) {
        given { paymentMethodsV2Api.calculateFees(any(), any(), any(), any()) }.willThrow(response)

        pspDetailClient
            .getPspDetails(PSP_ID, PAYMENT_METHOD_ID_APM)
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    companion object {
        private const val PSP_ID = "pspId"
        private const val PSP_BUSINESS_NAME = "pspBusinessName"
        private fun generateFeesResponse() =
            CalculateFeeResponse()
                .addBundlesItem(Bundle().idPsp(PSP_ID).pspBusinessName(PSP_BUSINESS_NAME))

        @JvmStatic
        fun errorResponses(): Stream<Arguments> =
            Arrays.stream(org.springframework.http.HttpStatus.values())
                .filter { it.isError }
                .map { WebClientResponseException(it.value(), it.reasonPhrase, null, null, null) }
                .map { Arguments.of(it) }
    }
}
