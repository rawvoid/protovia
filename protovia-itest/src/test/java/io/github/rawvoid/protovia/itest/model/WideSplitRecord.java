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
import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wide record exceeding the method-splitting threshold to verify {@code _ReadState}
 * round-trip, merge, arrays, deterministic maps, and unknown-field enum exclusion.
 *
 * @author Rawvoid
 */
@ProtoMessage
public record WideSplitRecord(
    @ProtoField(number = 1) int f1,
    @ProtoField(number = 2) String f2,
    @ProtoField(number = 3) Status f3,
    @ProtoField(number = 4) List<Integer> f4,
    @ProtoField(number = 5) int[] f5,
    @ProtoField(number = 6) @ProtoDeterministic Map<String, Integer> f6,
    @ProtoField(number = 7) Optional<String> f7,
    @ProtoField(number = 8) Address f8,
    @ProtoField(number = 9) int f9,
    @ProtoField(number = 10) int f10,
    @ProtoField(number = 11) int f11,
    @ProtoField(number = 12) int f12,
    @ProtoField(number = 13) int f13,
    @ProtoField(number = 14) int f14,
    @ProtoField(number = 15) int f15,
    @ProtoField(number = 16) int f16,
    @ProtoField(number = 17) int f17,
    @ProtoField(number = 18) int f18,
    @ProtoField(number = 19) int f19,
    @ProtoField(number = 20) int f20,
    @ProtoField(number = 21) String f21,
    @ProtoField(number = 22) String f22,
    @ProtoField(number = 23) String f23,
    @ProtoField(number = 24) String f24,
    @ProtoField(number = 25) String f25,
    @ProtoField(number = 26) String f26,
    @ProtoField(number = 27) String f27,
    @ProtoField(number = 28) String f28,
    @ProtoField(number = 29) String f29,
    @ProtoField(number = 30) String f30,
    @ProtoField(number = 31) boolean f31,
    @ProtoField(number = 32) boolean f32,
    @ProtoField(number = 33) boolean f33,
    @ProtoField(number = 34) boolean f34,
    @ProtoField(number = 35) boolean f35,
    @ProtoField(number = 36) int f36,
    @ProtoField(number = 37) int f37,
    @ProtoField(number = 38) int f38,
    @ProtoField(number = 39) int f39,
    @ProtoField(number = 40) int f40,
    @ProtoField(number = 41) int f41,
    @ProtoField(number = 42) int f42,
    @ProtoField(number = 43) int f43,
    @ProtoField(number = 44) int f44,
    @ProtoField(number = 45) int f45,
    @ProtoField(number = 46) String f46,
    @ProtoField(number = 47) String f47,
    @ProtoField(number = 48) String f48,
    @ProtoField(number = 49) String f49,
    @ProtoField(number = 50) String f50,
    @ProtoField(number = 51) int f51,
    @ProtoField(number = 52) int f52,
    @ProtoField(number = 53) int f53,
    @ProtoField(number = 54) int f54,
    @ProtoField(number = 55) int f55,
    @ProtoField(number = 56) Status f56,
    @ProtoField(number = 57) List<String> f57,
    @ProtoField(number = 58) int[] f58,
    @ProtoField(number = 59) Map<String, Integer> f59,
    @ProtoField(number = 60) String f60,
    @ProtoField(number = 61) int f61,
    @ProtoField(number = 62) int f62,
    @ProtoField(number = 63) int f63,
    @ProtoField(number = 64) int f64,
    @ProtoField(number = 65) int f65,
    @ProtoField(number = 66) int f66,
    @ProtoField(number = 67) int f67,
    @ProtoField(number = 68) int f68,
    @ProtoField(number = 69) int f69,
    @ProtoField(number = 70) int f70,
    @ProtoField(number = 71) long f71,
    @ProtoField(number = 72) long f72,
    @ProtoField(number = 73) long f73,
    @ProtoField(number = 74) long f74,
    @ProtoField(number = 75) long f75,
    @ProtoField(number = 76) boolean f76,
    @ProtoField(number = 77) boolean f77,
    @ProtoField(number = 78) boolean f78,
    @ProtoField(number = 79) boolean f79,
    @ProtoField(number = 80) boolean f80,
    @ProtoField(number = 81) String f81,
    @ProtoField(number = 82) String f82,
    @ProtoField(number = 83) String f83,
    @ProtoField(number = 84) String f84,
    @ProtoField(number = 85) String f85,
    @ProtoUnknown UnknownFields unknown,
    String transientNote
) {
}
