package io.github.rawvoid.protovia.processor.model;

/**
 * How a member is encoded on the wire.
 *
 * @author Rawvoid
 */
public enum FieldKind {
    SCALAR,
    ENUM,
    MESSAGE,
    REPEATED,
    MAP,
    ONEOF
}
