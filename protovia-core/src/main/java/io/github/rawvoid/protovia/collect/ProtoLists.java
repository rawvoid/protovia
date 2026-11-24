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

package io.github.rawvoid.protovia.collect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
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
     * Immutable or unmodifiable lists (such as {@code List.of} or {@code Collections.emptyList})
     * become a fresh instance so merge does not throw.
     */
    public static <E, C extends List<E>> C ensureMutableList(C list, Supplier<? extends C> empty) {
        if (list == null) {
            return empty.get();
        }
        if (isMutableList(list)) {
            return list;
        }
        C copy = empty.get();
        if (!list.isEmpty()) {
            copy.addAll(list);
        }
        return copy;
    }

    /**
     * Returns {@code list} when it can be appended in place, upgrading an empty non-primitive list
     * (such as a POJO field initialized to {@code new ArrayList<>()}) to the primitive list supplied
     * by {@code empty} without boxing or throwaway allocations.
     */
    public static <E, C extends List<E>> C ensureMutablePrimitiveList(C list, Supplier<? extends C> empty) {
        if (list == null || (list.isEmpty() && !(list instanceof AbstractPrimitiveList<?>))) {
            return empty.get();
        }
        if (isMutableList(list)) {
            return list;
        }
        C copy = empty.get();
        copy.addAll(list);
        return copy;
    }

    public static <E, C extends Set<E>> C ensureMutableSet(C set, Supplier<? extends C> empty) {
        if (set == null) {
            return empty.get();
        }
        if (isMutableSet(set)) {
            return set;
        }
        C copy = empty.get();
        if (!set.isEmpty()) {
            copy.addAll(set);
        }
        return copy;
    }

    public static <E, C extends Collection<E>> C ensureMutableCollection(C collection, Supplier<? extends C> empty) {
        if (collection == null) {
            return empty.get();
        }
        if (isMutableCollection(collection)) {
            return collection;
        }
        C copy = empty.get();
        if (!collection.isEmpty()) {
            copy.addAll(collection);
        }
        return copy;
    }

    public static <K, V, M extends Map<K, V>> M ensureMutableMap(M map, Supplier<? extends M> empty) {
        if (map == null) {
            return empty.get();
        }
        if (isMutableMap(map)) {
            return map;
        }
        M copy = empty.get();
        if (!map.isEmpty()) {
            copy.putAll(map);
        }
        return copy;
    }

    public static boolean isMutableList(List<?> list) {
        return list instanceof ArrayList
            || list instanceof AbstractPrimitiveList
            || list instanceof LinkedList
            || list instanceof Vector
            || list instanceof CopyOnWriteArrayList;
    }

    public static boolean isMutableSet(Set<?> set) {
        return set instanceof HashSet
            || set instanceof TreeSet
            || set instanceof CopyOnWriteArraySet
            || set instanceof ConcurrentSkipListSet
            || set instanceof EnumSet;
    }

    public static boolean isMutableCollection(Collection<?> collection) {
        return (collection instanceof List<?> list && isMutableList(list))
            || (collection instanceof Set<?> set && isMutableSet(set))
            || collection instanceof ArrayDeque;
    }

    public static boolean isMutableMap(Map<?, ?> map) {
        return map instanceof HashMap
            || map instanceof TreeMap
            || map instanceof ConcurrentHashMap
            || map instanceof ConcurrentSkipListMap
            || map instanceof EnumMap
            || map instanceof IdentityHashMap;
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
        if (additional <= 0) {
            return;
        }
        if (list instanceof IntArrayList ints) {
            ints.ensureCapacity(AbstractPrimitiveList.checkedCapacity(ints.size(), additional));
        }
    }

    public static void ensureLongCapacity(List<Long> list, int additional) {
        if (additional <= 0) {
            return;
        }
        if (list instanceof LongArrayList longs) {
            longs.ensureCapacity(AbstractPrimitiveList.checkedCapacity(longs.size(), additional));
        }
    }

    public static void ensureFloatCapacity(List<Float> list, int additional) {
        if (additional <= 0) {
            return;
        }
        if (list instanceof FloatArrayList floats) {
            floats.ensureCapacity(AbstractPrimitiveList.checkedCapacity(floats.size(), additional));
        }
    }

    public static void ensureDoubleCapacity(List<Double> list, int additional) {
        if (additional <= 0) {
            return;
        }
        if (list instanceof DoubleArrayList doubles) {
            doubles.ensureCapacity(AbstractPrimitiveList.checkedCapacity(doubles.size(), additional));
        }
    }

    public static void ensureBooleanCapacity(List<Boolean> list, int additional) {
        if (additional <= 0) {
            return;
        }
        if (list instanceof BooleanArrayList booleans) {
            booleans.ensureCapacity(AbstractPrimitiveList.checkedCapacity(booleans.size(), additional));
        }
    }
}
