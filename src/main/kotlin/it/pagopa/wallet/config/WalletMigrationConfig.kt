package it.pagopa.wallet.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wallet.migration")
data class WalletMigrationConfig(val cardPaymentMethodId: String)
