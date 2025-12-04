package it.pagopa.wallet.client

import it.pagopa.generated.pdv.api.TokenApi
import it.pagopa.generated.pdv.model.PiiResource
import it.pagopa.generated.pdv.model.TokenResource
import it.pagopa.wallet.exception.PDVTokenizerException
import it.pagopa.wallet.repositories.PdvTokenCacheDocument
import it.pagopa.wallet.repositories.PdvTokenTemplateWrapper
import it.pagopa.wallet.util.FiscalCodeHasher
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
        val hashedFiscalCode = FiscalCodeHasher.hashFiscalCode(value)

        return pdvTokenRedisTemplate
            .findById(hashedFiscalCode)
            .doOnNext {
                val maskedHashedFiscalCode = hashedFiscalCode.take(8)
                logger.debug("PDV token cache HIT for fiscal code hash: {}", maskedHashedFiscalCode)
            }
            .map { cachedDocument -> TokenResource().token(cachedDocument.token) }
            .onErrorResume { cacheError ->
                logger.warn(
                    "Redis cache read error for PDV token, falling back to PDV service", cacheError)
                Mono.empty()
            }
            .switchIfEmpty(
                Mono.defer {
                    val maskedHashedFiscalCode = hashedFiscalCode.take(8)
                    logger.debug(
                        "PDV token cache MISS for fiscal code hash: {}", maskedHashedFiscalCode)
                    tokenizeFromPdvService(value, hashedFiscalCode)
                })
    }

    private fun tokenizeFromPdvService(
        fiscalCode: String,
        hashedFiscalCode: String
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
                    .save(PdvTokenCacheDocument(hashedFiscalCode, tokenResource.token))
                    .doOnSuccess {
                        val maskedHashedFiscalCode = hashedFiscalCode.take(8)
                        logger.debug(
                            "Cached PDV token for fiscal code hash: {}", maskedHashedFiscalCode)
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
