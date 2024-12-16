package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.repositories.PaymentMethodsTemplateWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

@Service
class PaymentMethodsService(
    @Autowired private val paymentMethodsRedisTemplate: PaymentMethodsTemplateWrapper,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
) {

    /*
     * Logger instance
     */
    var logger: Logger = LoggerFactory.getLogger(this.javaClass)

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
            .flatMap { savePaymentMethodIntoCache(it) }
            .doOnError { logger.error("Error during call to payment method: [$paymentMethodId]") }

    private fun savePaymentMethodIntoCache(paymentMethodResponse: PaymentMethodResponse) =
        mono(Dispatchers.IO) {
                logger.debug("Save payment method into cache: [${paymentMethodResponse.id}]")
                paymentMethodsRedisTemplate.save(paymentMethodResponse)
            }
            .onErrorResume {
                logger.error(
                    "Error saving payment method into cache: [${paymentMethodResponse.id}]"
                )
                Mono.just(Unit)
            }
            .map { paymentMethodResponse }
}
