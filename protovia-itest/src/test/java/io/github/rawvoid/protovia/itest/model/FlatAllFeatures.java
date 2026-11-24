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

package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoOneof;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

import java.util.List;

/**
 * Flat message containing only scalars, strings, bytes, enums, unpacked repeated,
 * scalar/empty-only oneofs, and unknown fields — must report {@code needsSizeCache() == false}.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class FlatAllFeatures {

    public sealed interface Choice permits Text, Empty {
    }

    public record Text(String value) implements Choice {
    }

    public record Empty() implements Choice {
    }

    @ProtoField(number = 1)
    public int id;

    @ProtoField(number = 2)
    public String name;

    @ProtoField(number = 3)
    public byte[] data;

    @ProtoField(number = 4)
    public Status status;

    @ProtoField(number = 5)
    public List<String> tags;

    @ProtoField(number = 6, packed = false)
    public List<Integer> unpackedNums;

    @ProtoOneof({
        @ProtoOneof.Case(number = 7, of = Text.class),
        @ProtoOneof.Case(number = 8, of = Empty.class)
    })
    public Choice choice;

    @ProtoUnknown
    public UnknownFields unknown;
}
