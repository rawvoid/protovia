package io.github.rawvoid.protovia;

/**
 * Protobuf scalar / composite type. {@link #AUTO} lets the processor infer from the Java type.
 */
public enum ProtoType {
    AUTO,
    INT32,
    INT64,
    UINT32,
    UINT64,
    SINT32,
    SINT64,
    FIXED32,
    FIXED64,
    SFIXED32,
    SFIXED64,
    BOOL,
    FLOAT,
    DOUBLE,
    STRING,
    BYTES,
    ENUM,
    MESSAGE
}
