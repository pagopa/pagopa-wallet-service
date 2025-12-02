package it.pagopa.wallet.client

import it.pagopa.generated.pdv.api.TokenApi
import it.pagopa.generated.pdv.model.PiiResource
import it.pagopa.generated.pdv.model.TokenResource
import it.pagopa.wallet.exception.PDVTokenizerException
import it.pagopa.wallet.repositories.PdvTokenCacheDocument
import it.pagopa.wallet.repositories.PdvTokenTemplateWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class PdvTokenizerClient(
    @Autowired @Qualifier("pdvTokenizerWebClient") private val pdvTokenizerClient: TokenApi,
    @Autowired private val pdvTokenRedisTemplate: PdvTokenTemplateWrapper,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun tokenize(value: String): Mono<TokenResource> {
        val normalizedFiscalCode = value.uppercase()

        return pdvTokenRedisTemplate
            .findById(normalizedFiscalCode)
            .doOnNext {
                logger.debug("PDV token cache HIT for fiscal code: {}", normalizedFiscalCode)
            }
            .map { cachedDocument -> TokenResource().token(cachedDocument.token) }
            .onErrorResume { cacheError ->
                logger.warn(
                    "Redis cache read error for PDV token, falling back to PDV service", cacheError)
                Mono.empty()
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("PDV token cache MISS for fiscal code: {}", normalizedFiscalCode)
                    tokenizeFromPdvService(value, normalizedFiscalCode)
                })
    }

    private fun tokenizeFromPdvService(
        fiscalCode: String,
        normalizedFiscalCode: String
    ): Mono<TokenResource> {
        return pdvTokenizerClient
            .saveUsingPUT(PiiResource().pii(fiscalCode))
            .onErrorMap(WebClientResponseException::class.java) {
                logger.error(
                    "Error communicating with PDV: response: ${it.responseBodyAsString}", it)
                when (it.statusCode) {
                    HttpStatus.BAD_REQUEST ->
                        PDVTokenizerException(
                            description = "PDV - Bad request response",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    HttpStatus.UNAUTHORIZED ->
                        PDVTokenizerException(
                            description = "PDV - Misconfigured PDV api key",
                            httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                        )

                    HttpStatus.INTERNAL_SERVER_ERROR ->
                        PDVTokenizerException(
                            description = "PDV - internal server error",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    else ->
                        PDVTokenizerException(
                            description = "PDV - server error: ${it.statusCode}",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )
                }
            }
            .flatMap { tokenResource ->
                pdvTokenRedisTemplate
                    .save(PdvTokenCacheDocument(normalizedFiscalCode, tokenResource.token))
                    .doOnSuccess {
                        logger.debug("Cached PDV token for fiscal code: {}", normalizedFiscalCode)
                    }
                    .onErrorResume { cacheError ->
                        logger.warn(
                            "Failed to cache PDV token, continuing without caching", cacheError)
                        Mono.just(false)
                    }
                    .thenReturn(tokenResource)
            }
    }
}
