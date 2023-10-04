package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.domain.wallets.PaymentMethodId
import java.util.*
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

/*
 * @formatter:off
 *
 * Warning kotlin:S6516 - Functional interface implementations should use lambda expressions
 * Suppressed to allow for this converter implementation to be tested and not to harm
 * readability when configuring converters (see it.pagopa.wallet.config.MongoConfiguration#mongoCustomConversions)
 *
 * @formatter:on
 */
@SuppressWarnings("kotlin:S6516")
@WritingConverter
object PaymentMethodIdWriter : Converter<PaymentMethodId, String> {
    override fun convert(source: PaymentMethodId): String {
        return source.value.toString()
    }
}

/*
 * @formatter:off
 *
 * Warning kotlin:S6516 - Functional interface implementations should use lambda expressions
 * Suppressed to allow for this converter implementation to be tested and not to harm
 * readability when configuring converters (see it.pagopa.wallet.config.MongoConfiguration#mongoCustomConversions)
 *
 * @formatter:on
 */
@SuppressWarnings("kotlin:S6516")
@ReadingConverter
object PaymentMethodIdReader : Converter<String, PaymentMethodId> {
    override fun convert(source: String): PaymentMethodId {
        return PaymentMethodId(UUID.fromString(source))
    }
}
