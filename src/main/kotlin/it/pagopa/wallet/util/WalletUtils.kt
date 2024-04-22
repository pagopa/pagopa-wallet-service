package it.pagopa.wallet.util

import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.wallet.domain.wallets.OnboardingChannel
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.domain.wallets.details.PayPalDetails
import it.pagopa.wallet.exception.InvalidRequestException
import java.net.URI
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WalletUtils(@Autowired val logoMapping: Map<String, URI>) {

    companion object {
        const val UNKNOWN_LOGO_KEY = "UNKNOWN"
        val VALID_ONBOARDING_CHANNELS = OnboardingChannel.values().map { it.toString() }.toSet()
    }

    fun getLogo(wallet: Wallet): URI =
        requireNotNull(
            when (val walletDetail = wallet.details) {
                is CardDetails -> logoMapping[walletDetail.brand.value]
                is PayPalDetails -> logoMapping[walletDetail.type.name]
                else -> logoMapping[UNKNOWN_LOGO_KEY]
            }
        )
}

fun ClientIdDto.toOnboardingChannel(): OnboardingChannel =
    if (WalletUtils.VALID_ONBOARDING_CHANNELS.contains(this.toString())) {
        OnboardingChannel.valueOf(this.toString())
    } else {
        throw InvalidRequestException(
            "Input clientId: [$this] is unknown. Handled onboarding channels: ${WalletUtils.VALID_ONBOARDING_CHANNELS}"
        )
    }
