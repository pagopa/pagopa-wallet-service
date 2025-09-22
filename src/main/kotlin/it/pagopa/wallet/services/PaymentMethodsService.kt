package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodStatus
import it.pagopa.generated.ecommerce.paymentmethodshandler.model.PaymentMethodResponse
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.EcommercePaymentMethodsHandlerClient
import it.pagopa.wallet.config.properties.PaymentMethodsHandlerConfigProperties
import it.pagopa.wallet.domain.methods.PaymentMethodInfo
import it.pagopa.wallet.repositories.PaymentMethodsInfoTemplateWrapper
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
    @Autowired private val paymentMethodsRedisTemplate: PaymentMethodsInfoTemplateWrapper,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
    @Autowired
    private val ecommercePaymentMethodsHandlerClient: EcommercePaymentMethodsHandlerClient,
    @Autowired
    private val paymentMethodsHandlerConfigProperties: PaymentMethodsHandlerConfigProperties,
    private val paymentMethodCacheSaveSink: Sinks.Many<PaymentMethodInfo> =
        Sinks.many().unicast().onBackpressureBuffer()
) : ApplicationListener<ApplicationReadyEvent> {

    /*
     * Logger instance
     */
    var logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        subscribePaymentMethodCacheSaveSink()
    }

    fun getPaymentMethodById(paymentMethodId: String): Mono<PaymentMethodInfo> =
        paymentMethodsRedisTemplate
            .findById(paymentMethodId)
            .doFirst {
                logger.debug("Try to retrieve payment method from cache: [$paymentMethodId]")
            }
            .doOnNext { logger.info("Cache hit for payment method with id: [$paymentMethodId]") }
            .switchIfEmpty {
                logger.info("Cache miss for payment method: [$paymentMethodId]")
                mono { paymentMethodsHandlerConfigProperties.enabled }
                    .filter { enabled -> enabled }
                    .flatMap { retrievePaymentMethodByHandlerApi(paymentMethodId) }
                    .switchIfEmpty { retrievePaymentMethodByApi(paymentMethodId) }
            }

    private fun retrievePaymentMethodByApi(paymentMethodId: String): Mono<PaymentMethodInfo> =
        ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId)
            .map {
                PaymentMethodInfo(
                    id = it.id,
                    enabled = it.status == PaymentMethodStatus.ENABLED,
                    paymentTypeCode = it.paymentTypeCode)
            }
            .flatMap { emitPaymentMethodCacheSaveSink(it) }
            .doOnError { logger.error("Error during call to payment method: [$paymentMethodId]") }

    private fun retrievePaymentMethodByHandlerApi(
        paymentMethodId: String
    ): Mono<PaymentMethodInfo> =
        ecommercePaymentMethodsHandlerClient
            .getPaymentMethodById(paymentMethodId)
            .map {
                PaymentMethodInfo(
                    id = it.id,
                    enabled = it.status == PaymentMethodResponse.StatusEnum.ENABLED,
                    paymentTypeCode = it.paymentTypeCode.toString())
            }
            .flatMap { emitPaymentMethodCacheSaveSink(it) }
            .doOnError {
                logger.error(
                    "Error during call to payment method handler for id: [$paymentMethodId]")
            }

    private fun emitPaymentMethodCacheSaveSink(paymentMethodInfo: PaymentMethodInfo) =
        mono { paymentMethodCacheSaveSink.tryEmitNext(paymentMethodInfo) }
            .doOnNext { logger.debug("Emit paymentMethodCacheSaveSink result: {}", it) }
            .doOnError { logger.error("Exception while emitting cache save: ", it) }
            .map { paymentMethodInfo }
            .onErrorReturn(paymentMethodInfo)

    fun subscribePaymentMethodCacheSaveSink(): Disposable =
        paymentMethodCacheSaveSink
            .asFlux()
            .flatMap { paymentMethodInfo ->
                logger.debug("Save payment method into cache: [${paymentMethodInfo.id}]")
                paymentMethodsRedisTemplate.save(paymentMethodInfo).doOnError {
                    logger.error(
                        "Error saving payment method into cache: [${paymentMethodInfo.id}]")
                }
            }
            .subscribe()
}
