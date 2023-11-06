package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class NoCardsSessionValidateRequestException(walletId: WalletId) :
    ApiError(
        "Validate session is not allowed for the walletId $walletId with payment method different from cards"
    ) {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.METHOD_NOT_ALLOWED, "Not allowed", message!!)
}
