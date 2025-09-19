package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ecommerce-payment-methods-handler")
data class PaymentMethodsHandlerConfigProperties(
    val uri: String,
    val readTimeout: Int,
    val connectionTimeout: Int,
    val apiKey: String
)
