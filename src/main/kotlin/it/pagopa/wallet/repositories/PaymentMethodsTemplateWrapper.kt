package it.pagopa.wallet.repositories

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodResponse
import org.springframework.data.redis.core.ReactiveRedisTemplate
import java.time.Duration

class PaymentMethodsTemplateWrapper
/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(reactiveRedisTemplate: ReactiveRedisTemplate<String, PaymentMethodResponse>, ttl: Duration) :
    ReactiveRedisTemplateWrapper<PaymentMethodResponse>(
        reactiveRedisTemplate = reactiveRedisTemplate,
        "wallet-service:payment-methods",
        ttl
    ) {
    override fun getKeyFromEntity(value: PaymentMethodResponse): String = value.id
}
