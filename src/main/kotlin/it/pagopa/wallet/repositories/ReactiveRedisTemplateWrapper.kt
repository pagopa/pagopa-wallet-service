package it.pagopa.wallet.repositories

import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration

abstract class ReactiveRedisTemplateWrapper<V>(
    val reactiveRedisTemplate: ReactiveRedisTemplate<String, V>,
    private val keyspace: String,
    private val ttl: Duration
) {

    fun save(value: V) {
        reactiveRedisTemplate.opsForValue()["$keyspace:${getKeyFromEntity(value)}", value!!] = ttl
    }

    fun saveIfAbsent(value: V): Mono<Boolean> {
        return reactiveRedisTemplate
            .opsForValue()
            .setIfAbsent("$keyspace:${getKeyFromEntity(value)}", value!!, ttl)
    }

    fun saveIfAbsent(value: V, customTtl: Duration): Mono<Boolean> {
        return reactiveRedisTemplate
            .opsForValue()
            .setIfAbsent("$keyspace:${getKeyFromEntity(value)}", value!!, customTtl)
    }

    fun findById(key: String): Mono<V> = reactiveRedisTemplate.opsForValue()["$keyspace:$key"]

    protected abstract fun getKeyFromEntity(value: V): String
}
