package it.pagopa.wallet.client

import io.vavr.control.Try
import it.pagopa.generated.ecommerce.paymentmethods.v2.model.Bundle
import it.pagopa.generated.ecommerce.paymentmethods.v2.model.CalculateFeeResponse
import it.pagopa.generated.ecommerce.paymentmethods.v2.model.PaymentMethodStatus
import it.pagopa.wallet.config.properties.PaymentMethodsHandlerConfigProperties
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
 * backed by the calculate fees endpoint.
 *
 * It supports both backends governed by the single feature flag
 * `ecommerce-payment-methods-handler.enabled`:
 * - when `true`, it calls the payment-methods-handler `calculateFees` and maps the handler response
 *   into the v2 model before returning;
 * - when `false`, it calls the legacy payment-methods-service v2 `calculateFees`.
 *
 * In both cases the public methods return the v2 model so [it.pagopa.wallet.services.WalletService]
 * is unaffected by the switch.
 *
 * Note: It could be backed by dedicated Psp API to get details about it without using calculate
 * fees, so keep this client separated by PaymentMethodsClient
 */
@Component
class PspDetailClient(
    @Qualifier("ecommercePaymentMethodsWebClientV2")
    private val paymentMethodsApiV2:
        it.pagopa.generated.ecommerce.paymentmethods.v2.api.PaymentMethodsApi,
    @Qualifier("ecommercePaymentMethodsHandlerWebClient")
    private val paymentMethodsHandlerApi:
        it.pagopa.generated.ecommerce.paymentmethodshandler.api.PaymentMethodsHandlerApi,
    private val paymentMethodsHandlerConfigProperties: PaymentMethodsHandlerConfigProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPspList(paymentMethodId: PaymentMethodId): Mono<CalculateFeeResponse> {
        logger.info("Getting psp list for payment method id [{}]", paymentMethodId.value)
        val feesResponse =
            if (paymentMethodsHandlerConfigProperties.enabled) {
                getPspListByHandlerApi(paymentMethodId)
            } else {
                getPspListByApiV2(paymentMethodId)
            }
        return feesResponse
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

    private fun getPspListByApiV2(paymentMethodId: PaymentMethodId): Mono<CalculateFeeResponse> =
        Try.of {
                paymentMethodsApiV2.calculateFees(
                    paymentMethodId.value.toString(),
                    STUB_TRANSACTION_ID,
                    STUB_FEES_REQUEST_V2,
                    DEFAULT_MAX_OCCURRENCES)
            }
            .fold({ error -> Mono.error(error) }, { it })

    private fun getPspListByHandlerApi(
        paymentMethodId: PaymentMethodId
    ): Mono<CalculateFeeResponse> =
        Try.of {
                paymentMethodsHandlerApi.calculateFees(
                    paymentMethodId.value.toString(),
                    X_CLIENT_ID,
                    STUB_FEES_REQUEST_HANDLER,
                    DEFAULT_MAX_OCCURRENCES,
                    null)
            }
            .fold({ error -> Mono.error(error) }, { it })
            .map { toV2CalculateFeeResponse(it) }

    fun getPspDetails(pspId: String, paymentMethodId: PaymentMethodId): Mono<Bundle> {
        logger.info("Getting info about pspId [{}]", pspId)
        return getPspList(paymentMethodId).flatMap { bundle ->
            bundle.bundles.firstOrNull { it.idPsp == pspId }?.toMono() ?: Mono.empty()
        }
    }

    companion object {
        private const val X_CLIENT_ID = "IO"
        private const val STUB_TRANSACTION_ID = ""
        private const val DEFAULT_MAX_OCCURRENCES = 1000

        private val STUB_FEES_REQUEST_V2 =
            it.pagopa.generated.ecommerce.paymentmethods.v2.model
                .CalculateFeeRequest()
                .idPspList(emptyList())
                .touchpoint("IO")
                .addPaymentNoticesItem(
                    it.pagopa.generated.ecommerce.paymentmethods.v2.model
                        .PaymentNotice()
                        .paymentAmount(1)
                        .primaryCreditorInstitution("")
                        .addTransferListItem(
                            it.pagopa.generated.ecommerce.paymentmethods.v2.model
                                .TransferListItem()
                                .transferCategory("")
                                .digitalStamp(false)
                                .creditorInstitution("")))
                .isAllCCP(false)

        private val STUB_FEES_REQUEST_HANDLER =
            it.pagopa.generated.ecommerce.paymentmethodshandler.model
                .CalculateFeeRequest()
                .idPspList(emptyList())
                .touchpoint("IO")
                .addPaymentNoticesItem(
                    it.pagopa.generated.ecommerce.paymentmethodshandler.model
                        .PaymentNotice()
                        .paymentAmount(1L)
                        .primaryCreditorInstitution("")
                        .addTransferListItem(
                            it.pagopa.generated.ecommerce.paymentmethodshandler.model
                                .TransferListItem()
                                .transferCategory("")
                                .digitalStamp(false)
                                .creditorInstitution("")))
                .isAllCCP(false)

        /**
         * Maps the handler `CalculateFeeResponse` into the v2 model so that the handler branch and
         * the legacy v2 branch return the same type toward WalletService. Handler-only fields
         * (`paymentTypeCode`) have no v2 target and are dropped; the handler `MAINTENANCE` status
         * has no v2 equivalent and is mapped to `null`. None of the dropped fields are consumed
         * downstream (only `Bundle.idPsp` and `Bundle.pspBusinessName` are read).
         */
        private fun toV2CalculateFeeResponse(
            handler: it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
        ): CalculateFeeResponse =
            CalculateFeeResponse()
                .paymentMethodName(handler.paymentMethodName)
                .paymentMethodDescription(handler.paymentMethodDescription)
                .paymentMethodStatus(toV2PaymentMethodStatus(handler.paymentMethodStatus))
                .belowThreshold(handler.belowThreshold)
                .asset(handler.asset)
                .brandAssets(handler.brandAssets)
                .bundles(handler.bundles.map { toV2Bundle(it) })

        private fun toV2PaymentMethodStatus(
            status:
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse.PaymentMethodStatusEnum?
        ): PaymentMethodStatus? =
            when (status) {
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                    .PaymentMethodStatusEnum
                    .ENABLED -> PaymentMethodStatus.ENABLED
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                    .PaymentMethodStatusEnum
                    .DISABLED -> PaymentMethodStatus.DISABLED
                it.pagopa.generated.ecommerce.paymentmethodshandler.model.CalculateFeeResponse
                    .PaymentMethodStatusEnum
                    .MAINTENANCE -> null
                null -> null
            }

        private fun toV2Bundle(
            handler: it.pagopa.generated.ecommerce.paymentmethodshandler.model.Bundle
        ): Bundle =
            Bundle()
                .abi(handler.abi)
                .bundleDescription(handler.bundleDescription)
                .idBrokerPsp(handler.idBrokerPsp)
                .idBundle(handler.idBundle)
                .idChannel(handler.idChannel)
                .idPsp(handler.idPsp)
                .onUs(handler.onUs)
                .paymentMethod(handler.paymentMethod)
                .taxPayerFee(handler.taxPayerFee)
                .touchpoint(handler.touchpoint)
                .pspBusinessName(handler.pspBusinessName)
    }
}
