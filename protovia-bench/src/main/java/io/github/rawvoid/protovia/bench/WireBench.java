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

package io.github.rawvoid.protovia.bench;

import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.bench.model.User;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH suite comparing Protovia with official generated {@code protobuf-java} messages.
 *
 * @author Rawvoid
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class WireBench {

    private User protoviaSmall;
    private User protoviaCjk;
    private User protoviaPacked;
    private io.github.rawvoid.protovia.bench.official.User officialSmall;
    private io.github.rawvoid.protovia.bench.official.User officialCjk;
    private io.github.rawvoid.protovia.bench.official.User officialPacked;
    private byte[] smallBytes;
    private byte[] cjkBytes;
    private byte[] packedBytes;

    @Setup
    public void setup() throws Exception {
        protoviaSmall = Samples.protoviaSmall();
        protoviaCjk = Samples.protoviaCjk();
        protoviaPacked = Samples.protoviaPacked();
        officialSmall = Samples.officialSmall();
        officialCjk = Samples.officialCjk();
        officialPacked = Samples.officialPacked();
        smallBytes = officialSmall.toByteArray();
        cjkBytes = officialCjk.toByteArray();
        packedBytes = officialPacked.toByteArray();
    }

    @Benchmark
    public byte[] protoviaSerializeSmall() {
        return Protovia.toBytes(protoviaSmall);
    }

    @Benchmark
    public byte[] officialSerializeSmall() {
        return officialSmall.toByteArray();
    }

    @Benchmark
    public byte[] protoviaSerializeCjk() {
        return Protovia.toBytes(protoviaCjk);
    }

    @Benchmark
    public byte[] officialSerializeCjk() {
        return officialCjk.toByteArray();
    }

    @Benchmark
    public byte[] protoviaSerializePacked() {
        return Protovia.toBytes(protoviaPacked);
    }

    @Benchmark
    public byte[] officialSerializePacked() {
        return officialPacked.toByteArray();
    }

    @Benchmark
    public User protoviaDeserializeSmall() {
        return Protovia.fromBytes(User.class, smallBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializeSmall()
        throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(smallBytes);
    }

    @Benchmark
    public User protoviaDeserializeCjk() {
        return Protovia.fromBytes(User.class, cjkBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializeCjk() throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(cjkBytes);
    }

    @Benchmark
    public User protoviaDeserializePacked() {
        return Protovia.fromBytes(User.class, packedBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializePacked()
        throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(packedBytes);
    }
}
