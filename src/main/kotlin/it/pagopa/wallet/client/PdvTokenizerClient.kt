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
                    "Error communicating with ecommerce payment-methods-handler: response: ${it.responseBodyAsString}",
                    it)
                when (it.statusCode) {
                    HttpStatus.BAD_REQUEST ->
                        PDVTokenizerException(
                            description = "EcommercePaymentMethods - Bad request",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    HttpStatus.UNAUTHORIZED ->
                        PDVTokenizerException(
                            description =
                                "EcommercePaymentMethods - Misconfigured EcommercePaymentMethods api key",
                            httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                        )

                    HttpStatus.INTERNAL_SERVER_ERROR ->
                        PDVTokenizerException(
                            description = "EcommercePaymentMethods - internal server error",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    else ->
                        PDVTokenizerException(
                            description =
                                "EcommercePaymentMethods - server error: ${it.statusCode}",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )
                }
            }
    }
}
