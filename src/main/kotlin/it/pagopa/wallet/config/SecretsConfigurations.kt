package it.pagopa.wallet.config

import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.DecodingException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import javax.crypto.SecretKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecretsConfigurations {
    @Bean
    fun signingKeyForNpgNotification(
        @Value("\${npg.notifications.jwt.secretKey}") signingKeyForNpgNotificationAsString: String
    ): SecretKey {
        return jwtSigningKey(signingKeyForNpgNotificationAsString)
    }

    private fun jwtSigningKey(signingKeyForNpgNotificationAsString: String): SecretKey {
        return try {
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingKeyForNpgNotificationAsString))
        } catch (e: WeakKeyException) {
            throw IllegalStateException("Invalid configured JWT secret key", e)
        } catch (e: DecodingException) {
            throw IllegalStateException("Invalid configured JWT secret key", e)
        }
    }
}
