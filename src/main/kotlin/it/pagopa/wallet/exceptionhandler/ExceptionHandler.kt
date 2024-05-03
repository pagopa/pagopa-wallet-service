package it.pagopa.wallet.exceptionhandler

import it.pagopa.generated.wallet.model.ProblemJsonDto
import it.pagopa.generated.wallet.model.WalletApplicationDto
import it.pagopa.generated.wallet.model.WalletApplicationStatusDto
import it.pagopa.generated.wallet.model.WalletApplicationsPartialUpdateDto
import it.pagopa.wallet.exception.*
import jakarta.xml.bind.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ServerWebInputException

/**
 * Exception handler used to output a custom message in case an incoming request is invalid or an
 * api encounter an error and throw an RestApiException
 */
@RestControllerAdvice
class ExceptionHandler {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    /** RestApiException exception handler */
    @ExceptionHandler(RestApiException::class)
    fun handleException(e: RestApiException): ResponseEntity<ProblemJsonDto> {
        logger.error("Exception processing request", e)
        return ResponseEntity.status(e.httpStatus)
            .body(
                ProblemJsonDto().status(e.httpStatus.value()).title(e.title).detail(e.description)
            )
    }

    /** ApiError exception handler */
    @ExceptionHandler(ApiError::class)
    fun handleException(e: ApiError): ResponseEntity<ProblemJsonDto> {
        return handleException(e.toRestException())
    }

    /** Validation request exception handler */
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
        ServerWebInputException::class,
        ValidationException::class,
        HttpMessageNotReadableException::class,
        WebExchangeBindException::class
    )
    fun handleRequestValidationException(e: Exception): ResponseEntity<ProblemJsonDto> {

        logger.error("Input request is not valid", e)
        return ResponseEntity.badRequest()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Bad request")
                    .detail("Input request is not valid")
            )
    }

    /** Handler for generic exception */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ProblemJsonDto> {
        logger.error("Exception processing the request", e)
        return ResponseEntity.internalServerError()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .title("Error processing the request")
                    .detail("An internal error occurred processing the request")
            )
    }

    @ExceptionHandler(WalletApplicationStatusConflictException::class)
    fun walletApplicationStatusConflictExceptionHandler(
        exception: WalletApplicationStatusConflictException
    ): ResponseEntity<WalletApplicationsPartialUpdateDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                WalletApplicationsPartialUpdateDto().apply {
                    updatedApplications =
                        exception.updatedApplications.map {
                            WalletApplicationDto()
                                .name(it.key.id)
                                .status(WalletApplicationStatusDto.valueOf(it.value.name))
                        }
                    failedApplications =
                        exception.failedApplications.map {
                            WalletApplicationDto()
                                .name(it.key.id)
                                .status(WalletApplicationStatusDto.valueOf(it.value.name))
                        }
                }
            )
    }

    @ExceptionHandler(MigrationError::class)
    fun handleMigrationError(e: MigrationError): ResponseEntity<ProblemJsonDto> =
        when (e) {
            is MigrationError.WalletContractIdNotFound ->
                ProblemJsonDto()
                    .status(HttpStatus.NOT_FOUND.value())
                    .title("Cannot find wallet with given Contract Id")
                    .detail("The contract id or associated wallet does not exist")
            is MigrationError.WalletIllegalStateTransition ->
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Invalid request")
                    .detail("Cannot update Wallet details while its status is ${e.status}")
            is MigrationError.WalletAlreadyOnboarded ->
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Wallet already onboarded")
                    .detail(
                        "Cannot associated wallet ${e.walletId.value} to user cause it's already onboarded"
                    )
        }.let { ResponseEntity.status(it.status).body(it) }
}
