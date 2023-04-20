package it.pagopa.wallet.exceptionhandler

import it.pagopa.wallet.exception.ApiError
import it.pagopa.wallet.exception.RestApiException
import jakarta.xml.bind.ValidationException
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    fun handleException(e: RestApiException): ResponseEntity<String> {
        logger.error("Exception processing request", e)
        return ResponseEntity.status(e.httpStatus)
            .body("Error processing request: ${e.title} - ${e.description}")
    }

    /** ApiError exception handler */
    @ExceptionHandler(ApiError::class)
    fun handleException(e: ApiError): ResponseEntity<String> {
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
    fun handleRequestValidationException(e: Exception): ResponseEntity<String> {

        logger.error("Input request is not valid", e)
        return ResponseEntity.badRequest().body("Error processing request: ${e.localizedMessage}")
    }
}
