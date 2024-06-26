package it.pagopa.wallet.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.vavr.control.Either
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.*
import it.pagopa.wallet.client.NpgClient.NpgTracing.GatewayOperation
import it.pagopa.wallet.client.NpgClient.NpgTracing.NPG_CORRELATION_ID_ATTRIBUTE_NAME
import it.pagopa.wallet.client.NpgClient.NpgTracing.NPG_ERROR_CODES_ATTRIBUTE_NAME
import it.pagopa.wallet.client.NpgClient.NpgTracing.NPG_HTTP_ERROR_CODE
import it.pagopa.wallet.client.NpgClient.NpgTracing.usingNpgTracing
import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.util.npg.NpgPspApiKeysConfig
import java.io.IOException
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
    @Autowired private val npgPaypalPspApiKeysConfig: NpgPspApiKeysConfig,
    private val tracer: Tracer,
    private val objectMapper: ObjectMapper,
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

        return usingNpgTracing(
            tracer,
            GatewayOperation.BUILD_FORM,
            { it.setAttribute(NPG_CORRELATION_ID_ATTRIBUTE_NAME, correlationId.toString()) }
        ) { span, operation ->
            logger.info("Sending orderBuild with correlationId: $correlationId")
            apiKey
                .fold(
                    { Mono.error(it) },
                    {
                        npgWebClient.pspApiV1OrdersBuildPost(
                            correlationId,
                            it,
                            createHostedOrderRequest
                        )
                    }
                )
                .doOnError(WebClientResponseException::class.java) {
                    logger.error(
                        "Error communicating with NPG-orderBuild  for correlationId $correlationId - response: ${it.responseBodyAsString}",
                        it
                    )
                }
                .onErrorMap { error -> exceptionToNpgResponseException(error, span, operation) }
        }
    }

    fun getCardData(sessionId: String, correlationId: UUID): Mono<CardDataResponse> {
        return usingNpgTracing(
            tracer,
            GatewayOperation.GET_CARD_DATA,
            { it.setAttribute(NPG_CORRELATION_ID_ATTRIBUTE_NAME, correlationId.toString()) }
        ) { span, operation ->
            logger.info("getCardData with correlationId: $correlationId")
            npgWebClient
                .pspApiV1BuildCardDataGet(
                    correlationId,
                    npgPaypalPspApiKeysConfig.defaultApiKey,
                    sessionId
                )
                .doOnError(WebClientResponseException::class.java) {
                    logger.error(
                        "Error communicating with NPG-getCardData for correlationId $correlationId - response: ${it.responseBodyAsString}",
                        it
                    )
                }
                .onErrorMap { error -> exceptionToNpgResponseException(error, span, operation) }
        }
    }

    fun confirmPayment(
        confirmPaymentRequest: ConfirmPaymentRequest,
        correlationId: UUID
    ): Mono<StateResponse> {
        return usingNpgTracing(
            tracer,
            GatewayOperation.CONFIRM_PAYMENT,
            { it.setAttribute(NPG_CORRELATION_ID_ATTRIBUTE_NAME, correlationId.toString()) }
        ) { span, operation ->
            logger.info("confirmPayment with correlationId: $correlationId")
            npgWebClient
                .pspApiV1BuildConfirmPaymentPost(
                    correlationId,
                    npgPaypalPspApiKeysConfig.defaultApiKey,
                    confirmPaymentRequest,
                )
                .doOnError(WebClientResponseException::class.java) {
                    logger.error(
                        "Error communicating with NPG-confirmPayment for correlationId $correlationId - response: ${it.responseBodyAsString}",
                        it
                    )
                }
                .onErrorMap { error -> exceptionToNpgResponseException(error, span, operation) }
        }
    }

    private fun exceptionToNpgResponseException(
        err: Throwable,
        span: Span,
        gatewayOperation: GatewayOperation
    ): NpgClientException {
        span.setStatus(StatusCode.ERROR)
        if (err is WebClientResponseException) {
            try {
                val responseErrors =
                    when (err.statusCode.value()) {
                        INTERNAL_SERVER_ERROR.code() ->
                            objectMapper
                                .readValue(err.responseBodyAsByteArray, ServerError::class.java)
                                .errors
                        BAD_REQUEST.code() ->
                            objectMapper
                                .readValue(err.responseBodyAsByteArray, ClientError::class.java)
                                .errors
                        else -> emptyList()
                    }?.mapNotNull { it.code }
                        ?: emptyList()

                span.setAttribute(NPG_ERROR_CODES_ATTRIBUTE_NAME, responseErrors)

                span.setAttribute(NPG_HTTP_ERROR_CODE, err.statusCode.value())

                return mapNpgException(err.statusCode)
            } catch (ex: IOException) {
                return NpgClientException(
                    description =
                        "Invalid error response from NPG with status code ${err.statusCode}",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                )
            }
        }

        return NpgClientException(
            "Unexpected error while invoke method for %s".format(gatewayOperation.spanName),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
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

    object NpgTracing {
        val NPG_CORRELATION_ID_ATTRIBUTE_NAME: AttributeKey<String> =
            AttributeKey.stringKey("npg.correlation_id")
        val NPG_ERROR_CODES_ATTRIBUTE_NAME: AttributeKey<List<String>> =
            AttributeKey.stringArrayKey("npg.error_codes")
        val NPG_HTTP_ERROR_CODE: AttributeKey<Long> = AttributeKey.longKey("npg.http_error_code")

        enum class GatewayOperation(val spanName: String) {
            BUILD_FORM("NpgClient#buildForm"),
            GET_CARD_DATA("NpgClient#getCardData"),
            CONFIRM_PAYMENT("NpgClient#confirmPayment"),
        }

        fun <T> usingNpgTracing(
            tracer: Tracer,
            operation: GatewayOperation,
            spanDecorator: (SpanBuilder) -> SpanBuilder,
            mono: (Span, GatewayOperation) -> Mono<T>
        ): Mono<T> =
            Mono.using(
                {
                    spanDecorator(
                            tracer
                                .spanBuilder(operation.spanName)
                                .setParent(Context.current().with(Span.current()))
                        )
                        .startSpan()
                },
                { span -> mono(span, operation) },
                { span -> span.end() }
            )
    }
}
