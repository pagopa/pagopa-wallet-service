package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.WalletId
import java.util.*
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@WritingConverter
object WalletIdWriter : Converter<WalletId, String> {
    override fun convert(source: WalletId): String {
        return source.value.toString()
    }
}

@ReadingConverter
object WalletIdReader : Converter<String, WalletId> {
    override fun convert(source: String): WalletId {
        return WalletId(UUID.fromString(source))
    }
}
