package it.pagopa.wallet.exception

import java.util.UUID
import org.springframework.http.HttpStatus

class ServiceNotFoundException(private val serviceId: UUID) :
    ApiError("Service with id '${serviceId}' not found") {
    override fun toRestException(): RestApiException {
        return RestApiException(
            HttpStatus.NOT_FOUND,
            "Service not found",
            "Service with id '${serviceId}' not found"
        )
    }
}
