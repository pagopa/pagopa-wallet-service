package it.pagopa.wallet.util

import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.vavr.control.Either
import it.pagopa.wallet.exception.JWTTokenGenerationException
import jakarta.validation.constraints.NotNull
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Utility class used to generate JWT tokens with claims. */
@Component
class JwtTokenUtils(
    @Autowired private val signingKeyForNpgNotification: SecretKey,
    @Autowired
    @Value("\${npg.notifications.jwt.validityTimeSeconds}")
    private val tokenValidityTimeSeconds: Int
) {
    /**
     * This method generates a jwt with specific claim
     *
     * @param transactionIdAsClaim value for transactionId JWT claim
     * @param walletIdAsClaim value for transactionId JWT claim
     * @return Mono jwt with specific claim
     */
    fun generateJwtTokenForNpgNotifications(
        @NotNull transactionIdAsClaim: String,
        @NotNull walletIdAsClaim: String
    ): Either<JWTTokenGenerationException, String> {
        return try {
            val now = Instant.now()
            val issuedAtDate = Date.from(now)
            val expiryDate =
                Date.from(now.plus(Duration.ofSeconds(tokenValidityTimeSeconds.toLong())))
            val jwtBuilder: JwtBuilder =
                Jwts.builder()
                    .setId(UUID.randomUUID().toString()) // jti
                    .setIssuedAt(issuedAtDate) // iat
                    .setExpiration(expiryDate) // exp
                    .signWith(signingKeyForNpgNotification)

            jwtBuilder.claim(TRANSACTION_ID_CLAIM, transactionIdAsClaim) // claim TransactionId
            jwtBuilder.claim(WALLET_ID_CLAIM, walletIdAsClaim) // claim WalletId

            Either.right(jwtBuilder.compact())
        } catch (e: JwtException) {
            Either.left(JWTTokenGenerationException())
        }
    }

    companion object {
        /** The claim transactionId */
        const val TRANSACTION_ID_CLAIM = "transactionId"
        /** The claim walletId */
        const val WALLET_ID_CLAIM = "walletId"
    }
}
