package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.repositories.PaymentMethodsTemplateWrapper
import kotlinx.coroutines.Dispatchers
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
import reactor.core.scheduler.Schedulers
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
        mono(Dispatchers.IO) {
                logger.debug("Try to retrieve payment method from cache: [$paymentMethodId]")
                paymentMethodsRedisTemplate.findById(paymentMethodId)
            }
            .doOnNext { logger.info("Cache hit for payment method with id: [$paymentMethodId]") }
            .switchIfEmpty {
                logger.info("Cache miss for payment method: [$paymentMethodId]")
                retrievePaymentMethodByApi(paymentMethodId)
            }

    private fun retrievePaymentMethodByApi(paymentMethodId: String): Mono<PaymentMethodResponse> =
        ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId)
            .doOnSuccess {
                val emitResult = paymentMethodCacheSaveSink.tryEmitNext(it)
                logger.debug("Emit paymentMethodCacheSaveSink result: {}", emitResult)
            }
            .doOnError { logger.error("Error during call to payment method: [$paymentMethodId]") }

    fun subscribePaymentMethodCacheSaveSink(): Disposable =
        paymentMethodCacheSaveSink
            .asFlux()
            .flatMap { paymentMethodResponse ->
                mono(Dispatchers.IO) {
                        logger.debug(
                            "Save payment method into cache: [${paymentMethodResponse.id}]"
                        )
                        paymentMethodsRedisTemplate.save(paymentMethodResponse)
                    }
                    .doOnError {
                        logger.error(
                            "Error saving payment method into cache: [${paymentMethodResponse.id}]"
                        )
                    }
            }
            .subscribeOn(Schedulers.parallel())
            .subscribe()
}
