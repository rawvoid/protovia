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
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable serialization limits and configured entry point for proto3 encoding and decoding.
 * Instances are thread-safe and isolated from the process-wide defaults on {@link Protovia},
 * making them safe for multi-tenant applications and concurrent tests.
 *
 * @author Rawvoid
 */
public final class ProtoConfig {

    public static final ProtoConfig DEFAULT = new ProtoConfig(
        ProtoReader.DEFAULT_MAX_MESSAGE_SIZE, ProtoReader.DEFAULT_MAX_DEPTH);

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final int maxMessageSize;
    private final int maxDepth;

    private ProtoConfig(int maxMessageSize, int maxDepth) {
        if (maxMessageSize <= 0) {
            throw new IllegalArgumentException("maxMessageSize must be positive: " + maxMessageSize);
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive: " + maxDepth);
        }
        this.maxMessageSize = maxMessageSize;
        this.maxDepth = maxDepth;
    }

    /**
     * Creates a {@code ProtoConfig} with the given byte-size and nesting-depth caps.
     *
     * @param maxMessageSize positive byte limit for encoding and decoding
     * @param maxDepth       positive nesting limit for parsing
     * @return immutable config instance
     */
    public static ProtoConfig of(int maxMessageSize, int maxDepth) {
        if (maxMessageSize == ProtoReader.DEFAULT_MAX_MESSAGE_SIZE
            && maxDepth == ProtoReader.DEFAULT_MAX_DEPTH) {
            return DEFAULT;
        }
        return new ProtoConfig(maxMessageSize, maxDepth);
    }

    /**
     * Returns a copy of this config with a new {@code maxMessageSize}.
     *
     * @param maxMessageSize positive byte limit
     * @return updated config
     */
    public ProtoConfig withMaxMessageSize(int maxMessageSize) {
        if (maxMessageSize == this.maxMessageSize) {
            return this;
        }
        return of(maxMessageSize, this.maxDepth);
    }

    /**
     * Returns a copy of this config with a new {@code maxDepth}.
     *
     * @param maxDepth positive nesting limit
     * @return updated config
     */
    public ProtoConfig withMaxDepth(int maxDepth) {
        if (maxDepth == this.maxDepth) {
            return this;
        }
        return of(this.maxMessageSize, maxDepth);
    }

    public int maxMessageSize() {
        return maxMessageSize;
    }

    public int maxDepth() {
        return maxDepth;
    }

    /**
     * @param message entity to measure
     * @return encoded size in bytes
     */
    @SuppressWarnings("unchecked")
    public int sizeOf(Object message) {
        Objects.requireNonNull(message, "message");
        ProtoCodec<Object> codec = CodecLookup.get((Class<Object>) message.getClass());
        return codec.computeSize(message);
    }

    /**
     * Encodes {@code message} to proto3 bytes using an exact-size buffer.
     *
     * @param message entity to encode
     * @return wire bytes
     */
    @SuppressWarnings("unchecked")
    public byte[] toBytes(Object message) {
        Objects.requireNonNull(message, "message");
        ProtoCodec<Object> codec = CodecLookup.get((Class<Object>) message.getClass());
        return toBytes(codec, message);
    }

    /**
     * Encodes {@code message} to a heap {@link ByteBuffer} wrapping {@link #toBytes(Object)}.
     *
     * @param message entity to encode
     * @return read-ready byte buffer positioned at {@code 0}
     */
    public ByteBuffer toByteBuffer(Object message) {
        return ByteBuffer.wrap(toBytes(message));
    }

