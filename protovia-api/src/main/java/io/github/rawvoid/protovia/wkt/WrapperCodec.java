package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * proto3 wrapper: one scalar field {@code value = 1}, default omitted.
 *
 * @param <T> wrapper record type
 * @author Rawvoid
 */
final class WrapperCodec<T> implements ProtoCodec<T> {

    private final Class<T> type;
    private final String protoFullName;
    private final int tag;
    private final T zero;
    private final Predicate<T> unset;
    private final ToIntFunction<T> sizeOf;
    private final Write<T> write;
    private final Function<ProtoReader, T> read;

    private WrapperCodec(
        Class<T> type,
        String protoFullName,
        int tag,
        T zero,
        Predicate<T> unset,
        ToIntFunction<T> sizeOf,
        Write<T> write,
        Function<ProtoReader, T> read) {
        this.type = type;
        this.protoFullName = protoFullName;
        this.tag = tag;
        this.zero = zero;
        this.unset = unset;
        this.sizeOf = sizeOf;
        this.write = write;
        this.read = read;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String protoFullName() {
        return protoFullName;
    }

    @Override
    public int computeSize(T value) {
        return unset.test(value) ? 0 : sizeOf.applyAsInt(value);
    }

    @Override
    public boolean cachesNestedSizes() {
        return true;
    }

    @Override
    public void writeTo(ProtoWriter writer, T value) {
        if (!unset.test(value)) {
            write.write(writer, value);
        }
    }

    @Override
    public T readFrom(ProtoReader reader) {
        return mergeFrom(reader, zero);
    }

    @Override
    public T mergeFrom(ProtoReader reader, T existing) {
        T value = existing != null ? existing : zero;
        int seen;
        while ((seen = reader.readTag()) != 0) {
            if (seen == tag) {
                value = read.apply(reader);
            } else {
                reader.skipField();
            }
        }
        return value;
    }

    static ProtoCodec<DoubleValue> float64() {
        return new WrapperCodec<>(
            DoubleValue.class,
            "google.protobuf.DoubleValue",
            9,
            new DoubleValue(0D),
            v -> Double.doubleToRawLongBits(v.value()) == 0L,
            v -> CodedSize.float64(1),
            (w, v) -> w.writeDouble(1, v.value()),
            r -> new DoubleValue(r.readDouble()));
    }

    static ProtoCodec<FloatValue> float32() {
        return new WrapperCodec<>(
            FloatValue.class,
            "google.protobuf.FloatValue",
            13,
            new FloatValue(0F),
            v -> Float.floatToRawIntBits(v.value()) == 0,
            v -> CodedSize.float32(1),
            (w, v) -> w.writeFloat(1, v.value()),
            r -> new FloatValue(r.readFloat()));
    }

    static ProtoCodec<Int64Value> int64() {
        return new WrapperCodec<>(
            Int64Value.class,
            "google.protobuf.Int64Value",
            8,
            new Int64Value(0L),
            v -> v.value() == 0L,
            v -> CodedSize.int64(1, v.value()),
            (w, v) -> w.writeInt64(1, v.value()),
            r -> new Int64Value(r.readInt64()));
    }

    static ProtoCodec<UInt64Value> uint64() {
        return new WrapperCodec<>(
            UInt64Value.class,
            "google.protobuf.UInt64Value",
            8,
            new UInt64Value(0L),
            v -> v.value() == 0L,
            v -> CodedSize.uint64(1, v.value()),
            (w, v) -> w.writeUInt64(1, v.value()),
            r -> new UInt64Value(r.readUInt64()));
    }

    static ProtoCodec<Int32Value> int32() {
        return new WrapperCodec<>(
            Int32Value.class,
            "google.protobuf.Int32Value",
            8,
            new Int32Value(0),
            v -> v.value() == 0,
            v -> CodedSize.int32(1, v.value()),
            (w, v) -> w.writeInt32(1, v.value()),
            r -> new Int32Value(r.readInt32()));
    }

    static ProtoCodec<UInt32Value> uint32() {
        return new WrapperCodec<>(
            UInt32Value.class,
            "google.protobuf.UInt32Value",
            8,
            new UInt32Value(0),
            v -> v.value() == 0,
            v -> CodedSize.uint32(1, v.value()),
            (w, v) -> w.writeUInt32(1, v.value()),
            r -> new UInt32Value(r.readUInt32()));
    }

    static ProtoCodec<BoolValue> bool() {
        return new WrapperCodec<>(
            BoolValue.class,
            "google.protobuf.BoolValue",
            8,
            new BoolValue(false),
            v -> !v.value(),
            v -> CodedSize.bool(1, v.value()),
            (w, v) -> w.writeBool(1, v.value()),
            r -> new BoolValue(r.readBool()));
    }

    static ProtoCodec<StringValue> string() {
        return new WrapperCodec<>(
            StringValue.class,
            "google.protobuf.StringValue",
            10,
            new StringValue(""),
            v -> v.value().isEmpty(),
            v -> CodedSize.string(1, v.value()),
            (w, v) -> w.writeString(1, v.value()),
            r -> new StringValue(r.readString()));
    }

    static ProtoCodec<BytesValue> bytes() {
        return new WrapperCodec<>(
            BytesValue.class,
            "google.protobuf.BytesValue",
            10,
            new BytesValue(new byte[0]),
            v -> v.value().length == 0,
            v -> CodedSize.bytes(1, v.value()),
            (w, v) -> w.writeBytes(1, v.value()),
            r -> new BytesValue(r.readBytes()));
    }

    @FunctionalInterface
    interface Write<T> {
        void write(ProtoWriter writer, T value);
    }
}
