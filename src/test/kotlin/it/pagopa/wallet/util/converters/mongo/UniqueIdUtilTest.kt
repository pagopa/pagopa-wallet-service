package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.util.UniqueIdUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UniqueIdUtilsTest {

    @Test
    fun `generate unique id correctly`() {
        val uniqueId = UniqueIdUtils().generateUniqueId()
        Assertions.assertEquals(uniqueId.length, 18)
    }
}
