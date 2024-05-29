package it.pagopa.wallet.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "afm")
data class AfmCalculatorConfig(
    val baseUrl: String,
    val apiKey: String,
    val readTimeout: Int,
    val connectionTimeout: Int
)
