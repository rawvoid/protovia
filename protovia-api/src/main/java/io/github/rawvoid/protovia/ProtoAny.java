package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.ProtoReader;

import java.util.Arrays;

/**
 * {@code google.protobuf.Any}: a type URL plus the serialized payload.
 * {@code pack}/{@code unpack} by {@code Class} live on {@code ProtoVia} so this
 * type does not depend on codec lookup. Use {@link #unpack(ProtoCodec)} when
 * you already have a codec.
 *
 * <p>{@code type_url} is {@code type.googleapis.com/<protoFullName>}. The proto
 * full name comes from {@code @ProtoMessage(packageName, name)}, not the Java
 * FQCN. A missing package yields a short name that will not match an official
 * type that lives under {@code package foo.bar}.
 */
public final class ProtoAny {

    public static final String TYPE_URL_PREFIX = "type.googleapis.com";

    private static final byte[] EMPTY = new byte[0];

    private final String typeUrl;
    private final byte[] value;

    public ProtoAny(String typeUrl, byte[] value) {
        this.typeUrl = typeUrl == null ? "" : typeUrl;
        this.value = value == null || value.length == 0 ? EMPTY : value;
    }

    public String typeUrl() {
        return typeUrl;
    }

    public byte[] value() {
        return value;
    }

    public boolean is(String protoFullName) {
        return typeName(typeUrl).equals(protoFullName);
    }

    public boolean is(ProtoCodec<?> codec) {
        return is(codec.protoFullName());
    }

    public <T> T unpack(ProtoCodec<T> codec) {
        if (!is(codec)) {
            throw new ProtoException("Any type mismatch: " + typeUrl + " is not " + codec.protoFullName());
        }
        return codec.readFrom(new ProtoReader(value));
    }

    /**
     * The type name after the last {@code /}, matching official {@code Any}.
     * No slash means the name is empty.
     */
    public static String typeName(String typeUrl) {
        if (typeUrl == null) {
            return "";
        }
        int slash = typeUrl.lastIndexOf('/');
        return slash < 0 ? "" : typeUrl.substring(slash + 1);
    }

    public static String typeUrl(String protoFullName) {
        return typeUrl(TYPE_URL_PREFIX, protoFullName);
    }

    public static String typeUrl(String prefix, String protoFullName) {
        return prefix.endsWith("/") ? prefix + protoFullName : prefix + "/" + protoFullName;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtoAny other
                && typeUrl.equals(other.typeUrl)
                && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * typeUrl.hashCode() + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "ProtoAny{" + typeUrl + ", " + value.length + " bytes}";
    }
}
