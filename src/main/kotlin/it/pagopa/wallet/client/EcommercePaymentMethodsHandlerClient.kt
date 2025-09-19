package it.pagopa.wallet.client

import it.pagopa.generated.ecommerce.paymentmethodshandler.api.PaymentMethodsHandlerApi
import it.pagopa.generated.ecommerce.paymentmethodshandler.model.PaymentMethodResponse
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.exception.EcommercePaymentMethodException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class EcommercePaymentMethodsHandlerClient(
    @Autowired
    @Qualifier("ecommercePaymentMethodsHandlerClient")
    private val ecommercePaymentMethodHandlerClient: PaymentMethodsHandlerApi,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPaymentMethodById(paymentMethodId: String): Mono<PaymentMethodResponse> {

        val maybePaymentMethodResponse: Mono<PaymentMethodResponse> =
            try {
                logger.info("Starting getPaymentMethod given id $paymentMethodId")
                ecommercePaymentMethodHandlerClient.getPaymentMethod(paymentMethodId, "WALLET")
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }

        return maybePaymentMethodResponse
            .onErrorMap(WebClientResponseException::class.java) {
                logger.error(
                    "Error communicating with ecommerce payment-methods-handler: response: ${it.responseBodyAsString}",
                    it)
                when (it.statusCode) {
                    HttpStatus.BAD_REQUEST ->
                        EcommercePaymentMethodException(
                            description = "EcommercePaymentMethods - Bad request",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    HttpStatus.UNAUTHORIZED ->
                        EcommercePaymentMethodException(
                            description =
                                "EcommercePaymentMethods - Misconfigured EcommercePaymentMethods api key",
                            httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                        )

                    HttpStatus.INTERNAL_SERVER_ERROR ->
                        EcommercePaymentMethodException(
                            description = "EcommercePaymentMethods - internal server error",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )

                    else ->
                        EcommercePaymentMethodException(
                            description =
                                "EcommercePaymentMethods - server error: ${it.statusCode}",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )
                }
            }
            .filter {
                it.status == PaymentMethodResponse.StatusEnum.ENABLED &&
                    isValidPaymentMethodGivenWalletTypeAvailable(it.paymentTypeCode)
            }
            .switchIfEmpty(
                Mono.error(
                    EcommercePaymentMethodException(
                        description = "Invalid Payment Method",
                        httpStatusCode = HttpStatus.BAD_REQUEST,
                    )))
    }

    /**
     * Check if the returned payment method have a wallet compatible payment type code, that is, the
     * code used to uniquely identify a payment method typology
     */
    private fun isValidPaymentMethodGivenWalletTypeAvailable(
        paymentTypeCode: PaymentMethodResponse.PaymentTypeCodeEnum
    ): Boolean {
        return WalletDetailsType.entries.any { walletDetailType ->
            walletDetailType.paymentTypeCode == paymentTypeCode.toString()
        }
    }
}
