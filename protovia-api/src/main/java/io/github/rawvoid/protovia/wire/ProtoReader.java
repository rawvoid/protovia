package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.nio.ByteBuffer;

/**
 * proto3 decoder over a byte slice. Not thread-safe.
 */
public final class ProtoReader {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = 64 * 1024 * 1024;
    public static final int DEFAULT_MAX_DEPTH = 100;

    private final byte[] buffer;
    private final int end;
    private int pos;
    private int currentLimit;
    private int lastTag;
    private int depth;
    private final int maxMessageSize;
    private final int maxDepth;

    public ProtoReader(byte[] data) {
        this(data, 0, data.length, DEFAULT_MAX_MESSAGE_SIZE, DEFAULT_MAX_DEPTH);
    }

    public ProtoReader(byte[] data, int offset, int length) {
        this(data, offset, length, DEFAULT_MAX_MESSAGE_SIZE, DEFAULT_MAX_DEPTH);
    }

    public ProtoReader(byte[] data, int offset, int length, int maxMessageSize, int maxDepth) {
        if (data == null) {
            throw new ProtoException("data is null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new ProtoException("invalid slice offset=" + offset + " length=" + length);
        }
        if (length > maxMessageSize) {
            throw new ProtoException("message exceeds max size " + maxMessageSize);
        }
        this.buffer = data;
        this.pos = offset;
        this.end = offset + length;
        this.currentLimit = this.end;
        this.maxMessageSize = maxMessageSize;
        this.maxDepth = maxDepth;
    }

    public int maxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Reads the next tag. Returns {@code 0} at the current limit / end of input.
     */
    public int readTag() {
        if (pos >= currentLimit) {
            lastTag = 0;
            return 0;
        }
        int tag = readRawVarint32();
        if (tag == 0) {
            throw new ProtoException("invalid tag 0");
        }
        int wireType = WireType.getWireType(tag);
        if (wireType == WireType.END_GROUP) {
            lastTag = tag;
            return tag;
        }
        if (wireType > WireType.I32) {
            throw new ProtoException("invalid wire type " + wireType);
        }
        lastTag = tag;
        return tag;
    }

    public int lastTag() {
        return lastTag;
    }

    public int fieldNumber() {
        return WireType.fieldNumber(lastTag);
    }

    public int wireType() {
        return WireType.getWireType(lastTag);
    }

    public void skipField() {
        skipField(lastTag);
    }

    public void skipField(int tag) {
        switch (WireType.getWireType(tag)) {
            case WireType.VARINT -> readRawVarint64();
            case WireType.I64 -> skipRaw(8);
            case WireType.LEN -> {
                int length = readRawVarint32();
                checkLength(length);
                skipRaw(length);
            }
            case WireType.START_GROUP -> skipGroup(WireType.fieldNumber(tag));
            case WireType.END_GROUP -> throw new ProtoException("unexpected end group");
            case WireType.I32 -> skipRaw(4);
            default -> throw new ProtoException("invalid wire type " + WireType.getWireType(tag));
        }
    }

    public int readInt32() {
        return readRawVarint32();
    }

    public int readUInt32() {
        return readRawVarint32();
    }

    public int readSInt32() {
        return ZigZag.decode32(readRawVarint32());
    }

    public long readInt64() {
        return readRawVarint64();
    }

    public long readUInt64() {
        return readRawVarint64();
    }

    public long readSInt64() {
        return ZigZag.decode64(readRawVarint64());
    }

    public boolean readBool() {
        return readRawVarint64() != 0L;
    }

    public int readEnum() {
        return readRawVarint32();
    }

    public int readFixed32() {
        return readRawLittleEndian32();
    }

    public int readSFixed32() {
        return readRawLittleEndian32();
    }

    public long readFixed64() {
        return readRawLittleEndian64();
    }

    public long readSFixed64() {
        return readRawLittleEndian64();
    }

    public float readFloat() {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    public String readString() {
        int length = readRawVarint32();
        checkLength(length);
        require(length);
        String value = Utf8.decode(buffer, pos, length);
        pos += length;
        return value;
    }

    public byte[] readBytes() {
        int length = readRawVarint32();
        checkLength(length);
        require(length);
        byte[] copy = new byte[length];
        System.arraycopy(buffer, pos, copy, 0, length);
        pos += length;
        return copy;
    }

    public ByteBuffer readByteBuffer() {
        return ByteBuffer.wrap(readBytes());
    }

    public int pushLimit(int byteLength) {
        checkLength(byteLength);
        int old = currentLimit;
        int next = pos + byteLength;
        if (next > currentLimit) {
            throw new ProtoException("truncated message: nested length " + byteLength);
        }
        currentLimit = next;
        return old;
    }

    public void popLimit(int oldLimit) {
        if (pos != currentLimit) {
            throw new ProtoException("nested message was not fully consumed");
        }
        currentLimit = oldLimit;
    }

    public int remaining() {
        return currentLimit - pos;
    }

    public <T> T readMessage(ProtoCodec<T> codec) {
        int length = readRawVarint32();
        if (depth >= maxDepth) {
            throw new ProtoException("message nesting exceeds max depth " + maxDepth);
        }
        int old = pushLimit(length);
        depth++;
        T value = codec.readFrom(this);
        depth--;
        popLimit(old);
        return value;
    }

    /**
     * Reads a packed repeated block: consumes the length prefix and pushes the limit.
     * Caller must {@link #popLimit(int)} after draining {@link #remaining()}.
     */
    public int beginPacked() {
        int length = readRawVarint32();
        return pushLimit(length);
    }

    public int readRawVarint32() {
        return (int) readRawVarint64();
    }

    public long readRawVarint64() {
        long result = 0L;
        int shift = 0;
        while (shift < 64) {
            if (pos >= currentLimit) {
                throw new ProtoException("truncated varint");
            }
            byte b = buffer[pos++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ProtoException("malformed varint");
    }

    public int readRawLittleEndian32() {
        require(4);
        int b1 = buffer[pos++] & 0xFF;
        int b2 = buffer[pos++] & 0xFF;
        int b3 = buffer[pos++] & 0xFF;
        int b4 = buffer[pos++] & 0xFF;
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }

    public long readRawLittleEndian64() {
        require(8);
        long b1 = buffer[pos++] & 0xFFL;
        long b2 = buffer[pos++] & 0xFFL;
        long b3 = buffer[pos++] & 0xFFL;
        long b4 = buffer[pos++] & 0xFFL;
        long b5 = buffer[pos++] & 0xFFL;
        long b6 = buffer[pos++] & 0xFFL;
        long b7 = buffer[pos++] & 0xFFL;
        long b8 = buffer[pos++] & 0xFFL;
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24) | (b5 << 32) | (b6 << 40) | (b7 << 48) | (b8 << 56);
    }

    private void skipGroup(int fieldNumber) {
        if (depth >= maxDepth) {
            throw new ProtoException("message nesting exceeds max depth " + maxDepth);
        }
        depth++;
        while (true) {
            int tag = readTag();
            if (tag == 0) {
                throw new ProtoException("truncated group");
            }
            if (WireType.getWireType(tag) == WireType.END_GROUP) {
                if (WireType.fieldNumber(tag) != fieldNumber) {
                    throw new ProtoException("end group field mismatch");
                }
                depth--;
                return;
            }
            skipField(tag);
        }
    }

    private void skipRaw(int n) {
        require(n);
        pos += n;
    }

    private void require(int n) {
        if (n < 0 || pos + n > currentLimit) {
            throw new ProtoException("truncated message, needed " + n + " bytes");
        }
    }

    private void checkLength(int length) {
        if (length < 0) {
            throw new ProtoException("negative length-delimited size");
        }
        if (length > maxMessageSize) {
            throw new ProtoException("length-delimited field exceeds max size " + maxMessageSize);
        }
    }
}
