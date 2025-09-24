package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodResponse
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_APM
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID_CARDS
import it.pagopa.wallet.WalletTestUtils.getValidAPMPaymentMethod
import it.pagopa.wallet.WalletTestUtils.getValidCardsPaymentMethod
import it.pagopa.wallet.WalletTestUtils.toPaymentMethodHandlerResponse
import it.pagopa.wallet.WalletTestUtils.toPaymentMethodInfo
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.EcommercePaymentMethodsHandlerClient
import it.pagopa.wallet.config.properties.PaymentMethodsHandlerConfigProperties
import it.pagopa.wallet.domain.methods.PaymentMethodInfo
import it.pagopa.wallet.exception.EcommercePaymentMethodException
import it.pagopa.wallet.repositories.PaymentMethodsInfoTemplateWrapper
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

    private val paymentMethodsRedisTemplate: PaymentMethodsInfoTemplateWrapper = mock()
    private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient = mock()
    private val ecommercePaymentMethodsHandlerClient: EcommercePaymentMethodsHandlerClient = mock()
    private val paymentMethodCacheSaveSink: Sinks.Many<PaymentMethodInfo> = mock()

    private val paymentMethodsServiceWithHandlerDisabled: PaymentMethodsService =
        PaymentMethodsService(
            paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
            ecommercePaymentMethodsClient = ecommercePaymentMethodsClient,
            paymentMethodsHandlerConfigProperties =
                PaymentMethodsHandlerConfigProperties(
                    uri = "http://localhost",
                    readTimeout = 1000,
                    connectionTimeout = 1000,
                    apiKey = "api-key",
                    enabled = false),
            ecommercePaymentMethodsHandlerClient = ecommercePaymentMethodsHandlerClient)

    private val paymentMethodsServiceWithHandlerEnabled: PaymentMethodsService =
        PaymentMethodsService(
            paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
            ecommercePaymentMethodsClient = ecommercePaymentMethodsClient,
            paymentMethodsHandlerConfigProperties =
                PaymentMethodsHandlerConfigProperties(
                    uri = "http://localhost",
                    readTimeout = 1000,
                    connectionTimeout = 1000,
                    apiKey = "api-key",
                    enabled = true),
            ecommercePaymentMethodsHandlerClient = ecommercePaymentMethodsHandlerClient)

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
    fun `should find payment method from cache (payment method handler disabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }
            .willReturn(Mono.just(paymentMethodResponse.toPaymentMethodInfo()))

        paymentMethodsServiceWithHandlerDisabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerDisabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should not find payment method from cache and retrieve it by api client (payment method handler disabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse })

        paymentMethodsServiceWithHandlerDisabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerDisabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(1))
            .save(paymentMethodResponse.toPaymentMethodInfo())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should thrown error when fail payment-methods api call (payment method handler disabled)`(
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

        paymentMethodsServiceWithHandlerDisabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerDisabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectError(EcommercePaymentMethodException::class.java)
            .verify()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail cache update (payment method handler disabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse })
        given { paymentMethodsRedisTemplate.save(any()) }
            .willThrow(RuntimeException("Error during redis save"))

        paymentMethodsServiceWithHandlerDisabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerDisabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(1))
            .save(paymentMethodResponse.toPaymentMethodInfo())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail sink emit (payment method handler disabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        val paymentMethodsServiceMockSink =
            PaymentMethodsService(
                paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
                ecommercePaymentMethodsClient = ecommercePaymentMethodsClient,
                paymentMethodCacheSaveSink = paymentMethodCacheSaveSink,
                paymentMethodsHandlerConfigProperties =
                    PaymentMethodsHandlerConfigProperties(
                        uri = "http://localhost",
                        readTimeout = 1000,
                        connectionTimeout = 1000,
                        apiKey = "api-key",
                        enabled = false),
                ecommercePaymentMethodsHandlerClient = ecommercePaymentMethodsHandlerClient)
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
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodCacheSaveSink, times(1))
            .tryEmitNext(paymentMethodResponse.toPaymentMethodInfo())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should find payment method from cache (payment method handler enabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }
            .willReturn(Mono.just(paymentMethodResponse.toPaymentMethodInfo()))

        paymentMethodsServiceWithHandlerEnabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerEnabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should not find payment method from cache and retrieve it by api client (payment method handler enabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsHandlerClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse.toPaymentMethodHandlerResponse() })

        paymentMethodsServiceWithHandlerEnabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerEnabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(paymentMethodsRedisTemplate, timeout(100).times(1))
            .save(paymentMethodResponse.toPaymentMethodInfo())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should thrown error when fail payment-methods api call (payment method handler enabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsHandlerClient.getPaymentMethodById(any()) }
            .willReturn(
                Mono.error(
                    EcommercePaymentMethodException(
                        "EcommercePaymentMethods - Bad request", HttpStatus.BAD_GATEWAY)))

        paymentMethodsServiceWithHandlerEnabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerEnabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectError(EcommercePaymentMethodException::class.java)
            .verify()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail cache update (payment method handler enabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsHandlerClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse.toPaymentMethodHandlerResponse() })
        given { paymentMethodsRedisTemplate.save(any()) }
            .willThrow(RuntimeException("Error during redis save"))

        paymentMethodsServiceWithHandlerEnabled.subscribePaymentMethodCacheSaveSink()

        // Test
        paymentMethodsServiceWithHandlerEnabled
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodsRedisTemplate, timeout(100).times(1))
            .save(paymentMethodResponse.toPaymentMethodInfo())
    }

    @ParameterizedTest
    @MethodSource("paymentMethodsSource")
    fun `should return payment method even if fail sink emit (payment method handler enabled)`(
        paymentMethodId: String,
        paymentMethodResponse: PaymentMethodResponse
    ) {
        val paymentMethodsServiceMockSink =
            PaymentMethodsService(
                paymentMethodsRedisTemplate = paymentMethodsRedisTemplate,
                ecommercePaymentMethodsClient = ecommercePaymentMethodsClient,
                paymentMethodCacheSaveSink = paymentMethodCacheSaveSink,
                paymentMethodsHandlerConfigProperties =
                    PaymentMethodsHandlerConfigProperties(
                        uri = "http://localhost",
                        readTimeout = 1000,
                        connectionTimeout = 1000,
                        apiKey = "api-key",
                        enabled = true),
                ecommercePaymentMethodsHandlerClient = ecommercePaymentMethodsHandlerClient)
        // pre-requisites
        given { paymentMethodsRedisTemplate.findById(any()) }.willReturn(Mono.empty())
        given { ecommercePaymentMethodsHandlerClient.getPaymentMethodById(any()) }
            .willReturn(mono { paymentMethodResponse.toPaymentMethodHandlerResponse() })
        given { paymentMethodCacheSaveSink.tryEmitNext(any()) }
            .willThrow(RuntimeException("Error during sink emit"))

        // Test
        paymentMethodsServiceMockSink
            .getPaymentMethodById(paymentMethodId)
            .test()
            .expectNext(paymentMethodResponse.toPaymentMethodInfo())
            .verifyComplete()

        // verifications
        verify(paymentMethodsRedisTemplate, times(1)).findById(paymentMethodId)
        verify(ecommercePaymentMethodsClient, times(0)).getPaymentMethodById(any())
        verify(ecommercePaymentMethodsHandlerClient, times(1)).getPaymentMethodById(paymentMethodId)
        verify(paymentMethodCacheSaveSink, times(1))
            .tryEmitNext(paymentMethodResponse.toPaymentMethodInfo())
        verify(paymentMethodsRedisTemplate, timeout(100).times(0)).save(any())
    }
}
