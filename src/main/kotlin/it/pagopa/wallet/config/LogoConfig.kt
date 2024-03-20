package it.pagopa.wallet.config

import it.pagopa.generated.wallet.model.CardBrandDto
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import it.pagopa.wallet.util.WalletUtils
import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LogoConfig {

    @Bean
    fun walletLogoMapping(
        @Value("#{\${wallet.logo_mapping}}") logoMapping: Map<String, String>
    ): Map<String, URI> {
        val transformedLogoMapping = logoMapping.mapValues { URI.create(it.value) }
        val expectedKeys =
            (CardBrandDto.values().map { it.toString() } +
                    WalletDetailsType.values()
                        .filter { it != WalletDetailsType.CARDS }
                        .map { it.toString() } +
                    WalletUtils.UNKNOWN_LOGO_KEY)
                .toSet()
        val missingKeys = expectedKeys - transformedLogoMapping.keys
        check(missingKeys.isEmpty()) {
            "Invalid logo configuration map, missing logo entries for the following keys: $missingKeys"
        }
        return transformedLogoMapping
    }
}
