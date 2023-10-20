package it.pagopa.wallet.config

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import org.springframework.beans.factory.annotation.Value
import java.time.Duration
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
      npgSessionRedisTemplate.setConnectionFactory(redisConnectionFactory)
    val jackson2JsonRedisSerializer = buildJackson2RedisSerializer(NpgSession::class.java)
      npgSessionRedisTemplate.valueSerializer = jackson2JsonRedisSerializer
      npgSessionRedisTemplate.keySerializer = StringRedisSerializer()
      npgSessionRedisTemplate.afterPropertiesSet()
    return NpgSessionsTemplateWrapper(npgSessionRedisTemplate, Duration.ofMinutes(ttl))
  }

    private fun <T> buildJackson2RedisSerializer(clazz: Class<T>): Jackson2JsonRedisSerializer<T> {
        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(clazz)
        val jacksonObjectMapper = jacksonObjectMapper()
        val rptSerializationModule = SimpleModule()
        jacksonObjectMapper.registerModule(rptSerializationModule)
        jackson2JsonRedisSerializer.setObjectMapper(jacksonObjectMapper)
        return jackson2JsonRedisSerializer
    }
}
