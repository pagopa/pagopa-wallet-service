package it.pagopa.wallet.client

import io.vavr.control.Try
import it.pagopa.generated.afm.model.*
import it.pagopa.wallet.exception.RestApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Component
class AfmCalculatorClient(private val calculatorApi: it.pagopa.generated.afm.api.CalculatorApi) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPspList(paymentMethodCode: String): Mono<BundleOption> {
        logger.info("Getting psp list for payment method code [{}]", paymentMethodCode)
        return Try.of {
                calculatorApi.getFeesMulti(
                    STUB_FEES_REQUEST.paymentMethod(paymentMethodCode),
                    1000,
                    "false"
                )
            }
            .fold({ error -> Mono.error(error) }, { it })
            .doOnError {
                when (it) {
                    is WebClientResponseException ->
                        logger.error(
                            "Failed to get psp list. HTTP Status: [${it.statusCode}], Response: [${it.responseBodyAsString}]",
                            it
                        )
                    else -> logger.error("Failed to get psp list", it)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) {
                RestApiException(
                    HttpStatus.valueOf(it.statusCode.value()),
                    it.statusText,
                    "Failed to get psp list"
                )
            }
    }

    fun getPspDetails(pspId: String, paymentMethodCode: String): Mono<Transfer> {
        return getPspList(paymentMethodCode).flatMap { bundle ->
            bundle.bundleOptions?.firstOrNull { it.idPsp == pspId }?.toMono() ?: Mono.empty()
        }
    }

    companion object {
        private val STUB_FEES_REQUEST =
            PaymentOptionMulti()
                .idPspList(emptyList())
                .touchpoint("IO")
                .addPaymentNoticeItem(
                    PaymentNoticeItem()
                        .paymentAmount(1)
                        .primaryCreditorInstitution("")
                        .addTransferListItem(
                            TransferListItem()
                                .transferCategory("")
                                .digitalStamp(false)
                                .creditorInstitution("")
                        )
                )
    }
}
