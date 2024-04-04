package it.pagopa.wallet.reactormdc

import java.util.stream.Collectors
import org.reactivestreams.Subscription
import org.slf4j.MDC
import reactor.core.CoreSubscriber
import reactor.util.context.Context

internal class ContextLifterMDC<T>(private val coreSubscriber: CoreSubscriber<T>) :
    CoreSubscriber<T> {

    override fun onSubscribe(subscription: Subscription) {
        coreSubscriber.onSubscribe(subscription)
    }

    override fun onNext(obj: T) {
        copyToMdc(coreSubscriber.currentContext())
        coreSubscriber.onNext(obj)
    }

    override fun onError(t: Throwable) {
        coreSubscriber.onError(t)
    }

    override fun onComplete() {
        coreSubscriber.onComplete()
    }

    override fun currentContext(): Context {
        return coreSubscriber.currentContext()
    }

    /**
     * Extension function for the Reactor [Context]. Copies the current context to the MDC, if
     * context is empty clears the MDC. State of the MDC after calling this method should be same as
     * Reactor [Context] state. One thread-local access only.
     */
    private fun copyToMdc(context: Context) {
        if (!context.isEmpty) {
            val mdcContextMap = MDC.getCopyOfContextMap().orEmpty().toMutableMap()
            val reactorContextMap =
                context
                    .stream()
                    .collect(
                        Collectors.toMap({ e -> e.key.toString() }, { e -> e.value.toString() })
                    )
            if (
                reactorContextMap.getOrDefault("contextKey", "") ==
                    mdcContextMap.getOrDefault("contextKey", "")
            ) {
                reactorContextMap.putAll(mdcContextMap)
                MDC.setContextMap(reactorContextMap)
            } else {
                mdcContextMap.putAll(reactorContextMap)
                MDC.setContextMap(mdcContextMap)
            }
        } else {
            MDC.clear()
        }
    }
}
