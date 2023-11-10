package it.pagopa.wallet.util.converters.mongo

import it.pagopa.wallet.exception.UniqueIdGenerationException
import it.pagopa.wallet.repositories.UniqueIdTemplateWrapper
import it.pagopa.wallet.util.UniqueIdUtils
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.http.HttpStatus
import reactor.test.StepVerifier

class UniqueIdUtilsTest {
    private val uniqueIdTemplateWrapper: UniqueIdTemplateWrapper =
        Mockito.mock(UniqueIdTemplateWrapper::class.java)

    private val uniqueIdUtils: UniqueIdUtils = UniqueIdUtils(uniqueIdTemplateWrapper)

    private val PRODUCT_PREFIX = "W"

    @Test
    fun shouldGenerateUniqueIdGenerateException() {
        Mockito.`when`(uniqueIdTemplateWrapper.saveIfAbsent(any(), any())).thenReturn(false)
        StepVerifier.create(uniqueIdUtils.generateUniqueId())
            .expectErrorMatches {
                it as UniqueIdGenerationException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
        Mockito.verify(uniqueIdTemplateWrapper, Mockito.times(3)).saveIfAbsent(any(), any())
    }

    @Test
    fun shouldGenerateUniqueIdWithRetry() {
        Mockito.`when`(uniqueIdTemplateWrapper.saveIfAbsent(any(), any()))
            .thenReturn(false, false, true)
        StepVerifier.create(uniqueIdUtils.generateUniqueId())
            .expectNextMatches { response ->
                response.length == 18 && response.startsWith(PRODUCT_PREFIX)
            }
            .verifyComplete()
        Mockito.verify(uniqueIdTemplateWrapper, Mockito.times(3)).saveIfAbsent(any(), any())
    }

    @Test
    fun shouldGenerateUniqueIdNoRetry() {
        Mockito.`when`(uniqueIdTemplateWrapper.saveIfAbsent(any(), any())).thenReturn(true)
        StepVerifier.create(uniqueIdUtils.generateUniqueId())
            .expectNextMatches { response ->
                response.length == 18 && response.startsWith(PRODUCT_PREFIX)
            }
            .verifyComplete()
        Mockito.verify(uniqueIdTemplateWrapper, Mockito.times(1)).saveIfAbsent(any(), any())
    }
}
