package it.pagopa.wallet.common.tracing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import it.pagopa.wallet.common.tracing.TracingUtils.Companion.TRACEPARENT
import java.util.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class TracingUtilsTest {

    private val openTelemetry = spy(GlobalOpenTelemetry.get())
    private val tracer = spy(openTelemetry.getTracer("test-tracer"))
    private val tracingUtils = TracingUtils(openTelemetry, tracer)

    @BeforeEach
    fun setUp() {
        val textMapPropagator: TextMapPropagator =
            Mockito.spy(W3CTraceContextPropagator.getInstance())
        given { textMapPropagator.inject<Any>(any(), any<HashMap<Any, Any>>(), any()) }
            .willAnswer {
                val map: HashMap<String, String> = it.getArgument(1)
                val setter = it.getArgument<TextMapSetter<HashMap<String, String>>>(2)
                setter.set(map, TRACEPARENT, "mock_traceparent")
                null
            }
        val contextPropagators = Mockito.mock(ContextPropagators::class.java)
        given { contextPropagators.textMapPropagator }.willReturn(textMapPropagator)
        given { openTelemetry.propagators }.willReturn(contextPropagators)
        given(openTelemetry.propagators).willReturn(contextPropagators)
    }

    @Test
    fun traceMonoWithValueReturnValue() {
        val expected = 0
        val operation = Mono.just(expected)

        StepVerifier.create(tracingUtils.traceMonoQueue("test", ({ _ -> operation })))
            .expectNext(expected)
            .verifyComplete()
    }

    @Test
    fun traceMonoWithMonoErrorReturnsError() {
        val expected = RuntimeException("error!")
        val operation = Mono.error<Int>(expected)
        StepVerifier.create(tracingUtils.traceMonoQueue("test", ({ _ -> operation })))
            .expectErrorMatches { e: Throwable -> e == expected }
            .verify()
    }

    @Test
    fun shouldAddNewSpan() {
        tracingUtils.addSpan("testSpan", Attributes.empty())
        verify(tracer, times(1)).spanBuilder(eq("testSpan"))
    }

    companion object {
        fun getMock(): TracingUtils {
            val mockedTraceInfo = QueueTracingInfo(UUID.randomUUID().toString(), "", "")
            val mockedTracingUtils = Mockito.mock(TracingUtils::class.java)
            Mockito.`when`(mockedTracingUtils.traceMonoQueue<Any>(any(), any())).thenAnswer {
                invocation ->
                return@thenAnswer (invocation.getArgument<Any>(1) as (QueueTracingInfo) -> Mono<*>)
                    .invoke(mockedTraceInfo)
            }

            return mockedTracingUtils
        }
    }
}