    /**
     * @param data complete message bytes
     * @param type entity class
     * @return decoded instance
     */
    public <T> T fromBytes(byte[] data, Class<T> type) {
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
    public <T> T fromBytes(byte[] data, int offset, int length, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        ProtoCodec<T> codec = CodecLookup.get(type);
        ProtoReader reader = new ProtoReader(data, offset, length, maxMessageSize, maxDepth);
        return codec.readFrom(reader);
    }

    /**
     * Decodes one message from the remaining bytes {@code [buffer.position(), buffer.limit())}
     * without mutating {@code buffer}'s position or limit. Heap-backed buffers are decoded
     * zero-copy; direct and read-only buffers are bounds-checked against {@link #maxMessageSize()}
     * before copying.
     *
     * @param buffer source byte buffer
     * @param type   entity class
     * @return decoded instance
     */
    public <T> T fromByteBuffer(ByteBuffer buffer, Class<T> type) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(type, "type");
        if (buffer.hasArray()) {
            return fromBytes(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.remaining(),
                type);
        }
        int remaining = buffer.remaining();
        if (remaining > maxMessageSize) {
            throw new ProtoException("message exceeds max size " + maxMessageSize);
        }
        if (remaining == 0) {
            return fromBytes(EMPTY_BYTES, 0, 0, type);
        }
        byte[] copy = new byte[remaining];
        buffer.get(buffer.position(), copy, 0, remaining);
        return fromBytes(copy, 0, remaining, type);
    }

    /**
     * Merges wire bytes into {@code existing} according to proto3 merge semantics
     * (scalars overwrite, repeated fields append, nested messages merge).
     *
     * @param data     complete message bytes to merge
     * @param existing existing entity instance to merge into
     * @return merged instance (may be {@code existing} or a new object)
     */
    public <T> T mergeFrom(byte[] data, T existing) {
        Objects.requireNonNull(data, "data");
        return mergeFrom(data, 0, data.length, existing);
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
    @SuppressWarnings("unchecked")
    public <T> T mergeFrom(byte[] data, int offset, int length, T existing) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(data, "data");
        ProtoCodec<T> codec = CodecLookup.get((Class<T>) existing.getClass());
        ProtoReader reader = new ProtoReader(data, offset, length, maxMessageSize, maxDepth);
        return codec.mergeFrom(reader, existing);
    }

