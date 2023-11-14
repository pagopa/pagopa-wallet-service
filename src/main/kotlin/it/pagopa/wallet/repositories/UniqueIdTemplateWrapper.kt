package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.RedisTemplate

class UniqueIdTemplateWrapper

/**
 * Primary constructor
 *
 * @param redisTemplate inner redis template
 * @param ttl time to live for keys
 */
(redisTemplate: RedisTemplate<String, UniqueIdDocument>, ttl: Duration) :
    RedisTemplateWrapper<UniqueIdDocument>(redisTemplate = redisTemplate, "uniqueId", ttl) {
    override fun getKeyFromEntity(value: UniqueIdDocument): String = value.id
}
