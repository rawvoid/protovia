package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.time.Duration;

/**
 * {@code google.protobuf.Duration}: {@code int64 seconds = 1; int32 nanos = 2}.
 */
public final class DurationCodec implements ProtoCodec<Duration> {

    public static final DurationCodec INSTANCE = new DurationCodec();

    private DurationCodec() {
    }

    @Override
    public Class<Duration> type() {
        return Duration.class;
    }

    @Override
    public String protoFullName() {
        return "google.protobuf.Duration";
    }

    @Override
    public boolean cachesNestedSizes() {
        return true;
    }

    @Override
    public int computeSize(Duration value) {
        int size = 0;
        long seconds = value.getSeconds();
        int nanos = value.getNano();
        if (seconds < 0 && nanos > 0) {
            seconds += 1;
            nanos -= 1_000_000_000;
        }
        if (seconds != 0L) {
            size += CodedSize.int64(1, seconds);
        }
        if (nanos != 0) {
            size += CodedSize.int32(2, nanos);
        }
        return size;
    }

    @Override
    public void writeTo(ProtoWriter writer, Duration value) {
        long seconds = value.getSeconds();
        int nanos = value.getNano();
        if (seconds < 0 && nanos > 0) {
            seconds += 1;
            nanos -= 1_000_000_000;
        }
        if (seconds != 0L) {
            writer.writeInt64(1, seconds);
        }
        if (nanos != 0) {
            writer.writeInt32(2, nanos);
        }
    }

    @Override
    public Duration readFrom(ProtoReader reader) {
        long seconds = 0L;
        int nanos = 0;
        int tag;
        while ((tag = reader.readTag()) != 0) {
            switch (tag) {
                case 8 -> seconds = reader.readInt64();
                case 16 -> nanos = reader.readInt32();
                default -> reader.skipField();
            }
        }
        if (nanos <= -1_000_000_000 || nanos >= 1_000_000_000) {
            throw new ProtoException("Duration nanos out of range: " + nanos);
        }
        if (seconds > 0 && nanos < 0 || seconds < 0 && nanos > 0) {
            throw new ProtoException("Duration seconds and nanos must have the same sign");
        }
        return Duration.ofSeconds(seconds, nanos);
    }
}
