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
            case WireType.VARINT -> skipRawVarint();
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
        int tempPos = pos;
        int limit = currentLimit;
        if (tempPos == limit) {
            throw new ProtoException("truncated varint");
        }
        byte[] buf = buffer;
        int x;
        if ((x = buf[tempPos++]) >= 0) {
            pos = tempPos;
            return x;
        }
        if (limit - tempPos < 9) {
            return (int) readRawVarint64SlowPath();
        }
        if ((x ^= buf[tempPos++] << 7) < 0) {
            x ^= ~0 << 7;
        } else if ((x ^= buf[tempPos++] << 14) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= buf[tempPos++] << 21) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = buf[tempPos++];
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && buf[tempPos++] < 0
                    && buf[tempPos++] < 0
                    && buf[tempPos++] < 0
                    && buf[tempPos++] < 0
                    && buf[tempPos++] < 0) {
                throw new ProtoException("malformed varint");
            }
        }
        pos = tempPos;
        return x;
    }

    public long readRawVarint64() {
        int tempPos = pos;
        int limit = currentLimit;
        if (tempPos == limit) {
            throw new ProtoException("truncated varint");
        }
        byte[] buf = buffer;
        long x;
        int y;
        if ((y = buf[tempPos++]) >= 0) {
            pos = tempPos;
            return y;
        }
        if (limit - tempPos < 9) {
            return readRawVarint64SlowPath();
        }
        if ((y ^= buf[tempPos++] << 7) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= buf[tempPos++] << 14) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= buf[tempPos++] << 21) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) buf[tempPos++] << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= (long) buf[tempPos++] << 35) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= (long) buf[tempPos++] << 42) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= (long) buf[tempPos++] << 49) < 0L) {
            x ^= (~0L << 7)
                    ^ (~0L << 14)
                    ^ (~0L << 21)
                    ^ (~0L << 28)
                    ^ (~0L << 35)
                    ^ (~0L << 42)
                    ^ (~0L << 49);
        } else if ((x ^= (long) buf[tempPos++] << 56) >= 0L) {
            x ^= (~0L << 7)
                    ^ (~0L << 14)
                    ^ (~0L << 21)
                    ^ (~0L << 28)
                    ^ (~0L << 35)
                    ^ (~0L << 42)
                    ^ (~0L << 49)
                    ^ (~0L << 56);
        } else if ((x ^= (long) buf[tempPos++] << 63) >= 0L) {
            x ^= (~0L << 7)
                    ^ (~0L << 14)
                    ^ (~0L << 21)
                    ^ (~0L << 28)
                    ^ (~0L << 35)
                    ^ (~0L << 42)
                    ^ (~0L << 49)
                    ^ (~0L << 56)
                    ^ (~0L << 63);
        } else {
            throw new ProtoException("malformed varint");
        }
        pos = tempPos;
        return x;
    }

    public int readRawLittleEndian32() {
        int tempPos = pos;
        if (currentLimit - tempPos < 4) {
            throw new ProtoException("truncated message, needed 4 bytes");
        }
        byte[] buf = buffer;
        pos = tempPos + 4;
        return (buf[tempPos] & 0xFF)
                | ((buf[tempPos + 1] & 0xFF) << 8)
                | ((buf[tempPos + 2] & 0xFF) << 16)
                | ((buf[tempPos + 3] & 0xFF) << 24);
    }

    public long readRawLittleEndian64() {
        int tempPos = pos;
        if (currentLimit - tempPos < 8) {
            throw new ProtoException("truncated message, needed 8 bytes");
        }
        byte[] buf = buffer;
        pos = tempPos + 8;
        return (buf[tempPos] & 0xFFL)
                | ((buf[tempPos + 1] & 0xFFL) << 8)
                | ((buf[tempPos + 2] & 0xFFL) << 16)
                | ((buf[tempPos + 3] & 0xFFL) << 24)
                | ((buf[tempPos + 4] & 0xFFL) << 32)
                | ((buf[tempPos + 5] & 0xFFL) << 40)
                | ((buf[tempPos + 6] & 0xFFL) << 48)
                | ((buf[tempPos + 7] & 0xFFL) << 56);
    }

    private void skipRawVarint() {
        if (currentLimit - pos >= 10) {
            byte[] buf = buffer;
            for (int i = 0; i < 10; i++) {
                if (buf[pos++] >= 0) {
                    return;
                }
            }
            throw new ProtoException("malformed varint");
        }
        readRawVarint64SlowPath();
    }

    private long readRawVarint64SlowPath() {
        long result = 0L;
        for (int shift = 0; shift < 64; shift += 7) {
            if (pos >= currentLimit) {
                throw new ProtoException("truncated varint");
            }
            byte b = buffer[pos++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new ProtoException("malformed varint");
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
