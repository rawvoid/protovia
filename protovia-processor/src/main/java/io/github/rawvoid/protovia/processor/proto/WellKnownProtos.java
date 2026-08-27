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

package io.github.rawvoid.protovia.processor.proto;

import java.util.Map;

/**
 * Codec class → official proto full name and import. Bodies are never generated.
 * Java type → codec lives in {@code TypeClassifier.WELL_KNOWN_CODECS}.
 *
 * @author Rawvoid
 */
final class WellKnownProtos {

    record Type(String fullName, String importPath) {
    }

    private static final String WRAPPERS = "google/protobuf/wrappers.proto";

    private static final Map<String, Type> BY_CODEC = Map.ofEntries(
        entry("io.github.rawvoid.protovia.wkt.TimestampCodec", "google.protobuf.Timestamp",
            "google/protobuf/timestamp.proto"),
        entry("io.github.rawvoid.protovia.wkt.DurationCodec", "google.protobuf.Duration",
            "google/protobuf/duration.proto"),
        entry("io.github.rawvoid.protovia.wkt.AnyCodec", "google.protobuf.Any",
            "google/protobuf/any.proto"),
        entry("io.github.rawvoid.protovia.wkt.DoubleValue", "google.protobuf.DoubleValue", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.FloatValue", "google.protobuf.FloatValue", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.Int64Value", "google.protobuf.Int64Value", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.UInt64Value", "google.protobuf.UInt64Value", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.Int32Value", "google.protobuf.Int32Value", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.UInt32Value", "google.protobuf.UInt32Value", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.BoolValue", "google.protobuf.BoolValue", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.StringValue", "google.protobuf.StringValue", WRAPPERS),
        entry("io.github.rawvoid.protovia.wkt.BytesValue", "google.protobuf.BytesValue", WRAPPERS));

    private WellKnownProtos() {
    }

    static Type ofCodec(String codecName) {
        return codecName == null ? null : BY_CODEC.get(codecName);
    }

    static boolean isWellKnownFullName(String fullName) {
        return fullName != null && fullName.startsWith("google.protobuf.");
    }

    private static Map.Entry<String, Type> entry(String codec, String fullName, String importPath) {
        return Map.entry(codec, new Type(fullName, importPath));
    }
}
