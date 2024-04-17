package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.Client
import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletClientConfigurationException(val walletId: WalletId, val clientId: Client.Id) :
    ApiError("Could not update wallet client ${clientId.name} for wallet id ${walletId.value}") {
    override fun toRestException(): RestApiException =
        RestApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            message!!,
            "Wallet client configuration update failed",
        )
}
