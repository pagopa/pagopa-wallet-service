package it.pagopa.wallet.repositories

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import java.time.Duration
import org.springframework.data.redis.core.RedisTemplate

class PaymentMethodsTemplateWrapper
/**
 * Primary constructor
 *
 * @param redisTemplate inner redis template
 * @param ttl time to live for keys
 */
(redisTemplate: RedisTemplate<String, PaymentMethodResponse>, ttl: Duration) :
    RedisTemplateWrapper<PaymentMethodResponse>(
        redisTemplate = redisTemplate,
        "payment-methods",
        ttl
    ) {
    override fun getKeyFromEntity(value: PaymentMethodResponse): String = value.id
}
