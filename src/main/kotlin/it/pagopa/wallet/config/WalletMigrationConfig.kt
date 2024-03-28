package it.pagopa.wallet.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for Wallet migration process
 *
 * @property cardPaymentMethodId The default payment method id used by migrated wallet
 * @property defaultApplicationId The default application will be enabled on migrated wallet
 */
@ConfigurationProperties(prefix = "wallet.migration")
data class WalletMigrationConfig(val cardPaymentMethodId: String, val defaultApplicationId: String)
