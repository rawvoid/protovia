package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.*;

/**
 * Field number of one permitted subtype of a {@link ProtoOneof} sealed type.
 * The type may be top-level or nested. The number belongs to the parent message.
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoOneofCase {

    /**
     * Parent-message field number for this case, in {@code [1, 536870911]}.
     */
    int value();
}
