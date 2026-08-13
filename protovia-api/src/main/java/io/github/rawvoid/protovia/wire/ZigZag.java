package io.github.rawvoid.protovia.wire;

public final class ZigZag {

    private ZigZag() {
    }

    public static int encode32(int n) {
        return (n << 1) ^ (n >> 31);
    }

    public static int decode32(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    public static long encode64(long n) {
        return (n << 1) ^ (n >> 63);
    }

    public static long decode64(long n) {
        return (n >>> 1) ^ -(n & 1);
    }
}
