package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.time.Instant;

/**
 * {@code google.protobuf.Timestamp}: {@code int64 seconds = 1; int32 nanos = 2}.
 */
public final class TimestampCodec implements ProtoCodec<Instant> {

    public static final TimestampCodec INSTANCE = new TimestampCodec();

    private TimestampCodec() {
    }

    @Override
    public Class<Instant> type() {
        return Instant.class;
    }

    @Override
    public boolean cachesNestedSizes() {
        return true;
    }

    @Override
    public int computeSize(Instant value) {
        int size = 0;
        long seconds = value.getEpochSecond();
        int nanos = value.getNano();
        if (seconds != 0L) {
            size += CodedSize.int64(1, seconds);
        }
        if (nanos != 0) {
            size += CodedSize.int32(2, nanos);
        }
        return size;
    }

    @Override
    public void writeTo(ProtoWriter writer, Instant value) {
        long seconds = value.getEpochSecond();
        int nanos = value.getNano();
        if (seconds != 0L) {
            writer.writeInt64(1, seconds);
        }
        if (nanos != 0) {
            writer.writeInt32(2, nanos);
        }
    }

    @Override
    public Instant readFrom(ProtoReader reader) {
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
        if (nanos < 0 || nanos > 999_999_999) {
            throw new ProtoException("Timestamp nanos out of range: " + nanos);
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
