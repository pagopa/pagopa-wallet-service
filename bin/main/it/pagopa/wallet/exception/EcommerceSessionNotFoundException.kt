package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class EcommerceSessionNotFoundException(walletId: String, transactionId: String) :
    ApiError(
        "Cannot find ecommerce session for walletId [${walletId}] and transactionId $transactionId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Ecommerce session not found", message!!)
}
