package it.pagopa.wallet.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FiscalCodeHasherTest {

    @Test
    fun `should generate consistent hash for same fiscal code`() {
        val fiscalCode = "RSSMRA80A01H501U"

        val hash1 = FiscalCodeHasher.hashFiscalCode(fiscalCode)
        val hash2 = FiscalCodeHasher.hashFiscalCode(fiscalCode)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `should generate different hashes for different fiscal codes`() {
        val fiscalCode1 = "RSSMRA80A01H501U"
        val fiscalCode2 = "BNCLRD85T58G702X"

        val hash1 = FiscalCodeHasher.hashFiscalCode(fiscalCode1)
        val hash2 = FiscalCodeHasher.hashFiscalCode(fiscalCode2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `should generate SHA-256 hash of correct length`() {
        val fiscalCode = "RSSMRA80A01H501U"

        val hash = FiscalCodeHasher.hashFiscalCode(fiscalCode)

        // sha-256 hash in hex format -> 64 chars
        assertEquals(64, hash.length)
    }

    @Test
    fun `should generate hash containing only hexadecimal characters`() {
        val fiscalCode = "RSSMRA80A01H501U"

        val hash = FiscalCodeHasher.hashFiscalCode(fiscalCode)

        // check all chars are valid hex chars (0-9, a-f)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `should be case insensitive for valid fiscal codes`() {
        val fiscalCode1 = "RSSMRA80A01H501U"
        val fiscalCode2 = "rssmra80a01h501u"
        val fiscalCode3 = "RsSmRa80A01h501U"

        val hash1 = FiscalCodeHasher.hashFiscalCode(fiscalCode1)
        val hash2 = FiscalCodeHasher.hashFiscalCode(fiscalCode2)
        val hash3 = FiscalCodeHasher.hashFiscalCode(fiscalCode3)

        assertEquals(hash1, hash2)
        assertEquals(hash1, hash3)
    }

    @Test
    fun `should produce deterministic output`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val hashes = (1..100).map { FiscalCodeHasher.hashFiscalCode(fiscalCode) }

        // all hashes should be identical
        assertTrue(hashes.all { it == hashes.first() })
    }
}
