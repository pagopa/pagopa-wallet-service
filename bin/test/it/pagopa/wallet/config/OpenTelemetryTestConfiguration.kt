package it.pagopa.wallet.config

import it.pagopa.wallet.common.tracing.TracingUtils
import it.pagopa.wallet.common.tracing.TracingUtilsTest
import it.pagopa.wallet.common.tracing.WalletTracing
import org.mockito.kotlin.spy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class OpenTelemetryTestConfiguration {

    @Primary @Bean fun tracingUtils() = TracingUtilsTest.getMock()

    @Primary @Bean fun walletTracing(tracingUtils: TracingUtils) = spy(WalletTracing(tracingUtils))
}
