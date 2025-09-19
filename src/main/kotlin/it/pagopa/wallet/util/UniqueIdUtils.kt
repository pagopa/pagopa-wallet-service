package it.pagopa.wallet.util

import it.pagopa.wallet.exception.UniqueIdGenerationException
import it.pagopa.wallet.repositories.UniqueIdDocument
import it.pagopa.wallet.repositories.UniqueIdTemplateWrapper
import java.security.SecureRandom
import java.time.Duration
import java.util.stream.IntStream
import kotlin.streams.toList
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class UniqueIdUtils(
    @Autowired private val uniqueIdTemplateWrapper: UniqueIdTemplateWrapper,
) {
    private val secureRandom = SecureRandom()

    companion object {
        const val ALPHANUMERICS: String =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"
        const val MAX_LENGTH: Int = 18
        const val MAX_NUMBER_ATTEMPTS = 3
        const val PRODUCT_PREFIX = "W"
    }

    fun generateUniqueId(): Mono<String> {
        return Mono.fromSupplier { generateRandomIdentifier() }
            .flatMap { uniqueId ->
                uniqueIdTemplateWrapper
                    .saveIfAbsent(UniqueIdDocument(uniqueId), Duration.ofSeconds(60))
                    .map { savedSuccessfully -> Pair(uniqueId, savedSuccessfully) }
            }
            .filter { (_, savedSuccessfully) -> savedSuccessfully }
            .onErrorResume { Mono.empty() }
            .map { (uniqueId, _) -> uniqueId }
            .repeatWhenEmpty {
                Flux.fromIterable(IntStream.range(0, MAX_NUMBER_ATTEMPTS - 1).toList())
            }
            .switchIfEmpty(Mono.error { UniqueIdGenerationException() })
    }

    private fun generateRandomIdentifier(): String {
        val uniqueId = StringBuilder(PRODUCT_PREFIX)
        uniqueId.append(System.currentTimeMillis().toString())
        val randomStringLength = MAX_LENGTH - uniqueId.length
        return uniqueId.append(generateRandomString(randomStringLength)).toString()
    }

    private fun generateRandomString(length: Int): String {
        val stringBuilder = StringBuilder(length)
        for (i in 0 until length) {
            stringBuilder.append(ALPHANUMERICS[secureRandom.nextInt(ALPHANUMERICS.length)])
        }
        return stringBuilder.toString()
    }
}
