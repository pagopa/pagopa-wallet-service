package it.pagopa.wallet.util.npg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NpgPspApiKeysConfigTest {

    private val pspApiKeyConfigurationJson =
        """
       {
            "pspId1" : "pspId1-apiKey",
            "pspId2" : "pspId2-apiKey",
            "pspId3" : "pspId3-apiKey"
       } 
    """

    private val defaultApiKey = "defaultApiKey"
    private val pspList = setOf("pspId1", "pspId2", "pspId3")

    private val npgPspApiKeysConfig =
        NpgPspApiKeysConfig.parseApiKeyConfiguration(
                jsonSecretConfiguration = pspApiKeyConfigurationJson,
                pspToHandle = pspList,
                objectMapper = jacksonObjectMapper(),
                defaultApiKey = defaultApiKey
            )
            .get()

    @ParameterizedTest
    @ValueSource(strings = ["pspId1", "pspId2", "pspId3"])
    fun `Should retrieve all psp keys successfully`(pspId: String) {
        // test
        val pspApiKey = npgPspApiKeysConfig[pspId]
        val npgDefaultApiKey = npgPspApiKeysConfig.defaultApiKey
        // assertions
        assertEquals(defaultApiKey, npgDefaultApiKey)
        assertEquals("$pspId-apiKey", pspApiKey.get())
    }

    @Test
    fun `Should throw error for invalid json configuration`() {
        // pre-requisites
        val invalidPspConfigurationJson = "{"
        // test
        val configuration =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                jsonSecretConfiguration = invalidPspConfigurationJson,
                pspToHandle = pspList,
                objectMapper = jacksonObjectMapper(),
                defaultApiKey = defaultApiKey
            )
        // assertions
        assertTrue(configuration.isLeft)
        assertEquals(
            "Npg api key configuration error: Invalid json configuration map",
            configuration.left.message
        )
    }

    @Test
    fun `Should throw error for missing required psp api key in configuration`() {
        // pre-requisites
        val pspListWithMissingKeyInConf = pspList + "missing"
        // test
        val configuration =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                jsonSecretConfiguration = pspApiKeyConfigurationJson,
                pspToHandle = pspListWithMissingKeyInConf,
                objectMapper = jacksonObjectMapper(),
                defaultApiKey = defaultApiKey
            )
        // assertions
        assertTrue(configuration.isLeft)
        assertEquals(
            "Npg api key configuration error: Misconfigured api keys. Missing keys: [missing]",
            configuration.left.message
        )
    }

    @Test
    fun `Should throw error while searching for unknown psp api key`() {
        // pre-requisites
        val missingPspId = "missing"
        // test
        val pspApiKey = npgPspApiKeysConfig[missingPspId]
        // assertions
        assertTrue(pspApiKey.isLeft)
        assertEquals(
            "Npg api key configuration error: Requested API key for PSP: [missing]. Available PSPs: [pspId3, pspId2, pspId1]",
            pspApiKey.left.message
        )
    }
}
