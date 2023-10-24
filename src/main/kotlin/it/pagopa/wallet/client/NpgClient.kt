package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.wallet.exception.NpgClientException
import java.util.*
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
    @Autowired @Qualifier("npgWebClient") private val paymentServicesApi: PaymentServicesApi,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createNpgOrderBuild(
        correlationId: UUID,
        createHostedOrderRequest: CreateHostedOrderRequest
    ): Mono<Fields> {
        val response: Mono<Fields> =
            try {
                logger.info("Sending orderBuild with correlationId: $correlationId")
                paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest)
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }
        return response.onErrorMap(WebClientResponseException::class.java) {
            logger.error(
                "Error communicating with NPG-orderBuild for correlationId $correlationId - response: ${it.responseBodyAsString}",
                it
            )
            when (it.statusCode) {
                HttpStatus.BAD_REQUEST ->
                    NpgClientException(
                        description = "Bad request",
                        httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
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
                else ->
                    NpgClientException(
                        description = "NPG server error: ${it.statusCode}",
                        httpStatusCode = HttpStatus.BAD_GATEWAY,
                    )
            }
        }
    }
}
