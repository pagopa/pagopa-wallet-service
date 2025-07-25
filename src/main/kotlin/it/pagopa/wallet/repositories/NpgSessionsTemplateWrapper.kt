package it.pagopa.wallet.repositories

import org.springframework.data.redis.core.ReactiveRedisTemplate
import java.time.Duration

class NpgSessionsTemplateWrapper
/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(reactiveRedisTemplate: ReactiveRedisTemplate<String, NpgSession>, ttl: Duration) :
    ReactiveRedisTemplateWrapper<NpgSession>(reactiveRedisTemplate = reactiveRedisTemplate, "keys", ttl) {
    override fun getKeyFromEntity(value: NpgSession): String = value.orderId
}
