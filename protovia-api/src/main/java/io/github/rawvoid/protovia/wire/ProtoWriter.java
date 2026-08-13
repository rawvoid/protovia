package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

/**
 * Size-preallocated proto3 encoder. Not thread-safe.
 */
public final class ProtoWriter {

    private byte[] buffer;
    private int pos;
    private final boolean exact;
    private final SizeCache sizes;

    public ProtoWriter(int size) {
        this(size, true, SizeCache.NOOP);
    }

    public ProtoWriter(int size, SizeCache sizes) {
        this(size, true, sizes);
    }

    /**
     * Scratch writer that may grow. {@link #toByteArray()} returns the written prefix.
     */
    public static ProtoWriter growing() {
        return new ProtoWriter(64, false, SizeCache.NOOP);
    }

    private ProtoWriter(int size, boolean exact, SizeCache sizes) {
        if (size < 0) {
            throw new ProtoException("negative computed size: " + size);
        }
        this.buffer = size == 0 ? new byte[0] : new byte[size];
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
        writeUInt32NoTag(value ? 1 : 0);
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
        int maxUtf8 = value.length() * Utf8.MAX_BYTES_PER_CHAR;
        if (!exact) {
            require(CodedSize.uint32(maxUtf8) + maxUtf8);
        }
        try {
            int maxLengthVarIntSize = CodedSize.uint32(maxUtf8);
            int minLengthVarIntSize = CodedSize.uint32(value.length());
            if (minLengthVarIntSize == maxLengthVarIntSize) {
                pos = oldPos + minLengthVarIntSize;
                int newPos = Utf8.encode(value, buffer, pos, buffer.length - pos);
                pos = oldPos;
                int length = newPos - oldPos - minLengthVarIntSize;
                writeUInt32NoTag(length);
                pos = newPos;
            } else {
                int length = Utf8.encodedLength(value);
                writeUInt32NoTag(length);
                pos = Utf8.encode(value, buffer, pos, buffer.length - pos);
            }
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
        writeUInt32NoTag(length);
        writeRawBytes(value, offset, length);
    }

    public void writeBytesNoTag(ByteBuffer value) {
        int length = value.remaining();
        writeUInt32NoTag(length);
        if (value.hasArray()) {
            writeRawBytes(value.array(), value.arrayOffset() + value.position(), length);
        } else {
            byte[] copy = new byte[length];
            ByteBuffer duplicate = value.duplicate();
            duplicate.get(copy);
            writeRawBytes(copy, 0, length);
        }
    }

    public <T> void writeMessage(int fieldNumber, ProtoCodec<T> codec, T value) {
        writeTag(fieldNumber, WireType.LEN);
        writeMessageNoTag(codec, value);
    }

    public <T> void writeMessageNoTag(ProtoCodec<T> codec, T value) {
        int size = codec.cachesNestedSizes()
                ? takeSize(() -> codec.computeSize(value))
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
        if (!exact && position + 5 > buffer.length) {
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
        if (!exact && position + 10 > buffer.length) {
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
        if (position + 4 > buffer.length) {
            require(4);
            position = pos;
        }
        buffer[position] = (byte) value;
        buffer[position + 1] = (byte) (value >>> 8);
        buffer[position + 2] = (byte) (value >>> 16);
        buffer[position + 3] = (byte) (value >>> 24);
        pos = position + 4;
    }

    public void writeFixed64NoTag(long value) {
        int position = pos;
        if (position + 8 > buffer.length) {
            require(8);
            position = pos;
        }
        buffer[position] = (byte) value;
        buffer[position + 1] = (byte) (value >>> 8);
        buffer[position + 2] = (byte) (value >>> 16);
        buffer[position + 3] = (byte) (value >>> 24);
        buffer[position + 4] = (byte) (value >>> 32);
        buffer[position + 5] = (byte) (value >>> 40);
        buffer[position + 6] = (byte) (value >>> 48);
        buffer[position + 7] = (byte) (value >>> 56);
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
        if (length == 0) {
            return;
        }
        if (pos + length > buffer.length) {
            require(length);
        }
        System.arraycopy(value, offset, buffer, pos, length);
        pos += length;
    }

    private void require(int n) {
        if (pos + n <= buffer.length) {
            return;
        }
        if (exact) {
            throw new ProtoException(
                    "write overflow: need " + n + " bytes at " + pos + " of " + buffer.length);
        }
        int next = Math.max(buffer.length * 2, pos + n);
        byte[] grown = new byte[next];
        System.arraycopy(buffer, 0, grown, 0, pos);
        buffer = grown;
    }
}
