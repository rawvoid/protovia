package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

/**
 * {@code google.protobuf.Any}: {@code string type_url = 1; bytes value = 2}.
 *
 * @author Rawvoid
 */
public final class AnyCodec implements ProtoCodec<ProtoAny> {

    public static final AnyCodec INSTANCE = new AnyCodec();

    private static final byte[] EMPTY = new byte[0];

    private AnyCodec() {
    }

    @Override
    public Class<ProtoAny> type() {
        return ProtoAny.class;
    }

    @Override
    public String protoFullName() {
        return "google.protobuf.Any";
    }

    @Override
    public int computeSize(ProtoAny value) {
        int size = 0;
        if (!value.typeUrl().isEmpty()) {
            size += CodedSize.string(1, value.typeUrl());
        }
        if (value.value().length != 0) {
            size += CodedSize.bytes(2, value.value());
        }
        return size;
    }

    @Override
    public boolean cachesNestedSizes() {
        return true;
    }

    @Override
    public void writeTo(ProtoWriter writer, ProtoAny value) {
        if (!value.typeUrl().isEmpty()) {
            writer.writeString(1, value.typeUrl());
        }
        if (value.value().length != 0) {
            writer.writeBytes(2, value.value());
        }
    }

    @Override
    public ProtoAny readFrom(ProtoReader reader) {
        return mergeFrom(reader, new ProtoAny("", EMPTY));
    }

    @Override
    public ProtoAny mergeFrom(ProtoReader reader, ProtoAny existing) {
        String typeUrl = existing != null ? existing.typeUrl() : "";
        byte[] value = existing != null ? existing.value() : EMPTY;
        int tag;
        while ((tag = reader.readTag()) != 0) {
            switch (tag) {
                case 10 -> typeUrl = reader.readString();
                case 18 -> value = reader.readBytes();
                default -> reader.skipField();
            }
        }
        return new ProtoAny(typeUrl, value);
    }
}
