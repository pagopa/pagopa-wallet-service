package it.pagopa.wallet.domain.wallets.details

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CardBrandTest {
    companion object {
        @JvmStatic
        fun expectedValues(): Stream<Arguments> =
            Stream.of(
                Arguments.of("MASTERCARD", CardBrand("MC")),
                Arguments.of("MASTERCARD", CardBrand("MASTERCARD")),
                Arguments.of("VISA", CardBrand("VISA")),
            )
    }

    @ParameterizedTest
    @MethodSource("expectedValues")
    fun cardBrandEncodesValueCorrectly(expected: String, brand: CardBrand) {
        assertEquals(expected, brand.value)
    }

    @Test
    fun equalityForRemappedValueIsPreserved() {
        assertEquals(CardBrand("MC"), CardBrand("MASTERCARD"))
    }
}
