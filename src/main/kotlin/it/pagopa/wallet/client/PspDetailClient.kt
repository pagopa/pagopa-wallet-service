package it.pagopa.wallet.client

import io.vavr.control.Try
import it.pagopa.generated.ecommerce.paymentmethodshandler.model.*
import it.pagopa.wallet.domain.wallets.PaymentMethodId
import it.pagopa.wallet.exception.RestApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 * Client to retrieve info about psp by given a pspId or get all psp by payment method. Actually is
 * backed by calculate fees exposed by the payment-methods-handler microservice.
 *
 * Note: It could be backed by dedicated Psp API to get details about it without using calculate
 * fees, so keep this client separated by PaymentMethodsClient
 */
@Component
class PspDetailClient(
    @Qualifier("ecommercePaymentMethodsHandlerWebClient")
    private val paymentMethodsApi:
        it.pagopa.generated.ecommerce.paymentmethodshandler.api.PaymentMethodsHandlerApi
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPspList(paymentMethodId: PaymentMethodId): Mono<CalculateFeeResponse> {
        logger.info("Getting psp list for payment method id [{}]", paymentMethodId.value)
        return Try.of {
                paymentMethodsApi.calculateFees(
                    paymentMethodId.value.toString(),
                    X_CLIENT_ID,
                    STUB_FEES_REQUEST,
                    DEFAULT_MAX_OCCURRENCES,
                    null)
            }
            .fold({ error -> Mono.error(error) }, { it })
            .doOnError {
                when (it) {
                    is WebClientResponseException ->
                        logger.error(
                            "Failed to get psp list. HTTP Status: [${it.statusCode}], Response: [${it.responseBodyAsString}]",
                            it)
                    else -> logger.error("Failed to get psp list", it)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) {
                RestApiException(
                    HttpStatus.valueOf(it.statusCode.value()),
                    it.statusText,
                    "Failed to get psp list")
            }
    }

    fun getPspDetails(pspId: String, paymentMethodId: PaymentMethodId): Mono<Bundle> {
        logger.info("Getting info about pspId [{}]", pspId)
        return getPspList(paymentMethodId).flatMap { bundle ->
            bundle.bundles.firstOrNull { it.idPsp == pspId }?.toMono() ?: Mono.empty()
        }
    }

    companion object {
        private const val X_CLIENT_ID = "IO"
        private const val DEFAULT_MAX_OCCURRENCES = 1000
        private val STUB_FEES_REQUEST =
            CalculateFeeRequest()
                .idPspList(emptyList())
                .touchpoint("IO")
                .addPaymentNoticesItem(
                    PaymentNotice()
                        .paymentAmount(1L)
                        .primaryCreditorInstitution("")
                        .addTransferListItem(
                            TransferListItem()
                                .transferCategory("")
                                .digitalStamp(false)
                                .creditorInstitution("")))
                .isAllCCP(false)
    }
}
