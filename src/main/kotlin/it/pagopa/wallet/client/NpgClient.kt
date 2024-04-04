package it.pagopa.wallet.client

import io.vavr.control.Either
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.util.npg.NpgPspApiKeysConfig
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/** NPG API client service class */
@Component
class NpgClient(
    @Autowired @Qualifier("npgWebClient") private val npgWebClient: PaymentServicesApi,
    @Autowired private val npgPaypalPspApiKeysConfig: NpgPspApiKeysConfig
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createNpgOrderBuild(
        correlationId: UUID,
        createHostedOrderRequest: CreateHostedOrderRequest,
        pspId: String?
    ): Mono<Fields> {
        val apiKey =
            if (pspId == null) {
                Either.right(npgPaypalPspApiKeysConfig.defaultApiKey)
            } else {
                npgPaypalPspApiKeysConfig[pspId]
            }

        val response: Mono<Fields> =
            try {
                logger.info("Sending orderBuild with correlationId: $correlationId")
                apiKey.fold(
                    { Mono.error(it) },
                    {
                        npgWebClient.pspApiV1OrdersBuildPost(
                            correlationId,
                            it,
                            createHostedOrderRequest
                        )
                    }
                )
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }
        return response.onErrorMap(WebClientResponseException::class.java) {
            logger.error(
                "Error communicating with NPG-orderBuild for correlationId $correlationId - response: ${it.responseBodyAsString}",
                it
            )
            mapNpgException(it.statusCode)
        }
    }

    fun getCardData(sessionId: String, correlationId: UUID): Mono<CardDataResponse> {
        val response: Mono<CardDataResponse> =
            try {
                logger.info("getCardData with correlationId: $correlationId")
                npgWebClient.pspApiV1BuildCardDataGet(
                    correlationId,
                    npgPaypalPspApiKeysConfig.defaultApiKey,
                    sessionId
                )
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }
        return response.onErrorMap(WebClientResponseException::class.java) {
            logger.error(
                "Error communicating with NPG-getCardData for correlationId $correlationId - response: ${it.responseBodyAsString}",
                it
            )
            mapNpgException(it.statusCode)
        }
    }

    fun confirmPayment(
        confirmPaymentRequest: ConfirmPaymentRequest,
        correlationId: UUID
    ): Mono<StateResponse> {
        val response: Mono<StateResponse> =
            try {
                logger.info("confirmPayment with correlationId: $correlationId")
                npgWebClient.pspApiV1BuildConfirmPaymentPost(
                    correlationId,
                    npgPaypalPspApiKeysConfig.defaultApiKey,
                    confirmPaymentRequest,
                )
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }
        return response.onErrorMap(WebClientResponseException::class.java) {
            logger.error(
                "Error communicating with NPG-confirmPayment for correlationId $correlationId - response: ${it.responseBodyAsString}",
                it
            )
            mapNpgException(it.statusCode)
        }
    }

    private fun mapNpgException(statusCode: HttpStatusCode): NpgClientException =
        when (statusCode) {
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
                    description = "NPG server error: $statusCode",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                )
        }
}
