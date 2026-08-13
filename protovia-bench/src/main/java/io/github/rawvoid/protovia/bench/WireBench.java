package io.github.rawvoid.protovia.bench;

import io.github.rawvoid.protovia.ProtoVia;
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
        return ProtoVia.toBytes(protoviaSmall);
    }

    @Benchmark
    public byte[] officialSerializeSmall() {
        return officialSmall.toByteArray();
    }

    @Benchmark
    public byte[] protoviaSerializeCjk() {
        return ProtoVia.toBytes(protoviaCjk);
    }

    @Benchmark
    public byte[] officialSerializeCjk() {
        return officialCjk.toByteArray();
    }

    @Benchmark
    public byte[] protoviaSerializePacked() {
        return ProtoVia.toBytes(protoviaPacked);
    }

    @Benchmark
    public byte[] officialSerializePacked() {
        return officialPacked.toByteArray();
    }

    @Benchmark
    public User protoviaDeserializeSmall() {
        return ProtoVia.fromBytes(User.class, smallBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializeSmall()
        throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(smallBytes);
    }

    @Benchmark
    public User protoviaDeserializeCjk() {
        return ProtoVia.fromBytes(User.class, cjkBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializeCjk() throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(cjkBytes);
    }

    @Benchmark
    public User protoviaDeserializePacked() {
        return ProtoVia.fromBytes(User.class, packedBytes);
    }

    @Benchmark
    public io.github.rawvoid.protovia.bench.official.User officialDeserializePacked()
        throws Exception {
        return io.github.rawvoid.protovia.bench.official.User.parseFrom(packedBytes);
    }
}
