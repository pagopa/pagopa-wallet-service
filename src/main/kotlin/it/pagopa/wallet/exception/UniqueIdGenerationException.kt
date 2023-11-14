package it.pagopa.wallet.exception

import java.util.*
import org.springframework.http.HttpStatus

class UniqueIdGenerationException() : ApiError("Error when generating unique id") {
    override fun toRestException(): RestApiException {
        return RestApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal system error",
            "Error when generating unique id"
        )
    }
}