    /**
     * Writes one message as {@link #toBytes(Object)} to {@code out} with no length prefix.
     *
     * @param out     destination
     * @param message entity to encode
     */
    public void write(OutputStream out, Object message) {
        Objects.requireNonNull(out, "out");
        byte[] bytes = toBytes(message);
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new ProtoException("failed to write protobuf message", e);
        }
    }

    /**
     * Reads the remainder of {@code in} (capped at {@link #maxMessageSize()}) as one message.
     *
     * @param in   source
     * @param type entity class
     * @return decoded instance
     */
    public <T> T read(InputStream in, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(in, "in");
        try {
            byte[] data = Protovia.readBounded(in, maxMessageSize);
            return fromBytes(data, 0, data.length, type);
        } catch (IOException e) {
            throw new ProtoException("failed to read protobuf message", e);
        }
    }

    /**
     * Writes a varint32 byte length prefix followed by the encoded message bytes to {@code out},
     * matching official protobuf {@code writeDelimitedTo}. Respects {@link #maxMessageSize()}.
     *
     * @param out     destination
     * @param message entity to encode
     */
    public void writeDelimited(OutputStream out, Object message) {
        Objects.requireNonNull(out, "out");
        byte[] bytes = toBytes(message);
        int length = bytes.length;
        try {
            writeRawVarint32(out, length);
            if (length > 0) {
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new ProtoException("failed to write delimited protobuf message", e);
        }
    }

    /**
     * Reads one varint32 length-delimited message from {@code in}, matching official protobuf
     * {@code parseDelimitedFrom}. Returns {@code null} on clean EOF before any header byte is read.
     *
     * @param in   source
     * @param type entity class
     * @return decoded instance, or {@code null} on clean EOF
     */
    public <T> T readDelimited(InputStream in, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(in, "in");
        try {
            int firstByte = in.read();
            if (firstByte == -1) {
                return null;
            }
            int length = readRawVarint32(firstByte, in);
            if (length < 0) {
                throw ProtoException.negativeSize();
            }
            if (length > maxMessageSize) {
                throw new ProtoException("message exceeds max size " + maxMessageSize);
            }
            if (length == 0) {
                return fromBytes(EMPTY_BYTES, 0, 0, type);
            }
            byte[] data = in.readNBytes(length);
            if (data.length < length) {
                throw ProtoException.truncated(length);
            }
            return fromBytes(data, 0, data.length, type);
        } catch (IOException e) {
            throw new ProtoException("failed to read delimited protobuf message", e);
        }
    }

    /**
     * Packs {@code message} as {@code google.protobuf.Any}.
     * {@code type_url} is {@code type.googleapis.com/} plus {@link ProtoCodec#protoFullName()}.
     *
     * @param message entity to pack
     * @return Any envelope
     */
    public ProtoAny pack(Object message) {
        return pack(message, ProtoAny.TYPE_URL_PREFIX);
    }

    /**
     * @param message       entity to pack
     * @param typeUrlPrefix prefix such as {@code type.googleapis.com}; a trailing {@code /} is optional
     * @return Any envelope
     */
    @SuppressWarnings("unchecked")
    public ProtoAny pack(Object message, String typeUrlPrefix) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(typeUrlPrefix, "typeUrlPrefix");
        ProtoCodec<Object> codec = CodecLookup.get((Class<Object>) message.getClass());
        return new ProtoAny(
            ProtoAny.typeUrl(typeUrlPrefix, codec.protoFullName()),
            toBytes(codec, message));
    }

    /**
     * Unpacks {@code any} as {@code type}, enforcing this config's limits.
     *
     * @param any  packed Any
     * @param type expected entity class
     * @return unpacked instance
     * @throws ProtoException if {@code any} is not {@code type} or violates limits
     */
    public <T> T unpack(ProtoAny any, Class<T> type) {
        Objects.requireNonNull(any, "any");
        return any.unpack(Protovia.codec(type), this);
    }

    /**
     * @param any  packed Any
     * @param type candidate entity class
     * @return {@code true} if {@code any}'s type URL names {@code type}
     */
    public boolean is(ProtoAny any, Class<?> type) {
        Objects.requireNonNull(any, "any");
        return any.is(Protovia.codec(type));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtoConfig other
            && maxMessageSize == other.maxMessageSize
            && maxDepth == other.maxDepth;
    }

    @Override
    public int hashCode() {
        return 31 * maxMessageSize + maxDepth;
    }

    @Override
    public String toString() {
        return "ProtoConfig{maxMessageSize=" + maxMessageSize + ", maxDepth=" + maxDepth + "}";
    }

    private byte[] toBytes(ProtoCodec<Object> codec, Object message) {
        SizeCache sizes = codec.needsSizeCache() ? new SizeCache() : SizeCache.NOOP;
        int size = codec.computeSize(message, sizes);
        if (size < 0) {
            throw new ProtoException("negative computed size: " + size);
        }
        if (size > maxMessageSize) {
            throw new ProtoException("serialized size " + size + " exceeds max " + maxMessageSize);
        }
        if (size == 0) {
            return EMPTY_BYTES;
        }
        ProtoWriter writer = new ProtoWriter(size, sizes);
        codec.writeTo(writer, message);
        return writer.finish();
    }

    private static void writeRawVarint32(OutputStream out, int value) throws IOException {
        if ((value & ~0x7F) == 0) {
            out.write(value);
            return;
        }
        byte[] header = new byte[5];
        int i = 0;
        while ((value & ~0x7F) != 0) {
            header[i++] = (byte) (value | 0x80);
            value >>>= 7;
        }
        header[i++] = (byte) value;
        out.write(header, 0, i);
    }

    private static int readRawVarint32(int firstByte, InputStream in) throws IOException {
        if ((firstByte & 0x80) == 0) {
            return firstByte;
        }
        int result = firstByte & 0x7F;
        int offset = 7;
        for (; offset < 32; offset += 7) {
            int b = in.read();
            if (b == -1) {
                throw ProtoException.truncatedVarint();
            }
            result |= (b & 0x7F) << offset;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        for (; offset < 64; offset += 7) {
            int b = in.read();
            if (b == -1) {
                throw ProtoException.truncatedVarint();
            }
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw ProtoException.malformedVarint();
    }
}
