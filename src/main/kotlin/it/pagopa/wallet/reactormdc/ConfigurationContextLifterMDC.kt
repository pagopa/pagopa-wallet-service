package it.pagopa.wallet.reactormdc

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import reactor.core.publisher.Operators

@Configuration
class ConfigurationContextLifterMDC {
    private val mdcContextReactorKey: String = ConfigurationContextLifterMDC::class.java.name

    @PostConstruct
    private fun contextOperatorHook() {
        Hooks.onEachOperator(
            mdcContextReactorKey,
            Operators.lift { _, coreSubscriber -> ContextLifterMDC(coreSubscriber) }
        )
    }

    @PreDestroy
    private fun cleanupHook() {
        Hooks.resetOnEachOperator(mdcContextReactorKey)
    }
}
