package it.pagopa.wallet.exception

/**
 * Exception class wrapping checked exceptions that can occur during jwt generation
 *
 * @see it.pagopa.wallet.client.JwtTokenIssuerClient
 */
class JWTTokenGenerationException
/**
 * Constructor with fixed error message
 *
 * @see RuntimeException
 */
: RuntimeException("JWT token generation error")
