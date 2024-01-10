package it.pagopa.wallet.domain.wallets.details

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BinTest {

    private val validBin = "42424242"
    private val invalidBin = "42424"

    @Test
    fun `can instantiate a Bin from valid bin`() {
        val bin = Bin(validBin)
        Assertions.assertEquals(validBin, bin.bin)
    }

    @Test
    fun `can't instantiate a Bin from valid invalid Bin`() {
        assertThrows<IllegalArgumentException> { Bin(invalidBin) }
    }
}
