package it.pagopa.wallet.repositories

import java.time.Duration
import org.springframework.data.redis.core.ReactiveRedisTemplate

class WalletJwtTokenCtxOnboardingTemplateWrapper

/**
 * Primary constructor
 *
 * @param reactiveRedisTemplate inner reactive redis template
 * @param ttl time to live for keys
 */
(
    reactiveRedisTemplate: ReactiveRedisTemplate<String, WalletJwtTokenCtxOnboardingDocument>,
    ttl: Duration
) :
    ReactiveRedisTemplateWrapper<WalletJwtTokenCtxOnboardingDocument>(
        reactiveRedisTemplate = reactiveRedisTemplate, "uniqueId", ttl) {
    override fun getKeyFromEntity(value: WalletJwtTokenCtxOnboardingDocument): String = value.id
}
