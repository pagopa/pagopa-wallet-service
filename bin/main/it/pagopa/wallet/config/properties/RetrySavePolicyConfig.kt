package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logging-event.retry-save")
data class RetrySavePolicyConfig(
    val maxAttempts: Long,
    val intervalInSeconds: Long,
    val emitBusyLoopDurationInMillis: Long
) {}
