package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.RedisTemplate

class NpgSessionsTemplateWrapper
/**
 * Primary constructor
 *
 * @param redisTemplate inner redis template
 * @param ttl time to live for keys
 */
(redisTemplate: RedisTemplate<String, NpgSession>, ttl: Duration) :
    RedisTemplateWrapper<NpgSession>(redisTemplate = redisTemplate, "keys", ttl) {
    override fun getKeyFromEntity(value: NpgSession): String = "${value.orderId}"
}
