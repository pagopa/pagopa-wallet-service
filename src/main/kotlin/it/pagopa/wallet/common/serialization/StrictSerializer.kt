package it.pagopa.wallet.common.serialization

import com.azure.core.serializer.json.jackson.JacksonJsonSerializerBuilder
import com.azure.core.util.serializer.JsonSerializer
import com.azure.core.util.serializer.JsonSerializerProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule

/** [JsonSerializerProvider] that provides a JSON serializer with strict typing guarantees. */
class StrictJsonSerializerProvider : JsonSerializerProvider {
    /**
     * Return object mapper instance wrapped instance
     *
     * @return the object mapper instance
     */
    /** Object mapper associated to the [JsonSerializer] */
    private val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerModule(Jdk8Module())
            .registerModule(JavaTimeModule())
            .registerModule(kotlinModule())
            .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    /** {@inheritDoc} */
    override fun createInstance(): JsonSerializer {
        return JacksonJsonSerializerBuilder().serializer(objectMapper).build()
    }

    /**
     * Add mixin classes for override jackson annotations
     *
     * @param target target class to enrich
     * @param mixSource mix source class
     * @return this instance
     * @see ObjectMapper.addMixIn
     */
    fun addMixIn(target: Class<*>?, mixSource: Class<*>?): StrictJsonSerializerProvider {
        objectMapper.addMixIn(target, mixSource)
        return this
    }
}
