package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.PaymentInstrumentId
import java.util.*
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@WritingConverter
object PaymentInstrumentIdWriter : Converter<PaymentInstrumentId, String> {
    override fun convert(source: PaymentInstrumentId): String {
        return source.value.toString()
    }
}

@ReadingConverter
object PaymentInstrumentIdReader : Converter<String, PaymentInstrumentId> {
    override fun convert(source: String): PaymentInstrumentId {
        return PaymentInstrumentId(UUID.fromString(source))
    }
}
