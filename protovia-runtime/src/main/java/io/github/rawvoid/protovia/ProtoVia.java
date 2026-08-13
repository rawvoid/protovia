package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.runtime.CodecLookup;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.SizeCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Entry point for Protobuf serialization of {@code @ProtoMessage} entities.
 */
public final class ProtoVia {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = ProtoReader.DEFAULT_MAX_MESSAGE_SIZE;
    public static final int DEFAULT_MAX_DEPTH = ProtoReader.DEFAULT_MAX_DEPTH;

    private static volatile int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    private static volatile int maxDepth = DEFAULT_MAX_DEPTH;

    private ProtoVia() {
    }

    public static void setMaxMessageSize(int maxMessageSize) {
        if (maxMessageSize <= 0) {
            throw new IllegalArgumentException("maxMessageSize must be positive");
        }
        ProtoVia.maxMessageSize = maxMessageSize;
    }

    public static void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        ProtoVia.maxDepth = maxDepth;
    }

    public static int maxMessageSize() {
        return maxMessageSize;
    }

    public static int maxDepth() {
        return maxDepth;
    }

    public static <T> ProtoCodec<T> codec(Class<T> type) {
        return CodecLookup.get(type);
    }

    public static <T> void register(Class<T> type, ProtoCodec<T> codec) {
        CodecLookup.register(type, codec);
    }

    @SuppressWarnings("unchecked")
    public static int sizeOf(Object message) {
        Objects.requireNonNull(message, "message");
        ProtoCodec<Object> codec = codec((Class<Object>) message.getClass());
        return codec.computeSize(message);
    }

    @SuppressWarnings("unchecked")
    public static byte[] toBytes(Object message) {
        Objects.requireNonNull(message, "message");
        ProtoCodec<Object> codec = codec((Class<Object>) message.getClass());
        SizeCache sizes = new SizeCache();
        int size = codec.computeSize(message, sizes);
        if (size > maxMessageSize) {
            throw new ProtoException("serialized size " + size + " exceeds max " + maxMessageSize);
        }
        ProtoWriter writer = new ProtoWriter(size, sizes);
        codec.writeTo(writer, message);
        return writer.finish();
    }

    public static <T> T fromBytes(Class<T> type, byte[] data) {
        Objects.requireNonNull(data, "data");
        return fromBytes(type, data, 0, data.length);
    }

    public static <T> T fromBytes(Class<T> type, byte[] data, int offset, int length) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        ProtoCodec<T> codec = codec(type);
        ProtoReader reader = new ProtoReader(data, offset, length, maxMessageSize, maxDepth);
        return codec.readFrom(reader);
    }

    public static void write(OutputStream out, Object message) {
        Objects.requireNonNull(out, "out");
        byte[] bytes = toBytes(message);
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new ProtoException("failed to write protobuf message", e);
        }
    }

    public static <T> T read(Class<T> type, InputStream in) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(in, "in");
        try {
            byte[] data = readBounded(in, maxMessageSize);
            return fromBytes(type, data);
        } catch (IOException e) {
            throw new ProtoException("failed to read protobuf message", e);
        }
    }

    /**
     * Packs {@code message} as {@code google.protobuf.Any}.
     * {@code type_url} is {@code type.googleapis.com/} plus {@link ProtoCodec#protoFullName()}.
     */
    public static ProtoAny pack(Object message) {
        return pack(message, ProtoAny.TYPE_URL_PREFIX);
    }

    @SuppressWarnings("unchecked")
    public static ProtoAny pack(Object message, String typeUrlPrefix) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(typeUrlPrefix, "typeUrlPrefix");
        ProtoCodec<Object> codec = codec((Class<Object>) message.getClass());
        return new ProtoAny(ProtoAny.typeUrl(typeUrlPrefix, codec.protoFullName()), toBytes(message));
    }

    public static <T> T unpack(ProtoAny any, Class<T> type) {
        Objects.requireNonNull(any, "any");
        return any.unpack(codec(type));
    }

    public static boolean is(ProtoAny any, Class<?> type) {
        Objects.requireNonNull(any, "any");
        return any.is(codec(type));
    }

    static byte[] readBounded(InputStream in, int max) throws IOException {
        byte[] buf = in.readNBytes(max + 1);
        if (buf.length > max) {
            throw new ProtoException("input exceeds max message size " + max);
        }
        return buf;
    }
}
