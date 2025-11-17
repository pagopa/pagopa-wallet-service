package it.pagopa.wallet.config

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.wallet.domain.methods.PaymentMethodInfo
import it.pagopa.wallet.repositories.*
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfiguration {

    @Bean
    fun paymentMethodsRedisTemplate(
        reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory,
        @Value("\${payment-methods.cache.ttlSeconds}") ttlSeconds: Long,
    ): PaymentMethodsInfoTemplateWrapper {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = buildJackson2RedisSerializer(PaymentMethodInfo::class.java)

        val serializationContext =
            RedisSerializationContext.newSerializationContext<String, PaymentMethodInfo>(
                    keySerializer)
                .value(valueSerializer)
                .build()

        val paymentMethodsRedisTemplate =
            ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext)

        return PaymentMethodsInfoTemplateWrapper(
            paymentMethodsRedisTemplate, Duration.ofSeconds(ttlSeconds))
    }

    @Bean
    fun npgSessionRedisTemplate(
        reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory,
        @Value("\${wallet.session.ttlSeconds}") ttlSeconds: Long,
    ): NpgSessionsTemplateWrapper {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = buildJackson2RedisSerializer(NpgSession::class.java)

        val serializationContext =
            RedisSerializationContext.newSerializationContext<String, NpgSession>(keySerializer)
                .value(valueSerializer)
                .build()

        val npgSessionRedisTemplate =
            ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext)

        return NpgSessionsTemplateWrapper(npgSessionRedisTemplate, Duration.ofSeconds(ttlSeconds))
    }

    @Bean
    fun uniqueIdRedisTemplate(
        reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory
    ): UniqueIdTemplateWrapper {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = buildJackson2RedisSerializer(UniqueIdDocument::class.java)

        val serializationContext =
            RedisSerializationContext.newSerializationContext<String, UniqueIdDocument>(
                    keySerializer)
                .value(valueSerializer)
                .build()

        val uniqueIdTemplateWrapper =
            ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext)

        return UniqueIdTemplateWrapper(uniqueIdTemplateWrapper, Duration.ofSeconds(60))
    }

    @Bean
    fun walletJwtTokenCtxOnboardingRedisTemplate(
        reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory,
        @Value("\${wallet.ctxOnboardingJwtToken.ttlSeconds}") ttlSeconds: Long,
    ): WalletJwtTokenCtxOnboardingTemplateWrapper {
        val keySerializer = StringRedisSerializer()
        val valueSerializer =
            buildJackson2RedisSerializer(WalletJwtTokenCtxOnboardingDocument::class.java)

        val serializationContext =
            RedisSerializationContext.newSerializationContext<
                    String, WalletJwtTokenCtxOnboardingDocument>(
                    keySerializer)
                .value(valueSerializer)
                .build()

        val walletJwtTokenCtxOnboardingRedisTemplate =
            ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext)

        return WalletJwtTokenCtxOnboardingTemplateWrapper(
            walletJwtTokenCtxOnboardingRedisTemplate, Duration.ofSeconds(ttlSeconds))
    }

    private fun <T> buildJackson2RedisSerializer(clazz: Class<T>): Jackson2JsonRedisSerializer<T> {
        val jacksonObjectMapper = jacksonObjectMapper()
        val rptSerializationModule = SimpleModule()
        jacksonObjectMapper.registerModule(rptSerializationModule)
        return Jackson2JsonRedisSerializer(jacksonObjectMapper, clazz)
    }
}
