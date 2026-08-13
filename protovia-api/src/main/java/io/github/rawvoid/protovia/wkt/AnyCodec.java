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
