/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * Looks up the generated {@link ProtoCodec} by convention and encodes or decodes proto3 bytes.
 *
 * @author Rawvoid
 */
public final class Protovia {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = ProtoReader.DEFAULT_MAX_MESSAGE_SIZE;
    public static final int DEFAULT_MAX_DEPTH = ProtoReader.DEFAULT_MAX_DEPTH;

    private static volatile int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    private static volatile int maxDepth = DEFAULT_MAX_DEPTH;

    private Protovia() {
    }

    /**
     * Caps encoded size and {@link #fromBytes} input length. Default is
     * {@link #DEFAULT_MAX_MESSAGE_SIZE}.
     *
     * @param maxMessageSize positive byte limit
     */
    public static void setMaxMessageSize(int maxMessageSize) {
        if (maxMessageSize <= 0) {
            throw new IllegalArgumentException("maxMessageSize must be positive");
        }
        Protovia.maxMessageSize = maxMessageSize;
    }

    /**
     * Caps nested-message depth while parsing. Default is {@link #DEFAULT_MAX_DEPTH}.
     *
     * @param maxDepth positive nesting limit
     */
    public static void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        Protovia.maxDepth = maxDepth;
    }

    public static int maxMessageSize() {
        return maxMessageSize;
    }

    public static int maxDepth() {
        return maxDepth;
    }

    /**
     * @param type entity class
     * @return codec for {@code type} (generated, well-known, or {@link #register registered})
     */
    public static <T> ProtoCodec<T> codec(Class<T> type) {
        return CodecLookup.get(type);
    }

    /**
     * Overrides codec lookup for {@code type}. Used by tests and hand-written codecs.
     *
     * @param type  entity class
     * @param codec codec to use
     */
    public static <T> void register(Class<T> type, ProtoCodec<T> codec) {
        CodecLookup.register(type, codec);
    }

    /**
     * @param message entity to measure
     * @return encoded size in bytes
     */
    @SuppressWarnings("unchecked")
    public static int sizeOf(Object message) {
        Objects.requireNonNull(message, "message");
        ProtoCodec<Object> codec = codec((Class<Object>) message.getClass());
        return codec.computeSize(message);
    }

    /**
     * Encodes {@code message} to proto3 bytes using an exact-size buffer.
     *
     * @param message entity to encode
     * @return wire bytes
     */
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

    /**
     * @param data complete message bytes
     * @param type entity class
     * @return decoded instance
     */
    public static <T> T fromBytes(byte[] data, Class<T> type) {
        Objects.requireNonNull(data, "data");
        return fromBytes(data, 0, data.length, type);
    }

    /**
     * @param data   buffer holding the message
     * @param offset start index in {@code data}
     * @param length number of bytes to parse
     * @param type   entity class
     * @return decoded instance
     */
    public static <T> T fromBytes(byte[] data, int offset, int length, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        ProtoCodec<T> codec = codec(type);
        ProtoReader reader = new ProtoReader(data, offset, length, maxMessageSize, maxDepth);
        return codec.readFrom(reader);
    }

    /**
     * Writes one message as {@link #toBytes(Object)} to {@code out}.
     * There is no length prefix — this matches official {@code toByteArray}
     * followed by a stream write, not {@code writeDelimitedTo}.
     *
     * @param out     destination
     * @param message entity to encode
     */
    public static void write(OutputStream out, Object message) {
        Objects.requireNonNull(out, "out");
        byte[] bytes = toBytes(message);
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new ProtoException("failed to write protobuf message", e);
        }
    }

    /**
     * Reads the remainder of {@code in} (capped at {@link #maxMessageSize()}) as
     * one message. Same boundary as official {@code parseFrom(InputStream)};
     * there is no delimited / multi-message variant.
     *
     * @param in   source
     * @param type entity class
     * @return decoded instance
     */
    public static <T> T read(InputStream in, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(in, "in");
        try {
            byte[] data = readBounded(in, maxMessageSize);
            return fromBytes(data, type);
        } catch (IOException e) {
            throw new ProtoException("failed to read protobuf message", e);
        }
    }

    /**
     * Packs {@code message} as {@code google.protobuf.Any}.
     * {@code type_url} is {@code type.googleapis.com/} plus {@link ProtoCodec#protoFullName()}.
     *
     * @param message entity to pack
     * @return Any envelope
     */
    public static ProtoAny pack(Object message) {
        return pack(message, ProtoAny.TYPE_URL_PREFIX);
    }

    /**
     * @param message       entity to pack
     * @param typeUrlPrefix prefix such as {@code type.googleapis.com}; a trailing {@code /} is optional
     * @return Any envelope
     */
    @SuppressWarnings("unchecked")
    public static ProtoAny pack(Object message, String typeUrlPrefix) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(typeUrlPrefix, "typeUrlPrefix");
        ProtoCodec<Object> codec = codec((Class<Object>) message.getClass());
        return new ProtoAny(ProtoAny.typeUrl(typeUrlPrefix, codec.protoFullName()), toBytes(message));
    }

    /**
     * @param any  packed Any
     * @param type expected entity class
     * @return unpacked instance
     * @throws ProtoException if {@code any} is not {@code type}
     */
    public static <T> T unpack(ProtoAny any, Class<T> type) {
        Objects.requireNonNull(any, "any");
        return any.unpack(codec(type));
    }

    /**
     * @param any  packed Any
     * @param type candidate entity class
     * @return {@code true} if {@code any}'s type URL names {@code type}
     */
    public static boolean is(ProtoAny any, Class<?> type) {
        Objects.requireNonNull(any, "any");
        return any.is(codec(type));
    }

    static byte[] readBounded(InputStream in, int max) throws IOException {
        if (max == Integer.MAX_VALUE) {
            return in.readAllBytes();
        }
        byte[] buf = in.readNBytes(max + 1);
        if (buf.length > max) {
            throw new ProtoException("input exceeds max message size " + max);
        }
        return buf;
    }
}
