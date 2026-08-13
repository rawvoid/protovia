package io.github.rawvoid.protovia.collect;

import java.util.List;

/**
 * Decode helpers that keep packed / repeated scalars on primitive arrays when possible.
 */
public final class ProtoLists {

    private ProtoLists() {
    }

    public static void addInt(List<Integer> list, int value) {
        if (list instanceof IntArrayList ints) {
            ints.addInt(value);
        } else {
            list.add(value);
        }
    }

    public static void addLong(List<Long> list, long value) {
        if (list instanceof LongArrayList longs) {
            longs.addLong(value);
        } else {
            list.add(value);
        }
    }

    public static void addFloat(List<Float> list, float value) {
        if (list instanceof FloatArrayList floats) {
            floats.addFloat(value);
        } else {
            list.add(value);
        }
    }

    public static void addDouble(List<Double> list, double value) {
        if (list instanceof DoubleArrayList doubles) {
            doubles.addDouble(value);
        } else {
            list.add(value);
        }
    }

    public static void addBoolean(List<Boolean> list, boolean value) {
        if (list instanceof BooleanArrayList booleans) {
            booleans.addBoolean(value);
        } else {
            list.add(value);
        }
    }

    public static void ensureIntCapacity(List<Integer> list, int additional) {
        if (list instanceof IntArrayList ints) {
            ints.ensureCapacity(ints.size() + additional);
        }
    }

    public static void ensureLongCapacity(List<Long> list, int additional) {
        if (list instanceof LongArrayList longs) {
            longs.ensureCapacity(longs.size() + additional);
        }
    }

    public static void ensureFloatCapacity(List<Float> list, int additional) {
        if (list instanceof FloatArrayList floats) {
            floats.ensureCapacity(floats.size() + additional);
        }
    }

    public static void ensureDoubleCapacity(List<Double> list, int additional) {
        if (list instanceof DoubleArrayList doubles) {
            doubles.ensureCapacity(doubles.size() + additional);
        }
    }

    public static void ensureBooleanCapacity(List<Boolean> list, int additional) {
        if (list instanceof BooleanArrayList booleans) {
            booleans.ensureCapacity(booleans.size() + additional);
        }
    }
}
