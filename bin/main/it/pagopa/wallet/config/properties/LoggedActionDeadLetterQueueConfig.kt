package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("logged-action-dlq")
data class LoggedActionDeadLetterQueueConfig(
    val storageConnectionString: String,
    val storageQueueName: String,
    val ttlSeconds: Long,
    val visibilityTimeoutSeconds: Long
)
