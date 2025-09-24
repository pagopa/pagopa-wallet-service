package it.pagopa.wallet.client

import it.pagopa.generated.ecommerce.paymentmethodshandler.api.PaymentMethodsHandlerApi
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.toPaymentMethodHandlerResponse
import it.pagopa.wallet.exception.EcommercePaymentMethodException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier

class EcommercePaymentMethodHandlerClientTest {

    private val paymentMethodsApi: PaymentMethodsHandlerApi = mock()
    private val paymentMethodsClient = EcommercePaymentMethodsHandlerClient(paymentMethodsApi)

    @Test
    fun `Should retrieve payment method by id successfully`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        val paymentMethod =
            WalletTestUtils.getValidCardsPaymentMethod().toPaymentMethodHandlerResponse()
        Hooks.onOperatorDebug()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willReturn(mono { paymentMethod })

        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectNext(paymentMethod)
            .verifyComplete()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_GATEWAY error for exception during communication`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willThrow(
                WebClientResponseException.create(
                    500, "statusText", HttpHeaders.EMPTY, ByteArray(0), StandardCharsets.UTF_8))
        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with INTERNAL_SERVER_ERROR error for 401 from ecommerce-payment-methods`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willThrow(
                WebClientResponseException.create(
                    401, "statusText", HttpHeaders.EMPTY, ByteArray(0), StandardCharsets.UTF_8))
        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_GATEWAY error for 500 from ecommerce-payment-methods`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willThrow(
                WebClientResponseException.create(
                    500, "statusText", HttpHeaders.EMPTY, ByteArray(0), StandardCharsets.UTF_8))
        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_GATEWAY error for 404 from ecommerce-payment-methods`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willThrow(
                WebClientResponseException.create(
                    404, "statusText", HttpHeaders.EMPTY, ByteArray(0), StandardCharsets.UTF_8))
        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_GATEWAY error for 400 from ecommerce-payment-methods`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willThrow(
                WebClientResponseException.create(
                    400, "statusText", HttpHeaders.EMPTY, ByteArray(0), StandardCharsets.UTF_8))
        Hooks.onOperatorDebug()
        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_REQUEST error for disabled payment method`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        val paymentMethod =
            WalletTestUtils.getDisabledCardsPaymentMethod().toPaymentMethodHandlerResponse()

        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willReturn(mono { paymentMethod })

        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    @Test
    fun `Should map payment method service error response to EcommercePaymentMethodsClientException with BAD_REQUEST error for invalid payment method`() {
        val paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS.value.toString()
        val paymentMethod =
            WalletTestUtils.getInvalidPaymentMethod().toPaymentMethodHandlerResponse()

        // prerequisite
        given(paymentMethodsApi.getPaymentMethod(any(), anyOrNull()))
            .willReturn(mono { paymentMethod })

        // test and assertions
        StepVerifier.create(paymentMethodsClient.getPaymentMethodById(paymentMethodId))
            .expectErrorMatches {
                it as EcommercePaymentMethodException
                it.toRestException().httpStatus == HttpStatus.BAD_REQUEST
            }
            .verify()
    }
}
