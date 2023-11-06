package it.pagopa.wallet.exception

import java.util.*
import org.springframework.http.HttpStatus

class SessionNotFoundException(orderId: UUID) :
    ApiError("Cannot find session with orderId $orderId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Session not found", message!!)
}
