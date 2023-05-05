package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class ContractIdNotFoundException() : ApiError("Cannot find wallet with specified contract id") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Wallet not found", message!!)
}
