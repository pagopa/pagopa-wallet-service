package it.pagopa.wallet.repositories

import it.pagopa.wallet.domain.methods.PaymentMethodInfo
import java.time.Duration
import org.springframework.data.redis.core.ReactiveRedisTemplate

class PaymentMethodsInfoTemplateWrapper
/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(reactiveRedisTemplate: ReactiveRedisTemplate<String, PaymentMethodInfo>, ttl: Duration) :
    ReactiveRedisTemplateWrapper<PaymentMethodInfo>(
        reactiveRedisTemplate = reactiveRedisTemplate, "wallet-service:payment-methods-info", ttl) {
    override fun getKeyFromEntity(value: PaymentMethodInfo): String = value.id
}
