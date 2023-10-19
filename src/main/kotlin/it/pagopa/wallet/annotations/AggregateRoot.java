package it.pagopa.wallet.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * <p>
 * An annotation for aggregate roots, as in the Domain Driven Design (DDD)
 * sense. Aggregate roots are composites of entities and value objects that are
 * responsible for maintaining business logic invariants.
 * </p>
 * <p>
 * Each aggregate root has an identifier (an
 * {@link it.pagopa.wallet.annotations.AggregateRootId}) which can be used to
 * refer to this aggregate root by other aggregate roots. Entities inside
 * aggregate roots should never be referenced by external aggregate roots.
 * </p>
 */
@Documented
@Target(TYPE)
public @interface AggregateRoot {
}
