package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.ReactiveRedisTemplate

class UniqueIdTemplateWrapper

/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(reactiveRedisTemplate: ReactiveRedisTemplate<String, UniqueIdDocument>, ttl: Duration) :
    ReactiveRedisTemplateWrapper<UniqueIdDocument>(
        reactiveRedisTemplate = reactiveRedisTemplate, "uniqueId", ttl) {
    override fun getKeyFromEntity(value: UniqueIdDocument): String = value.id
}
