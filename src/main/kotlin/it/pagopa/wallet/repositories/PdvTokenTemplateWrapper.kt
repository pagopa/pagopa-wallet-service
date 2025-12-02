package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.ReactiveRedisTemplate

class PdvTokenTemplateWrapper
/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(reactiveRedisTemplate: ReactiveRedisTemplate<String, PdvTokenCacheDocument>, ttl: Duration) :
    ReactiveRedisTemplateWrapper<PdvTokenCacheDocument>(
        reactiveRedisTemplate = reactiveRedisTemplate,
        keyspace = "wallet-service:pdv-fiscal-code-tokens",
        ttl = ttl) {
    override fun getKeyFromEntity(value: PdvTokenCacheDocument): String = value.hashedFiscalCode
}
