package it.pagopa.wallet

import it.pagopa.wallet.repositories.LoggingEventRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class ApplicationTest {

    @MockBean private lateinit var loggingEventRepository: LoggingEventRepository

    @Test
    fun contextLoads() {
        // check only if the context is loaded
        Assertions.assertTrue(true)
    }
}
