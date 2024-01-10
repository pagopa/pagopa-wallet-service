package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.NpgClientException
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
    @Autowired @Qualifier("npgWebClient") private val cardsServicesApi: PaymentServicesApi,
    @Autowired @Qualifier("npgPaypalWebClient") private val paypalServicesApi: PaymentServicesApi
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createNpgOrderBuild(
        correlationId: UUID,
        createHostedOrderRequest: CreateHostedOrderRequest
    ): Mono<Fields> {
        val client =
            when (createHostedOrderRequest.paymentSession?.paymentService) {
                WalletDetailsType.CARDS.name -> {
                    cardsServicesApi
                }
                WalletDetailsType.PAYPAL.name -> {
                    paypalServicesApi
                }
                else ->
                    throw NpgClientException(
                        "Invalid /order/build request: unhandled `paymentSession.paymentService` (value is ${createHostedOrderRequest.paymentSession?.paymentService})",
                        HttpStatus.BAD_GATEWAY
                    )
            }

        val response: Mono<Fields> =
            try {
                logger.info("Sending orderBuild with correlationId: $correlationId")
                client.pspApiV1OrdersBuildPost(correlationId, createHostedOrderRequest)
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
                cardsServicesApi.pspApiV1BuildCardDataGet(correlationId, sessionId)
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
                cardsServicesApi.pspApiV1BuildConfirmPaymentPost(
                    correlationId,
                    confirmPaymentRequest
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
