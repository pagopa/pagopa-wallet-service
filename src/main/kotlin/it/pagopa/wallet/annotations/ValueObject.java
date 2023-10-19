package it.pagopa.wallet.annotations;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * <p>
 * An annotation for value objects, as in the Domain Driven Design (DDD) sense.
 * Value objects should be immutable classes where each instance derives its
 * semantics from the values it contains. Their characterizing feature is that
 * value objects have no identity.
 * </p>
 * <p>
 * Value objects can have some form of input validation.
 * </p>
 * <p>
 * In practice, you can implement them via Lombok's {@link lombok.Value}, Java
 * records or Kotlin's data classes.
 * </p>
 */
@Target(TYPE)
@Documented
public @interface ValueObject {
}
