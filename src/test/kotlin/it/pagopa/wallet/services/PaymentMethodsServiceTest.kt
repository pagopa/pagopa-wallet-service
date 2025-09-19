package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodResponse
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_APM
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.getValidAPMPaymentMethod
import it.pagopa.wallet.WalletTestUtils.getValidCardsPaymentMethod
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.exception.EcommercePaymentMethodException
import it.pagopa.wallet.repositories.PaymentMethodsTemplateWrapper
import java.util.stream.Stream
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kotlin.test.test

class PaymentMethodsServiceTest {

    private val paymentMethodsRedisTemplate: PaymentMethodsTemplateWrapper = mock()
    private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient = mock()
    private val paymentMethodCacheSaveSink: Sinks.Many<PaymentMethodResponse> = mock()

    private val paymentMethodsService: PaymentMethodsService =
        PaymentMethodsService(
            paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
            ecommercePaymentMethodsClient = ecommercePaymentMethodsClient)
    private val paymentMethodsServiceMockSink: PaymentMethodsService =
        PaymentMethodsService(
            paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
            ecommercePaymentMethodsClient = ecommercePaymentMethodsClient,
            paymentMethodCacheSaveSink = paymentMethodCacheSaveSink)

    companion object {

        @JvmStatic
        fun paymentMethodsSource(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    PAYMENT_METHOD_ID_CARDS.value.toString(), getValidCardsPaymentMethod()),
                Arguments.of(PAYMENT_METHOD_ID_APM.value.toString(), getValidAPMPaymentMethod()))
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should find payment method from cache`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }
            .willReturn(Mono.just(paymentMethodResponse))

        paymentMethodsService.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsService
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse)
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should not find payment method from cache and retrieve it by api client`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse })

        paymentMethodsService.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsService
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse)
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodsRedisTemplate, timeout(100).times(1)).save(paymentMethodResponse)
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should thrown error when fail payment-methods api call`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(
                Mono.error(
                    EcommercePaymentMethodException(
                        "EcommercePaymentMethods - Bad request", HttpStatus.BAD_GATEWAY)))

        paymentMethodsService.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsService
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectError(EcommercePaymentMethodException::class.java)
            .verify()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail cache update`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse })
        given { paymentMethodsRedisTemplate.save(any()) }
            .willThrow(RuntimeException("Error during redis save"))

        paymentMethodsService.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsService
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse)
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodsRedisTemplate, timeout(100).times(1)).save(paymentMethodResponse)
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail sink emit`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse })
        given { paymentMethodCacheSaveSink.tryEmitNext(any()) }
            .willThrow(RuntimeException("Error during sink emit"))

        // Test
        paymentMethodsServiceMockSink
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse)
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodCacheSaveSink, times(1)).tryEmitNext(paymentMethodResponse)
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }
}
