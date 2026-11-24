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

package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntSupplier;

/**
 * Size-preallocated proto3 encoder. Not thread-safe.
 * Prefer the exact-size constructors; {@link #growing()} is for tests and unknown-field capture.
 *
 * @author Rawvoid
 */
public final class ProtoWriter {

    private static final byte[] EMPTY = new byte[0];
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final VarHandle INT_LE =
        MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_LE =
        MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private final boolean exact;
    private final SizeCache sizes;
    private byte[] buffer;
    private int pos;

    /**
     * Exact-size writer. {@link #finish()} requires that every allocated byte is written.
     *
     * @param size result of {@link io.github.rawvoid.protovia.codec.ProtoCodec#computeSize}
     */
    public ProtoWriter(int size) {
        this(size, true, SizeCache.NOOP);
    }

    /**
     * Exact-size writer that consumes nested lengths from {@code sizes} in pre-order.
     *
     * @param size  computed encoded size
     * @param sizes cache filled by {@code computeSize(value, cache)}
     */
    public ProtoWriter(int size, SizeCache sizes) {
        this(size, true, sizes);
    }

    private ProtoWriter(int size, boolean exact, SizeCache sizes) {
        if (size < 0) {
            throw new ProtoException("negative computed size: " + size);
        }
        this.buffer = size == 0 ? EMPTY : new byte[size];
        this.exact = exact;
        this.sizes = sizes;
    }

    public boolean hasCachedSize() {
        return sizes.hasNext();
    }

    public int takeSize() {
        return sizes.take();
    }

    public int takeSize(IntSupplier fallback) {
        return sizes.take(fallback);
    }

    public boolean hasCachedObject() {
        return sizes.hasNextObject();
    }

    @SuppressWarnings("unchecked")
    public <T> T takeObject() {
        return (T) sizes.takeObject();
    }

    public int position() {
        return pos;
    }

    public int capacity() {
        return buffer.length;
    }

    /**
     * Returns the exact serialized bytes. Throws if {@code computeSize} and {@code writeTo} disagree.
     */
    public byte[] finish() {
        if (!exact || pos != buffer.length) {
            throw new ProtoException("size mismatch: computed " + buffer.length + " wrote " + pos);
        }
        return buffer;
    }

    public byte[] toByteArray() {
        if (pos == 0) {
            return EMPTY;
        }
        if (pos == buffer.length) {
            return buffer;
        }
        byte[] copy = new byte[pos];
        System.arraycopy(buffer, 0, copy, 0, pos);
        return copy;
    }

    public void writeTag(int fieldNumber, int wireType) {
        writeUInt32NoTag(WireType.tag(fieldNumber, wireType));
    }

