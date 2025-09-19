package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodResponse
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.repositories.PaymentMethodsTemplateWrapper
import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kotlin.core.publisher.switchIfEmpty

@Service
class PaymentMethodsService(
    @Autowired private val paymentMethodsRedisTemplate: PaymentMethodsTemplateWrapper,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
    private val paymentMethodCacheSaveSink: Sinks.Many<PaymentMethodResponse> =
        Sinks.many().unicast().onBackpressureBuffer()
) : ApplicationListener<ApplicationReadyEvent> {

    /*
     * Logger instance
     */
    var logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        subscribePaymentMethodCacheSaveSink()
    }

    fun getPaymentMethodById(paymentMethodId: String): Mono<PaymentMethodResponse> =
            paymentMethodsRedisTemplate.findById(paymentMethodId)
            .doFirst { logger.debug("Try to retrieve payment method from cache: [$paymentMethodId]") }
            .doOnNext { logger.info("Cache hit for payment method with id: [$paymentMethodId]") }
            .switchIfEmpty {
                logger.info("Cache miss for payment method: [$paymentMethodId]")
                retrievePaymentMethodByApi(paymentMethodId)
            }

    private fun retrievePaymentMethodByApi(paymentMethodId: String): Mono<PaymentMethodResponse> =
        ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId)
            .flatMap { emitPaymentMethodCacheSaveSink(it) }
            .doOnError { logger.error("Error during call to payment method: [$paymentMethodId]") }

    private fun emitPaymentMethodCacheSaveSink(paymentMethodResponse: PaymentMethodResponse) =
        mono { paymentMethodCacheSaveSink.tryEmitNext(paymentMethodResponse) }
            .doOnNext { logger.debug("Emit paymentMethodCacheSaveSink result: {}", it) }
            .doOnError { logger.error("Exception while emitting cache save: ", it) }
            .map { paymentMethodResponse }
            .onErrorReturn(paymentMethodResponse)

    fun subscribePaymentMethodCacheSaveSink(): Disposable =
        paymentMethodCacheSaveSink
            .asFlux()
            .flatMap { paymentMethodResponse ->
                logger.debug("Save payment method into cache: [${paymentMethodResponse.id}]")
                paymentMethodsRedisTemplate.save(paymentMethodResponse)
                    .doOnError {
                        logger.error(
                            "Error saving payment method into cache: [${paymentMethodResponse.id}]"
                        )
                    }
            }
            .subscribe()
}
