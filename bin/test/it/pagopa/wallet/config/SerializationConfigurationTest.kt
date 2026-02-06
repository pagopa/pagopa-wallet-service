package it.pagopa.wallet.config

import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.generated.wallet.model.WalletClientDto
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class SerializationConfigurationTest {

    private val objectMapper =
        SerializationConfiguration().objectMapperBuilder().build<ObjectMapper>()

    @Test
    fun shouldNotSerializeNullValues() {
        val sampleObject = WalletClientDto().status(null)
        assertThat(objectMapper.writeValueAsString(sampleObject), not(containsString("status")))
    }
}
