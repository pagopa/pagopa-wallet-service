package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.RedisTemplate

class PaymentMethodsTemplateWrapper
/**
 * Primary constructor
 *
 * @param redisTemplate inner redis template
 * @param ttl time to live for keys
 */
(redisTemplate: RedisTemplate<String, PaymentMethod>, ttl: Duration) :
    RedisTemplateWrapper<PaymentMethod>(redisTemplate = redisTemplate, "payment-methods", ttl) {
    override fun getKeyFromEntity(value: PaymentMethod): String = value.id
}
