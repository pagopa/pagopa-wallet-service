package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class ContractIdMissingException(val walletId: WalletId) :
    ApiError("Wallet with walletId [${walletId.value}] has no contractId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NO_CONTENT, "ContractId Missing", message!!)
}
