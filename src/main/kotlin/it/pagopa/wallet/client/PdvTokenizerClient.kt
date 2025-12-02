package it.pagopa.wallet.client

import it.pagopa.generated.pdv.api.TokenApi
import it.pagopa.generated.pdv.model.PiiResource
import it.pagopa.generated.pdv.model.TokenResource
import it.pagopa.wallet.exception.PDVTokenizerException
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
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun tokenize(value: String): Mono<TokenResource> {

        return pdvTokenizerClient.saveUsingPUT(PiiResource().pii(value)).onErrorMap(
            WebClientResponseException::class.java) {
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
    }
}
