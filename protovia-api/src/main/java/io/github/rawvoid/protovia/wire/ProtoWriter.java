package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.nio.ByteBuffer;

/**
 * Size-preallocated proto3 encoder. Not thread-safe.
 */
public final class ProtoWriter {

    private byte[] buffer;
    private int pos;
    private final boolean exact;

    public ProtoWriter(int size) {
        this(size, true);
    }

    /**
     * Scratch writer that may grow. {@link #toByteArray()} returns the written prefix.
     */
    public static ProtoWriter growing() {
        return new ProtoWriter(64, false);
    }

    private ProtoWriter(int size, boolean exact) {
        if (size < 0) {
            throw new ProtoException("negative computed size: " + size);
        }
        this.buffer = size == 0 ? new byte[0] : new byte[size];
        this.exact = exact;
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
        writeUInt32NoTag(length);
        writeRawBytes(value, offset, length);
    }

    public void writeBytes(int fieldNumber, ByteBuffer value) {
        int length = value.remaining();
        writeTag(fieldNumber, WireType.LEN);
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
        int size = codec.computeSize(value);
        writeTag(fieldNumber, WireType.LEN);
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
        while (true) {
            if ((value & ~0x7F) == 0) {
                writeRawByte(value);
                return;
            }
            writeRawByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public void writeUInt64NoTag(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0L) {
                writeRawByte((int) value);
                return;
            }
            writeRawByte(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
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
        require(4);
        buffer[pos++] = (byte) value;
        buffer[pos++] = (byte) (value >>> 8);
        buffer[pos++] = (byte) (value >>> 16);
        buffer[pos++] = (byte) (value >>> 24);
    }

    public void writeFixed64NoTag(long value) {
        require(8);
        buffer[pos++] = (byte) value;
        buffer[pos++] = (byte) (value >>> 8);
        buffer[pos++] = (byte) (value >>> 16);
        buffer[pos++] = (byte) (value >>> 24);
        buffer[pos++] = (byte) (value >>> 32);
        buffer[pos++] = (byte) (value >>> 40);
        buffer[pos++] = (byte) (value >>> 48);
        buffer[pos++] = (byte) (value >>> 56);
    }

    public void writeFloatNoTag(float value) {
        writeFixed32NoTag(Float.floatToRawIntBits(value));
    }

    public void writeDoubleNoTag(double value) {
        writeFixed64NoTag(Double.doubleToRawLongBits(value));
    }

    public void writeRawByte(int value) {
        require(1);
        buffer[pos++] = (byte) value;
    }

    public void writeRawBytes(byte[] value, int offset, int length) {
        if (length == 0) {
            return;
        }
        require(length);
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
