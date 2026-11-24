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
 * Wide POJO exceeding the method-splitting threshold to verify round-trip, merge,
 * arrays, deterministic maps, and unknown-field enum exclusion across chunks.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class WideSplitPojo {

    @ProtoField(number = 1) public int f1;
    @ProtoField(number = 2) public String f2;
    @ProtoField(number = 3) public Status f3;
    @ProtoField(number = 4) public List<Integer> f4;
    @ProtoField(number = 5) public int[] f5;
    @ProtoField(number = 6) @ProtoDeterministic public Map<String, Integer> f6;
    @ProtoField(number = 7) public Optional<String> f7 = Optional.empty();
    @ProtoField(number = 8) public Address f8;
    @ProtoField(number = 9) public List<Double> f9;
    @ProtoField(number = 10) public long f10;
    @ProtoField(number = 11) public int f11;
    @ProtoField(number = 12) public int f12;
    @ProtoField(number = 13) public int f13;
    @ProtoField(number = 14) public int f14;
    @ProtoField(number = 15) public int f15;
    @ProtoField(number = 16) public int f16;
    @ProtoField(number = 17) public int f17;
    @ProtoField(number = 18) public int f18;
    @ProtoField(number = 19) public int f19;
    @ProtoField(number = 20) public int f20;
    @ProtoField(number = 21) public String f21;
    @ProtoField(number = 22) public String f22;
    @ProtoField(number = 23) public String f23;
    @ProtoField(number = 24) public String f24;
    @ProtoField(number = 25) public String f25;
    @ProtoField(number = 26) public String f26;
    @ProtoField(number = 27) public String f27;
    @ProtoField(number = 28) public String f28;
    @ProtoField(number = 29) public String f29;
    @ProtoField(number = 30) public String f30;
    @ProtoField(number = 31) public boolean f31;
    @ProtoField(number = 32) public boolean f32;
    @ProtoField(number = 33) public boolean f33;
    @ProtoField(number = 34) public boolean f34;
    @ProtoField(number = 35) public boolean f35;
    @ProtoField(number = 36) public int f36;
    @ProtoField(number = 37) public int f37;
    @ProtoField(number = 38) public int f38;
    @ProtoField(number = 39) public int f39;
    @ProtoField(number = 40) public int f40;
    @ProtoField(number = 41) public long f41;
    @ProtoField(number = 42) public long f42;
    @ProtoField(number = 43) public long f43;
    @ProtoField(number = 44) public long f44;
    @ProtoField(number = 45) public long f45;
    @ProtoField(number = 46) public String f46;
    @ProtoField(number = 47) public String f47;
    @ProtoField(number = 48) public String f48;
    @ProtoField(number = 49) public String f49;
    @ProtoField(number = 50) public String f50;
    @ProtoField(number = 51) public int f51;
    @ProtoField(number = 52) public int f52;
    @ProtoField(number = 53) public int f53;
    @ProtoField(number = 54) public int f54;
    @ProtoField(number = 55) public int f55;
    @ProtoField(number = 56) public Status f56;
    @ProtoField(number = 57) public List<String> f57;
    @ProtoField(number = 58) public int[] f58;
    @ProtoField(number = 59) public Map<String, Integer> f59;
    @ProtoField(number = 60) public String f60;
    @ProtoField(number = 61) public int f61;
    @ProtoField(number = 62) public int f62;
    @ProtoField(number = 63) public int f63;
    @ProtoField(number = 64) public int f64;
    @ProtoField(number = 65) public int f65;
    @ProtoField(number = 66) public int f66;
    @ProtoField(number = 67) public int f67;
    @ProtoField(number = 68) public int f68;
    @ProtoField(number = 69) public int f69;
    @ProtoField(number = 70) public int f70;
    @ProtoField(number = 71) public long f71;
    @ProtoField(number = 72) public long f72;
    @ProtoField(number = 73) public long f73;
    @ProtoField(number = 74) public long f74;
    @ProtoField(number = 75) public long f75;
    @ProtoField(number = 76) public boolean f76;
    @ProtoField(number = 77) public boolean f77;
    @ProtoField(number = 78) public boolean f78;
    @ProtoField(number = 79) public boolean f79;
    @ProtoField(number = 80) public boolean f80;
    @ProtoField(number = 81) public String f81;
    @ProtoField(number = 82) public String f82;
    @ProtoField(number = 83) public String f83;
    @ProtoField(number = 84) public String f84;
    @ProtoField(number = 85) public String f85;

    @ProtoUnknown
    public UnknownFields unknown;
}
