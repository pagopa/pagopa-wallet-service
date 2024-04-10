package it.pagopa.wallet.util.npg

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.vavr.control.Either
import it.pagopa.wallet.exception.NpgApiKeyConfigurationException
import it.pagopa.wallet.exception.NpgApiKeyMissingPspRequestedException

/** This class takes care of parsing NPG per-PSP API keys from the JSON configuration */
class NpgPspApiKeysConfig
internal constructor(private val configuration: Map<String, String>, val defaultApiKey: String) {

    /**
     * Retrieves an API key for a specific PSP
     *
     * @param psp the PSP you want the API key for
     * @return the API key corresponding to the input PSP
     */
    operator fun get(psp: String): Either<NpgApiKeyMissingPspRequestedException, String> {
        return if (configuration.containsKey(psp)) {
            Either.right(configuration[psp])
        } else {
            Either.left(NpgApiKeyMissingPspRequestedException(psp, configuration.keys))
        }
    }

    companion object {
        /**
         * Return a map where valued with each psp id - api keys entries
         *
         * @param jsonSecretConfiguration - secret configuration json representation
         * @param pspToHandle - psp expected to be present into configuration json
         * @param objectMapper - [ObjectMapper] used to parse input JSON
         * @param defaultApiKey - the default api key
         * @return either the parsed map or the related parsing exception
         */
        fun parseApiKeyConfiguration(
            jsonSecretConfiguration: String,
            pspToHandle: Set<String>,
            objectMapper: ObjectMapper,
            defaultApiKey: String
        ): Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> {
            return try {
                val apiKeys: Map<String, String> =
                    objectMapper.readValue(
                        jsonSecretConfiguration,
                        object : TypeReference<HashMap<String, String>>() {}
                    )
                val missingKeys = pspToHandle - apiKeys.keys
                if (missingKeys.isNotEmpty()) {
                    Either.left(
                        NpgApiKeyConfigurationException(
                            "Misconfigured api keys. Missing keys: $missingKeys"
                        )
                    )
                } else Either.right(NpgPspApiKeysConfig(apiKeys, defaultApiKey))
            } catch (_: JacksonException) {
                // exception here is ignored on purpose in order to avoid secret configuration
                // logging in case of wrong configured json string object
                Either.left(NpgApiKeyConfigurationException("Invalid json configuration map"))
            }
        }
    }
}