    public void writeInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeInt32NoTag(value);
    }

    public void writeUInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeUInt32NoTag(value);
    }

    public void writeSInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeUInt32NoTag(ZigZag.encode32(value));
    }

    public void writeInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeUInt64NoTag(value);
    }

    public void writeUInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeUInt64NoTag(value);
    }

    public void writeSInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeUInt64NoTag(ZigZag.encode64(value));
    }

    public void writeBool(int fieldNumber, boolean value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeRawByte(value ? 1 : 0);
    }

    public void writeEnum(int fieldNumber, int number) {
        writeInt32(fieldNumber, number);
    }

    public void writeFixed32(int fieldNumber, int value) {
        writeTag(fieldNumber, WireType.I32);
        writeFixed32NoTag(value);
    }

    public void writeSFixed32(int fieldNumber, int value) {
        writeFixed32(fieldNumber, value);
    }

    public void writeFloat(int fieldNumber, float value) {
        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFixed64(int fieldNumber, long value) {
        writeTag(fieldNumber, WireType.I64);
        writeFixed64NoTag(value);
    }

    public void writeSFixed64(int fieldNumber, long value) {
        writeFixed64(fieldNumber, value);
    }

    public void writeDouble(int fieldNumber, double value) {
        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeString(int fieldNumber, String value) {
        writeTag(fieldNumber, WireType.LEN);
        writeStringNoTag(value);
    }

    /**
     * Writes a length-delimited UTF-8 string with no field tag. Encodes directly into the
     * destination buffer (protobuf-java {@code ArrayEncoder.writeStringNoTag}).
     */
    public void writeStringNoTag(String value) {
        int oldPos = pos;
        try {
            int utf16Len = value.length();
            if (utf16Len <= Integer.MAX_VALUE / Utf8.MAX_BYTES_PER_CHAR) {
                int maxUtf8 = utf16Len * Utf8.MAX_BYTES_PER_CHAR;
                int maxLengthVarIntSize = CodedSize.uint32(maxUtf8);
                int minLengthVarIntSize = CodedSize.uint32(utf16Len);
                if (minLengthVarIntSize == maxLengthVarIntSize) {
                    if (!exact) {
                        int needed;
                        if (maxUtf8 <= Integer.MAX_VALUE - maxLengthVarIntSize) {
                            needed = maxLengthVarIntSize + maxUtf8;
                        } else {
                            int actual = Utf8.encodedLength(value);
                            if (actual > Integer.MAX_VALUE - maxLengthVarIntSize) {
                                throw new ProtoException("string too large to encode: " + actual);
                            }
                            needed = maxLengthVarIntSize + actual;
                        }
                        require(needed);
                    }
                    int startPos = oldPos + minLengthVarIntSize;
                    int newPos = Utf8.encode(value, buffer, startPos, buffer.length - startPos);
                    int length = newPos - startPos;
                    int p = oldPos;
                    while ((length & ~0x7F) != 0) {
                        buffer[p++] = (byte) (length | 0x80);
                        length >>>= 7;
                    }
                    buffer[p] = (byte) length;
                    pos = newPos;
                    return;
                }
            }
            int length = Utf8.encodedLength(value);
            int varIntSize = CodedSize.uint32(length);
            if (!exact) {
                if (length > Integer.MAX_VALUE - varIntSize) {
                    throw new ProtoException("string too large to encode: " + length);
                }
                require(varIntSize + length);
            }
            writeUInt32NoTag(length);
            pos = Utf8.encode(value, buffer, pos, buffer.length - pos);
        } catch (IndexOutOfBoundsException e) {
            throw new ProtoException("write overflow encoding string at " + oldPos + " of " + buffer.length, e);
        }
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        writeBytes(fieldNumber, value, 0, value.length);
    }

    public void writeBytes(int fieldNumber, byte[] value, int offset, int length) {
        writeTag(fieldNumber, WireType.LEN);
        writeBytesNoTag(value, offset, length);
    }

    public void writeBytes(int fieldNumber, ByteBuffer value) {
        writeTag(fieldNumber, WireType.LEN);
        writeBytesNoTag(value);
    }

    public void writeBytesNoTag(byte[] value) {
        writeBytesNoTag(value, 0, value.length);
    }

    public void writeBytesNoTag(byte[] value, int offset, int length) {
        if (length < 0) {
            throw new ProtoException("negative length: " + length);
        }
        writeUInt32NoTag(length);
        writeRawBytes(value, offset, length);
    }

    public void writeBytesNoTag(ByteBuffer value) {
        int length = value.remaining();
        writeUInt32NoTag(length);
        if (length == 0) {
            return;
        }
        if (value.hasArray()) {
            writeRawBytes(value.array(), value.arrayOffset() + value.position(), length);
        } else {
            if (length > buffer.length - pos) {
                require(length);
            }
            value.get(value.position(), buffer, pos, length);
            pos += length;
        }
    }

    public <T> void writeMessage(int fieldNumber, ProtoCodec<T> codec, T value) {
        writeTag(fieldNumber, WireType.LEN);
        writeMessageNoTag(codec, value);
    }

    public <T> void writeMessageNoTag(ProtoCodec<T> codec, T value) {
        int size = codec.cachesNestedSizes() && hasCachedSize()
            ? takeSize()
            : codec.computeSize(value);
        writeUInt32NoTag(size);
        codec.writeTo(this, value);
    }

    public void writeInt32NoTag(int value) {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            writeUInt64NoTag(value);
        }
    }

    public void writeUInt32NoTag(int value) {
        int position = pos;
        if (!exact && 5 > buffer.length - position) {
            require(5);
            position = pos;
        }
        try {
            while (true) {
                if ((value & ~0x7F) == 0) {
                    buffer[position++] = (byte) value;
                    break;
                }
                buffer[position++] = (byte) (value | 0x80);
                value >>>= 7;
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ProtoException("write overflow: varint32 at " + pos + " of " + buffer.length, e);
        }
        pos = position;
    }

    public void writeUInt64NoTag(long value) {
        int position = pos;
        if (!exact && 10 > buffer.length - position) {
            require(10);
            position = pos;
        }
        try {
            while (true) {
                if ((value & ~0x7FL) == 0L) {
                    buffer[position++] = (byte) value;
                    break;
                }
                buffer[position++] = (byte) ((int) value | 0x80);
                value >>>= 7;
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ProtoException("write overflow: varint64 at " + pos + " of " + buffer.length, e);
        }
        pos = position;
    }

    public void writeSInt32NoTag(int value) {
        writeUInt32NoTag(ZigZag.encode32(value));
    }

    public void writeSInt64NoTag(long value) {
        writeUInt64NoTag(ZigZag.encode64(value));
    }

    public void writeBoolNoTag(boolean value) {
        writeRawByte(value ? 1 : 0);
    }

    public void writeFixed32NoTag(int value) {
        int position = pos;
        if (4 > buffer.length - position) {
            require(4);
            position = pos;
        }
        INT_LE.set(buffer, position, value);
        pos = position + 4;
    }

    public void writeFixed64NoTag(long value) {
        int position = pos;
        if (8 > buffer.length - position) {
            require(8);
            position = pos;
        }
        LONG_LE.set(buffer, position, value);
        pos = position + 8;
    }

    public void writeFloatNoTag(float value) {
        writeFixed32NoTag(Float.floatToRawIntBits(value));
    }

    public void writeDoubleNoTag(double value) {
        writeFixed64NoTag(Double.doubleToRawLongBits(value));
    }

    public void writeRawByte(int value) {
        if (pos == buffer.length) {
            require(1);
        }
        buffer[pos++] = (byte) value;
    }

    public void writeRawBytes(byte[] value, int offset, int length) {
        if (length < 0) {
            throw new ProtoException("negative length: " + length);
        }
        if (length == 0) {
            return;
        }
        if (length > buffer.length - pos) {
            require(length);
        }
        System.arraycopy(value, offset, buffer, pos, length);
        pos += length;
    }

    private void require(int n) {
        if (n < 0) {
            throw new ProtoException("negative write size: " + n);
        }
        if (n <= buffer.length - pos) {
            return;
        }
        if (exact) {
            throw new ProtoException(
                "write overflow: need " + n + " bytes at " + pos + " of " + buffer.length);
        }
        if (n > Integer.MAX_VALUE - pos) {
            throw new ProtoException(
                "write buffer overflow: need " + n + " bytes at " + pos);
        }
        int minCapacity = pos + n;
        int doubled = buffer.length > (MAX_ARRAY_SIZE >>> 1) ? MAX_ARRAY_SIZE : buffer.length << 1;
        int next = Math.max(doubled, minCapacity);
        byte[] grown = new byte[next];
        System.arraycopy(buffer, 0, grown, 0, pos);
        buffer = grown;
    }

    /**
     * Scratch writer that may grow. {@link #toByteArray()} returns the written prefix.
     */
    public static ProtoWriter growing() {
        return new ProtoWriter(64, false, SizeCache.NOOP);
    }
}
