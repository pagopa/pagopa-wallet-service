package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ecommerce-payment-methods")
data class PaymentMethodsConfigProperties(
    val uri: String,
    val uriV2: String,
    val readTimeout: Int,
    val connectionTimeout: Int,
    val apiKey: String
)
