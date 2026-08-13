package io.github.rawvoid.protovia.collect;

import java.util.*;
import java.util.function.Supplier;

/**
 * Decode helpers that keep packed / repeated scalars on primitive arrays when possible.
 *
 * @author Rawvoid
 */
public final class ProtoLists {

    private ProtoLists() {
    }

    /**
     * Returns {@code list} when it can be appended in place; otherwise a mutable copy from {@code empty}.
     * {@code List.of} / empty lists become a fresh instance so merge does not throw.
     */
    public static <E> List<E> ensureMutableList(List<E> list, Supplier<List<E>> empty) {
        if (list == null || list.isEmpty()) {
            return empty.get();
        }
        if (isMutableList(list)) {
            return list;
        }
        List<E> copy = empty.get();
        copy.addAll(list);
        return copy;
    }

    public static <E> Set<E> ensureMutableSet(Set<E> set, Supplier<Set<E>> empty) {
        if (set == null || set.isEmpty()) {
            return empty.get();
        }
        if (set instanceof HashSet || set instanceof LinkedHashSet) {
            return set;
        }
        Set<E> copy = empty.get();
        copy.addAll(set);
        return copy;
    }

    public static <K, V> Map<K, V> ensureMutableMap(Map<K, V> map, Supplier<Map<K, V>> empty) {
        if (map == null || map.isEmpty()) {
            return empty.get();
        }
        if (map instanceof HashMap || map instanceof LinkedHashMap) {
            return map;
        }
        Map<K, V> copy = empty.get();
        copy.putAll(map);
        return copy;
    }

    public static boolean isMutableList(List<?> list) {
        return list instanceof ArrayList
            || list instanceof IntArrayList
            || list instanceof LongArrayList
            || list instanceof FloatArrayList
            || list instanceof DoubleArrayList
            || list instanceof BooleanArrayList;
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
