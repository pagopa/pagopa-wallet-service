package it.pagopa.wallet.client

import it.pagopa.generated.ecommerce.api.PaymentMethodsApi
import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.ecommerce.model.PaymentMethodStatus
import it.pagopa.wallet.domain.details.WalletDetailsType
import it.pagopa.wallet.exception.EcommercePaymentMethodException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class EcommercePaymentMethodsClient(
    @Autowired
    @Qualifier("ecommercePaymentMethodsWebClient")
    private val ecommercePaymentMethodsClient: PaymentMethodsApi,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPaymentMethodById(paymentMethodId: String): Mono<PaymentMethodResponse> {

        val maybePaymentMethodResponse: Mono<PaymentMethodResponse> =
            try {
                logger.info("Starting getPaymentMethod given id $paymentMethodId")
                ecommercePaymentMethodsClient.getPaymentMethod(paymentMethodId)
            } catch (e: WebClientResponseException) {
                Mono.error(e)
            }

        return maybePaymentMethodResponse
            .onErrorMap(WebClientResponseException::class.java) {
                logger.error(
                    "Error communicating with ecommerce payment-methods: response: ${it.responseBodyAsString}",
                    it
                )
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
                PaymentMethodStatus.ENABLED == it.status &&
                    isValidPaymentMethodGivenWalletTypeAvailable(it.name)
            }
            .switchIfEmpty(
                Mono.error(
                    EcommercePaymentMethodException(
                        description = "Invalid Payment Method",
                        httpStatusCode = HttpStatus.BAD_REQUEST,
                    )
                )
            )
    }

    private fun isValidPaymentMethodGivenWalletTypeAvailable(paymentMethodName: String): Boolean {
        return WalletDetailsType.values().any { it.name == paymentMethodName }
    }
}
