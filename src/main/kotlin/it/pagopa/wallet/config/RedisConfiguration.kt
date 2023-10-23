package it.pagopa.wallet.config

import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfiguration {

    @Bean
    fun npgSessionRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        @Value("\${wallet.session.ttl}") ttl: Long,
    ): NpgSessionsTemplateWrapper {
        val npgSessionRedisTemplate = RedisTemplate<String, NpgSession>()
        npgSessionRedisTemplate.connectionFactory = redisConnectionFactory
        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(NpgSession::class.java)
        npgSessionRedisTemplate.valueSerializer = jackson2JsonRedisSerializer
        npgSessionRedisTemplate.keySerializer = StringRedisSerializer()
        npgSessionRedisTemplate.afterPropertiesSet()
        return NpgSessionsTemplateWrapper(npgSessionRedisTemplate, Duration.ofMinutes(ttl))
    }
}
