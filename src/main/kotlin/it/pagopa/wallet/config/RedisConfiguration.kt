package it.pagopa.wallet.config

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.wallet.repositories.*
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
    fun paymentMethodsRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        @Value("\${payment-methods.cache.ttlSeconds}") ttlSeconds: Long,
    ): PaymentMethodsTemplateWrapper {
        val paymentMethodsRedisTemplate = RedisTemplate<String, PaymentMethodResponse>()
        paymentMethodsRedisTemplate.connectionFactory = redisConnectionFactory
        val jackson2JsonRedisSerializer =
            buildJackson2RedisSerializer(PaymentMethodResponse::class.java)
        paymentMethodsRedisTemplate.valueSerializer = jackson2JsonRedisSerializer
        paymentMethodsRedisTemplate.keySerializer = StringRedisSerializer()
        paymentMethodsRedisTemplate.afterPropertiesSet()
        return PaymentMethodsTemplateWrapper(
            paymentMethodsRedisTemplate,
            Duration.ofSeconds(ttlSeconds)
        )
    }

    @Bean
    fun npgSessionRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        @Value("\${wallet.session.ttlSeconds}") ttlSeconds: Long,
    ): NpgSessionsTemplateWrapper {
        val npgSessionRedisTemplate = RedisTemplate<String, NpgSession>()
        npgSessionRedisTemplate.connectionFactory = redisConnectionFactory
        val jackson2JsonRedisSerializer = buildJackson2RedisSerializer(NpgSession::class.java)
        npgSessionRedisTemplate.valueSerializer = jackson2JsonRedisSerializer
        npgSessionRedisTemplate.keySerializer = StringRedisSerializer()
        npgSessionRedisTemplate.afterPropertiesSet()
        return NpgSessionsTemplateWrapper(npgSessionRedisTemplate, Duration.ofSeconds(ttlSeconds))
    }

    @Bean
    fun uniqueIdRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory
    ): UniqueIdTemplateWrapper {
        val uniqueIdTemplateWrapper = RedisTemplate<String, UniqueIdDocument>()
        uniqueIdTemplateWrapper.connectionFactory = redisConnectionFactory
        val jackson2JsonRedisSerializer = buildJackson2RedisSerializer(UniqueIdDocument::class.java)
        uniqueIdTemplateWrapper.valueSerializer = jackson2JsonRedisSerializer
        uniqueIdTemplateWrapper.keySerializer = StringRedisSerializer()
        uniqueIdTemplateWrapper.afterPropertiesSet()
        return UniqueIdTemplateWrapper(uniqueIdTemplateWrapper, Duration.ofSeconds(60))
    }

    private fun <T> buildJackson2RedisSerializer(clazz: Class<T>): Jackson2JsonRedisSerializer<T> {
        val jacksonObjectMapper = jacksonObjectMapper()
        val rptSerializationModule = SimpleModule()
        jacksonObjectMapper.registerModule(rptSerializationModule)
        return Jackson2JsonRedisSerializer(jacksonObjectMapper, clazz)
    }
}
