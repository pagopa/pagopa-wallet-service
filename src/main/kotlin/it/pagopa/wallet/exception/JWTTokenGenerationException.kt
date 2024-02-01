package it.pagopa.wallet.exception

/**
 * Exception class wrapping checked exceptions that can occur during jwt generation
 *
 * @see it.pagopa.wallet.util.JwtTokenUtils
 */
class JWTTokenGenerationException
/**
 * Constructor with fixed error message
 *
 * @see RuntimeException
 */
: RuntimeException("JWT token generation error")
