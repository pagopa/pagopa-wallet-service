package it.pagopa.wallet.client

import it.pagopa.generated.jwtIssuer.api.JwtIssuerApi
import it.pagopa.generated.jwtIssuer.model.CreateTokenRequest
import it.pagopa.generated.jwtIssuer.model.CreateTokenResponse
import it.pagopa.wallet.exception.JWTTokenGenerationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class JwtTokenIssuerClient(
    @Autowired
    @Qualifier("jwtTokenIssuerWebClient")
    private val jwtTokenIssuerWebClient: JwtIssuerApi
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createToken(createTokenRequest: CreateTokenRequest): Mono<CreateTokenResponse> {
        return jwtTokenIssuerWebClient
            .createJwtToken(createTokenRequest)
            .doOnError(WebClientResponseException::class.java) {
                logger.error("Error communicating with Jwt Issuer")
            }
            .onErrorMap { JWTTokenGenerationException() }
    }
}
