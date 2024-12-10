package it.pagopa.wallet.services

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.repositories.PaymentMethod
import it.pagopa.wallet.repositories.PaymentMethodsTemplateWrapper
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
        mono {
                logger.info("Try to retrieve payment method from cache: [$paymentMethodId]")
                paymentMethodsRedisTemplate.findById(paymentMethodId)
            }
            .map {
                logger.info("Cache hit for payment method with id: [$paymentMethodId]")
                PaymentMethodResponse().apply {
                    id = it.id
                    name = it.name
                    description = it.description
                    asset = it.asset
                    status = it.status
                    paymentTypeCode = it.paymentTypeCode
                    methodManagement = it.methodManagement
                    ranges = it.ranges
                    brandAssets = it.brandAssets
                }
            }
            .switchIfEmpty {
                logger.info("Not found payment method on cache: [$paymentMethodId]")
                retrievePaymentMethodById(paymentMethodId)
            }

    fun retrievePaymentMethodById(paymentMethodId: String): Mono<PaymentMethodResponse> =
        ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId)
            .doOnSuccess {
                logger.info("Save payment method into cache: [$paymentMethodId]")
                paymentMethodsRedisTemplate.save(
                    PaymentMethod(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        asset = it.asset,
                        status = it.status,
                        paymentTypeCode = it.paymentTypeCode,
                        methodManagement = it.methodManagement,
                        ranges = it.ranges,
                        brandAssets = it.brandAssets
                    )
                )
            }
            .doOnError { logger.error("Error during call to payment method: [$paymentMethodId]") }
}
