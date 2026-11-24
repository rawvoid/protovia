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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for Protobuf serialization of {@code @ProtoMessage} entities.
 * Looks up the generated {@link ProtoCodec} by convention and encodes or decodes proto3 bytes.
 *
 * <p>For isolated per-call or per-tenant limits, obtain an immutable {@link ProtoConfig} via
 * {@link #withLimits(int, int)} or {@link ProtoConfig#of(int, int)}.
 *
 * @author Rawvoid
 */
public final class Protovia {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = ProtoReader.DEFAULT_MAX_MESSAGE_SIZE;
    public static final int DEFAULT_MAX_DEPTH = ProtoReader.DEFAULT_MAX_DEPTH;

    private static final AtomicReference<ProtoConfig> DEFAULT_CONFIG =
        new AtomicReference<>(ProtoConfig.DEFAULT);

    private Protovia() {
    }

    /**
     * Caps encoded size and {@link #fromBytes} input length for the global default config.
     * Default is {@link #DEFAULT_MAX_MESSAGE_SIZE}.
     *
     * @param maxMessageSize positive byte limit
     */
    public static void setMaxMessageSize(int maxMessageSize) {
        if (maxMessageSize <= 0) {
            throw new IllegalArgumentException("maxMessageSize must be positive");
        }
        DEFAULT_CONFIG.updateAndGet(config -> config.withMaxMessageSize(maxMessageSize));
    }

    /**
     * Caps nested-message depth while parsing for the global default config.
     * Default is {@link #DEFAULT_MAX_DEPTH}.
     *
     * @param maxDepth positive nesting limit
     */
    public static void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        DEFAULT_CONFIG.updateAndGet(config -> config.withMaxDepth(maxDepth));
    }

    public static int maxMessageSize() {
        return DEFAULT_CONFIG.get().maxMessageSize();
    }

    public static int maxDepth() {
        return DEFAULT_CONFIG.get().maxDepth();
    }

    /**
     * @return immutable snapshot of the current global {@link ProtoConfig}
     */
    public static ProtoConfig config() {
        return DEFAULT_CONFIG.get();
    }

    /**
     * Creates an isolated {@link ProtoConfig} with the given limits without mutating global defaults.
     *
     * @param maxMessageSize positive byte limit
     * @param maxDepth       positive nesting limit
     * @return immutable config instance
     */
    public static ProtoConfig withLimits(int maxMessageSize, int maxDepth) {
        return ProtoConfig.of(maxMessageSize, maxDepth);
    }

    /**
     * @param type entity class
     * @return codec for {@code type} (generated, well-known, or {@link #register registered})
     */
    public static <T> ProtoCodec<T> codec(Class<T> type) {
        return CodecLookup.get(type);
    }

    /**
     * Overrides top-level codec lookup for {@code type} (used by {@code Protovia.*} and
     * {@link ProtoConfig}, including {@link #unpack(ProtoAny, Class)} and
     * {@link ProtoConfig#unpack(ProtoAny, Class)}). Generated codecs invoke child
     * {@code XxxProtoCodec.INSTANCE} directly for static dispatch, so registering a child
     * type does not intercept parent-generated codecs.
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
    public static int sizeOf(Object message) {
        return DEFAULT_CONFIG.get().sizeOf(message);
    }

    /**
     * Encodes {@code message} to proto3 bytes using an exact-size buffer.
     *
     * @param message entity to encode
     * @return wire bytes
     */
    public static byte[] toBytes(Object message) {
        return DEFAULT_CONFIG.get().toBytes(message);
    }

    /**
     * Encodes {@code message} to a heap {@link ByteBuffer} wrapping {@link #toBytes(Object)}.
     *
     * @param message entity to encode
     * @return read-ready byte buffer positioned at {@code 0}
     */
    public static ByteBuffer toByteBuffer(Object message) {
        return DEFAULT_CONFIG.get().toByteBuffer(message);
    }

    /**
     * @param data complete message bytes
     * @param type entity class
     * @return decoded instance
     */
    public static <T> T fromBytes(byte[] data, Class<T> type) {
        return DEFAULT_CONFIG.get().fromBytes(data, type);
    }

    /**
     * @param data   buffer holding the message
     * @param offset start index in {@code data}
     * @param length number of bytes to parse
     * @param type   entity class
     * @return decoded instance
     */
    public static <T> T fromBytes(byte[] data, int offset, int length, Class<T> type) {
        return DEFAULT_CONFIG.get().fromBytes(data, offset, length, type);
    }

    /**
     * Decodes one message from {@code [buffer.position(), buffer.limit())} without mutating
     * {@code buffer}'s position or limit.
     *
     * @param buffer source byte buffer
     * @param type   entity class
     * @return decoded instance
     */
    public static <T> T fromByteBuffer(ByteBuffer buffer, Class<T> type) {
        return DEFAULT_CONFIG.get().fromByteBuffer(buffer, type);
    }

    /**
     * Merges wire bytes into {@code existing} according to proto3 merge semantics
     * (scalars overwrite, repeated fields append, nested messages merge).
     *
     * @param data     complete message bytes to merge
     * @param existing existing entity instance to merge into
     * @return merged instance (may be {@code existing} or a new object)
     */
    public static <T> T mergeFrom(byte[] data, T existing) {
        return DEFAULT_CONFIG.get().mergeFrom(data, existing);
    }

    /**
     * Merges a byte slice into {@code existing} according to proto3 merge semantics.
     *
     * @param data     buffer holding the message
     * @param offset   start index in {@code data}
     * @param length   number of bytes to parse
     * @param existing existing entity instance to merge into
     * @return merged instance (may be {@code existing} or a new object)
     */
    public static <T> T mergeFrom(byte[] data, int offset, int length, T existing) {
        return DEFAULT_CONFIG.get().mergeFrom(data, offset, length, existing);
    }

    /**
     * Writes one message as {@link #toBytes(Object)} to {@code out}.
     * There is no length prefix — this matches official {@code toByteArray}
     * followed by a stream write; see {@link #writeDelimited(OutputStream, Object)} for framed output.
     *
     * @param out     destination
     * @param message entity to encode
     */
    public static void write(OutputStream out, Object message) {
        DEFAULT_CONFIG.get().write(out, message);
    }

    /**
     * Reads the remainder of {@code in} (capped at {@link #maxMessageSize()}) as
     * one message. Same boundary as official {@code parseFrom(InputStream)};
     * see {@link #readDelimited(InputStream, Class)} for framed multi-message streams.
     *
     * @param in   source
     * @param type entity class
     * @return decoded instance
     */
    public static <T> T read(InputStream in, Class<T> type) {
        return DEFAULT_CONFIG.get().read(in, type);
    }

    /**
     * Writes a varint32 byte length prefix followed by the encoded message bytes to {@code out},
     * matching official protobuf {@code writeDelimitedTo}.
     *
     * @param out     destination
     * @param message entity to encode
     */
    public static void writeDelimited(OutputStream out, Object message) {
        DEFAULT_CONFIG.get().writeDelimited(out, message);
    }

    /**
     * Reads one varint32 length-delimited message from {@code in}, matching official protobuf
     * {@code parseDelimitedFrom}. Returns {@code null} on clean EOF before any header byte is read.
     *
     * @param in   source
     * @param type entity class
     * @return decoded instance, or {@code null} on clean EOF
     */
    public static <T> T readDelimited(InputStream in, Class<T> type) {
        return DEFAULT_CONFIG.get().readDelimited(in, type);
    }

    /**
     * Packs {@code message} as {@code google.protobuf.Any}.
     * {@code type_url} is {@code type.googleapis.com/} plus {@link ProtoCodec#protoFullName()}.
     *
     * @param message entity to pack
     * @return Any envelope
     */
    public static ProtoAny pack(Object message) {
        return DEFAULT_CONFIG.get().pack(message);
    }

    /**
     * @param message       entity to pack
     * @param typeUrlPrefix prefix such as {@code type.googleapis.com}; a trailing {@code /} is optional
     * @return Any envelope
     */
    public static ProtoAny pack(Object message, String typeUrlPrefix) {
        return DEFAULT_CONFIG.get().pack(message, typeUrlPrefix);
    }

    /**
     * @param any  packed Any
     * @param type expected entity class
     * @return unpacked instance
     * @throws ProtoException if {@code any} is not {@code type}
     */
    public static <T> T unpack(ProtoAny any, Class<T> type) {
        return DEFAULT_CONFIG.get().unpack(any, type);
    }

    /**
     * @param any  packed Any
     * @param type candidate entity class
     * @return {@code true} if {@code any}'s type URL names {@code type}
     */
    public static boolean is(ProtoAny any, Class<?> type) {
        return DEFAULT_CONFIG.get().is(any, type);
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
