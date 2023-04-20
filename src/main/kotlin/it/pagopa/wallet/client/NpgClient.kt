package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.DefaultApi
import it.pagopa.generated.npg.model.HppRequest
import it.pagopa.generated.npg.model.HppResponse
import it.pagopa.wallet.exception.NpgClientException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/** NPG API client service class */
@Component
class NpgClient(
    @Autowired @Qualifier("npgWebClient") private val npgClient: DefaultApi,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun orderHpp(hppRequest: HppRequest): Mono<HppResponse> {
        val response: Mono<HppResponse> =
            try {
                npgClient.startPayment(hppRequest)
            } catch (e: WebClientResponseException) {
                logger.error("Error communicating with NPG", e)
                Mono.error(
                    NpgClientException(
                        description = "Error communicating with NPG",
                        httpStatusCode = HttpStatus.BAD_GATEWAY,
                    )
                )
            }
        return response.onErrorMap(WebClientResponseException::class.java) {
            logger.error("Npg response error", it)
            when (it.statusCode) {
                HttpStatus.BAD_REQUEST ->
                    NpgClientException(
                        description = "Bad request",
                        httpStatusCode = HttpStatus.BAD_REQUEST,
                    )
                HttpStatus.UNAUTHORIZED ->
                    NpgClientException(
                        description = "Misconfigured NPG api key",
                        httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                    )
                HttpStatus.INTERNAL_SERVER_ERROR ->
                    NpgClientException(
                        description = "NPG internal server error",
                        httpStatusCode = HttpStatus.BAD_GATEWAY,
                    )
                else -> it
            }
        }
    }
}
