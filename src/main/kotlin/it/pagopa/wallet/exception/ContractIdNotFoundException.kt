package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class ContractIdNotFoundException(contractId: String) :
    ApiError("Cannot find wallet with contract id $contractId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Wallet not found", message!!)
}
