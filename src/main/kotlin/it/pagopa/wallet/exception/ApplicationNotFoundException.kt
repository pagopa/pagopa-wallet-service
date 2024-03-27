package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class ApplicationNotFoundException(private val serviceId: String) :
    ApiError("Application with id '${serviceId}' not found") {
    override fun toRestException(): RestApiException {
        return RestApiException(
            HttpStatus.NOT_FOUND,
            "Application not found",
            "Application with id '${serviceId}' not found"
        )
    }
}
