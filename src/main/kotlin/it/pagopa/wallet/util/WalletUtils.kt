package it.pagopa.wallet.util

import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.domain.wallets.details.PayPalDetails
import java.net.URI
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WalletUtils(@Autowired val logoMapping: Map<String, URI>) {

    companion object {
        const val UNKNOWN_LOGO_KEY = "UNKNOWN"
    }

    fun getLogo(wallet: Wallet): URI =
        requireNotNull(
            when (val walletDetail = wallet.details) {
                is CardDetails -> logoMapping[walletDetail.brand.toString()]
                is PayPalDetails -> logoMapping[walletDetail.type.toString()]
                else -> logoMapping[UNKNOWN_LOGO_KEY]
            }
        )
}
