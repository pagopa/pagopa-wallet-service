package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("expiration-queue")
data class ExpirationQueueConfig(
    val storageConnectionString: String,
    val storageQueueName: String,
    val ttlSeconds: Long,
    val timeoutWalletExpired: Long
)
