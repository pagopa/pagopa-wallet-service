package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class ApplicationNotFoundException(private val serviceId: String) :
    ApiError("Service with id '${serviceId}' not found") {
    override fun toRestException(): RestApiException {
        return RestApiException(
            HttpStatus.NOT_FOUND,
            "Service not found",
            "Service with id '${serviceId}' not found"
        )
    }
}
