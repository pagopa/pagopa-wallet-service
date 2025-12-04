package it.pagopa.wallet.repositories

import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono

class PdvTokenTemplateWrapperTest {

    private val redisTemplate: ReactiveRedisTemplate<String, PdvTokenCacheDocument> = mock()
    private val defaultTtl = Duration.ofSeconds(86400)
    private val keySpace = "wallet-service:pdv-fiscal-code-tokens"

    private val pdvTokenTemplateWrapper =
        PdvTokenTemplateWrapper(reactiveRedisTemplate = redisTemplate, ttl = defaultTtl)

    private val opsForVal: ReactiveValueOperations<String, PdvTokenCacheDocument> = mock()

    @Test
    fun `should save PDV token cache document`() {
        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)
        val result = Mono.just(true)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.set(any(), any(), any<Duration>())).willReturn(result)

        val returnedResult = pdvTokenTemplateWrapper.save(cacheDocument)

        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).set("$keySpace:$hashedFiscalCode", cacheDocument, defaultTtl)
    }

    @Test
    fun `should save PDV token if absent`() {
        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)
        val result = Mono.just(true)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.setIfAbsent(any(), any(), any<Duration>())).willReturn(result)

        val returnedResult = pdvTokenTemplateWrapper.saveIfAbsent(cacheDocument)

        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1))
            .setIfAbsent("$keySpace:$hashedFiscalCode", cacheDocument, defaultTtl)
    }

    @Test
    fun `should save PDV token with custom TTL if absent`() {
        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)
        val result = Mono.just(true)
        val customTtl = Duration.ofSeconds(3600)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.setIfAbsent(any(), any(), any<Duration>())).willReturn(result)

        val returnedResult = pdvTokenTemplateWrapper.saveIfAbsent(cacheDocument, customTtl)

        assertEquals(result, returnedResult)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1))
            .setIfAbsent("$keySpace:$hashedFiscalCode", cacheDocument, customTtl)
    }

    @Test
    fun `should find PDV token by hashed fiscal code`() {
        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)
        val result = Mono.just(cacheDocument)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.get(any())).willReturn(result)

        val returnedValue = pdvTokenTemplateWrapper.findById(hashedFiscalCode)

        assertEquals(result, returnedValue)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).get("$keySpace:$hashedFiscalCode")
    }

    @Test
    fun `should return empty when PDV token not found in cache`() {
        val hashedFiscalCode = "nonexistent"
        val result = Mono.empty<PdvTokenCacheDocument>()

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.get(any())).willReturn(result)

        val returnedValue = pdvTokenTemplateWrapper.findById(hashedFiscalCode)

        assertEquals(result, returnedValue)
        verify(redisTemplate, times(1)).opsForValue()
        verify(opsForVal, times(1)).get("$keySpace:$hashedFiscalCode")
    }

    @Test
    fun `should use correct keyspace`() {
        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.set(any(), any(), any<Duration>())).willReturn(Mono.just(true))

        pdvTokenTemplateWrapper.save(cacheDocument)

        verify(opsForVal, times(1))
            .set(eq("$keySpace:$hashedFiscalCode"), eq(cacheDocument), eq(defaultTtl))
    }

    @Test
    fun `should use configured TTL`() {
        val customTtl = Duration.ofSeconds(1800)
        val pdvTokenTemplateWrapperWithCustomTtl =
            PdvTokenTemplateWrapper(reactiveRedisTemplate = redisTemplate, ttl = customTtl)

        val hashedFiscalCode = "a1b2c3d4e5f6"
        val token = UUID.randomUUID()
        val cacheDocument = PdvTokenCacheDocument(hashedFiscalCode, token)

        given(redisTemplate.opsForValue()).willReturn(opsForVal)
        given(opsForVal.set(any(), any(), any<Duration>())).willReturn(Mono.just(true))

        pdvTokenTemplateWrapperWithCustomTtl.save(cacheDocument)

        verify(opsForVal, times(1)).set(any(), eq(cacheDocument), eq(customTtl))
    }
}
