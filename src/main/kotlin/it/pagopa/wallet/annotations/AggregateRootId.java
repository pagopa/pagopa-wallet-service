package it.pagopa.wallet.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * <p>
 * An identifier for aggregate roots. Note that this identifier should be
 * globally unique.
 * </p>
 */
@Documented
@Target(
    {
            FIELD,
            METHOD
    }
)
public @interface AggregateRootId {
}
