package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.nio.ByteBuffer;

/**
 * Serialized-size helpers matching official proto3 encoding.
 */
public final class CodedSize {

    private CodedSize() {
    }

    public static int uint32(int value) {
        if ((value & ~0x7F) == 0) {
            return 1;
        }
        if ((value & ~0x3FFF) == 0) {
            return 2;
        }
        if ((value & ~0x1FFFFF) == 0) {
            return 3;
        }
        if ((value & ~0xFFFFFFF) == 0) {
            return 4;
        }
        return 5;
    }

    public static int uint64(long value) {
        if ((value & ~0x7FL) == 0L) {
            return 1;
        }
        if ((value & ~0x3FFFL) == 0L) {
            return 2;
        }
        if ((value & ~0x1FFFFFL) == 0L) {
            return 3;
        }
        if ((value & ~0xFFFFFFFL) == 0L) {
            return 4;
        }
        if ((value & ~0x7FFFFFFFFL) == 0L) {
            return 5;
        }
        if ((value & ~0x3FFFFFFFFFFL) == 0L) {
            return 6;
        }
        if ((value & ~0x1FFFFFFFFFFFFL) == 0L) {
            return 7;
        }
        if ((value & ~0xFFFFFFFFFFFFFFL) == 0L) {
            return 8;
        }
        if ((value & ~0x7FFFFFFFFFFFFFFFL) == 0L) {
            return 9;
        }
        return 10;
    }

    public static int int32(int value) {
        return value >= 0 ? uint32(value) : 10;
    }

    public static int int64(long value) {
        return uint64(value);
    }

    public static int sint32(int value) {
        return uint32(ZigZag.encode32(value));
    }

    public static int sint64(long value) {
        return uint64(ZigZag.encode64(value));
    }

    public static int bool(boolean value) {
        return 1;
    }

    public static int enumValue(int number) {
        return int32(number);
    }

    public static int tag(int fieldNumber) {
        return uint32(WireType.tag(fieldNumber, 0));
    }

    public static int tag(int fieldNumber, int wireType) {
        return uint32(WireType.tag(fieldNumber, wireType));
    }

    public static int int32(int fieldNumber, int value) {
        return tag(fieldNumber) + int32(value);
    }

    public static int uint32(int fieldNumber, int value) {
        return tag(fieldNumber) + uint32(value);
    }

    public static int sint32(int fieldNumber, int value) {
        return tag(fieldNumber) + sint32(value);
    }

    public static int int64(int fieldNumber, long value) {
        return tag(fieldNumber) + int64(value);
    }

    public static int uint64(int fieldNumber, long value) {
        return tag(fieldNumber) + uint64(value);
    }

    public static int sint64(int fieldNumber, long value) {
        return tag(fieldNumber) + sint64(value);
    }

    public static int bool(int fieldNumber, boolean value) {
        return tag(fieldNumber) + 1;
    }

    public static int enumValue(int fieldNumber, int number) {
        return tag(fieldNumber) + enumValue(number);
    }

    public static int fixed32(int fieldNumber) {
        return tag(fieldNumber) + 4;
    }

    public static int fixed64(int fieldNumber) {
        return tag(fieldNumber) + 8;
    }

    public static int float32(int fieldNumber) {
        return fixed32(fieldNumber);
    }

    public static int float64(int fieldNumber) {
        return fixed64(fieldNumber);
    }

    public static int lengthDelimited(int fieldNumber, int dataSize) {
        return tag(fieldNumber, WireType.LEN) + uint32(dataSize) + dataSize;
    }

    public static int string(int fieldNumber, String value) {
        int utf8 = Utf8.encodedLength(value);
        return lengthDelimited(fieldNumber, utf8);
    }

    public static int bytes(int fieldNumber, byte[] value) {
        return lengthDelimited(fieldNumber, value.length);
    }

    public static int bytes(int fieldNumber, byte[] value, int offset, int length) {
        return lengthDelimited(fieldNumber, length);
    }

    public static int bytes(int fieldNumber, ByteBuffer value) {
        return lengthDelimited(fieldNumber, value.remaining());
    }

    public static int message(int fieldNumber, int messageSize) {
        return lengthDelimited(fieldNumber, messageSize);
    }

    public static <T> int message(int fieldNumber, ProtoCodec<T> codec, T value) {
        return message(fieldNumber, codec.computeSize(value));
    }
}
