package it.pagopa.wallet.config

import it.pagopa.wallet.util.converters.mongo.PaymentInstrumentIdReader
import it.pagopa.wallet.util.converters.mongo.PaymentInstrumentIdWriter
import it.pagopa.wallet.util.converters.mongo.WalletIdReader
import it.pagopa.wallet.util.converters.mongo.WalletIdWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

@Configuration
class MongoConfiguration {
    @Bean
    fun mongoCustomConversions() =
        MongoCustomConversions(
            listOf(
                WalletIdWriter,
                WalletIdReader,
                PaymentInstrumentIdReader,
                PaymentInstrumentIdWriter
            )
        )
}
