package it.pagopa.wallet.repositories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import java.time.Duration

class ReactiveRedisTemplateWrapperTest {
    class MockReactiveRedisTemplateWrapper(
        reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
        keyspace: String,
        ttl: Duration,
    ) :
        ReactiveRedisTemplateWrapper<String>(
            reactiveRedisTemplate = reactiveRedisTemplate,
            ttl = ttl,
            keyspace = keyspace,
        ) {
        override fun getKeyFromEntity(value: String) = "key"
    }

    private val redisTemplate: ReactiveRedisTemplate<String, String> = mock()
    private val defaultTtl = Duration.ofSeconds(1)
    private val keySpace = "keyspace"

    private val mockedRedisTemplate =
        MockReactiveRedisTemplateWrapper(
            reactiveRedisTemplate = redisTemplate,
            keyspace = keySpace,
            ttl = defaultTtl,
        )

    private val opsForVal: ReactiveValueOperations<String, String> = mock()

    @Test
    fun `should save entity`() {
        // pre-conditions
        val value = "value"
        val result = Mono.just(true)
        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.set(any(), any(), any<Duration>())).willReturn(result)
        // test
        val returnedResult = mockedRedisTemplate.save(value)
        // assertions
        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).set("$keySpace:key", value, defaultTtl)
    }

    @Test
    fun `should save entity if key is absent`() {
        // pre-conditions
        val value = "value"
        val result = Mono.just(true)
        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.setIfAbsent(any(), any(), any<Duration>())).willReturn(result)
        // test
        val returnedResult = mockedRedisTemplate.saveIfAbsent(value)
        // assertions
        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).setIfAbsent("$keySpace:key", value, defaultTtl)
    }

    @Test
    fun `should save entity with custom TTL if key absent`() {
        // pre-conditions
        val value = "value"
        val result = Mono.just(true)
        val customTtl = Duration.ofSeconds(60)
        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.setIfAbsent(any(), any(), any<Duration>())).willReturn(result)
        // test
        val returnedResult = mockedRedisTemplate.saveIfAbsent(value, customTtl)
        // assertions
        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).setIfAbsent("$keySpace:key", value, customTtl)
    }


    @Test
    fun `should find entity by id`() {
        // pre-conditions
        val value = Mono.just("value")
        val key = "key"
        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.get(any())).willReturn(value)
        // test
        val returnedValue = mockedRedisTemplate.findById(key)
        // assertions
        assertEquals(value, returnedValue)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).get("$keySpace:key")
    }
}