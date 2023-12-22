package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.services.ServiceName
import org.springframework.http.HttpStatus

class ServiceNameNotFoundException(private val serviceName: ServiceName) :
    ApiError("Service with name '${serviceName.name}' not found") {
    override fun toRestException(): RestApiException {
        return RestApiException(
            HttpStatus.NOT_FOUND,
            "Service not found",
            "Service with name '${serviceName.name}' not found"
        )
    }
}
