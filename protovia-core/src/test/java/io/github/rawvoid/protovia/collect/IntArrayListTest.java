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

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class IntArrayListTest {

    private enum SampleKey {
        A, B
    }

    @Test
    void addIntAvoidsListBoxingPath() {
        IntArrayList list = new IntArrayList(2);
        list.addInt(3);
        list.addInt(270);
        assertEquals(2, list.size());
        assertEquals(3, list.getInt(0));
        assertEquals(270, list.getInt(1));
        assertEquals(List.of(3, 270), list);
        assertArrayEquals(new int[]{3, 270}, list.toIntArray());
    }

    @Test
    void equalsArrayList() {
        IntArrayList list = new IntArrayList();
        list.addInt(1);
        list.addInt(2);
        assertEquals(new ArrayList<>(List.of(1, 2)), list);
        assertEquals(list, List.of(1, 2));
        assertEquals(List.of(1, 2).hashCode(), list.hashCode());
    }

    @Test
    void defaultConstructorUsesEmptyBackingArray() {
        IntArrayList list = new IntArrayList();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertEquals(0, list.capacity());
        assertSame(list.toIntArray(), new IntArrayList(0).toIntArray());

        list.addInt(42);
        assertEquals(1, list.size());
        assertEquals(10, list.capacity());
        assertEquals(42, list.getInt(0));
    }

    @Test
    void ensureMutableListCopiesListOf() {
        List<Integer> immutable = List.of(1, 2);
        List<Integer> mutable = ProtoLists.ensureMutableList(immutable, IntArrayList::new);
        mutable.add(3);
        assertEquals(List.of(1, 2, 3), mutable);
        assertEquals(List.of(1, 2), immutable);
    }

    @Test
    void ensureMutableListKeepsArrayList() {
        List<Integer> original = new ArrayList<>(List.of(1));
        List<Integer> ensured = ProtoLists.ensureMutableList(original, IntArrayList::new);
        assertSame(original, ensured);
        ensured.add(2);
        assertEquals(List.of(1, 2), original);
    }

    @Test
    void ensureMutableListSupportsConcreteAndImmutableTypes() {
        Supplier<ArrayList<String>> throwingArrayList = () -> {
            throw new AssertionError("must not invoke supplier");
        };

        ArrayList<String> emptyArrayList = new ArrayList<>(64);
        ArrayList<String> ensuredArrayList = ProtoLists.ensureMutableList(emptyArrayList, throwingArrayList);
        assertSame(emptyArrayList, ensuredArrayList);

        LinkedList<String> emptyLinkedList = new LinkedList<>();
        LinkedList<String> ensuredLinkedList = ProtoLists.ensureMutableList(emptyLinkedList, () -> {
            throw new AssertionError("must not invoke supplier");
        });
        assertSame(emptyLinkedList, ensuredLinkedList);

        Vector<String> vector = new Vector<>();
        Vector<String> ensuredVector = ProtoLists.ensureMutableList(vector, Vector::new);
        assertSame(vector, ensuredVector);

        CopyOnWriteArrayList<String> cow = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> ensuredCow = ProtoLists.ensureMutableList(cow, CopyOnWriteArrayList::new);
        assertSame(cow, ensuredCow);

        IntArrayList emptyInts = new IntArrayList();
        IntArrayList ensuredInts = ProtoLists.ensureMutableList(emptyInts, () -> {
            throw new AssertionError("must not invoke supplier");
        });
        assertSame(emptyInts, ensuredInts);

        // PECS: concrete Supplier<ArrayList<String>> works when target is List<String>.
        Supplier<ArrayList<String>> concreteListSupplier = ArrayList::new;
        List<String> fromPecs = ProtoLists.ensureMutableList(List.of("p"), concreteListSupplier);
        fromPecs.add("q");
        assertEquals(List.of("p", "q"), fromPecs);

        List<String> fromNull = ProtoLists.ensureMutableList(null, ArrayList::new);
        fromNull.add("x");
        assertEquals(List.of("x"), fromNull);

        List<String> fromEmptyImmutable = ProtoLists.ensureMutableList(List.of(), ArrayList::new);
        fromEmptyImmutable.add("a");
        assertEquals(List.of("a"), fromEmptyImmutable);

        List<String> fromCollectionsEmpty = ProtoLists.ensureMutableList(Collections.emptyList(), ArrayList::new);
        fromCollectionsEmpty.add("b");
        assertEquals(List.of("b"), fromCollectionsEmpty);

        List<String> fromArraysAsList = ProtoLists.ensureMutableList(Arrays.asList("a", "b"), ArrayList::new);
        fromArraysAsList.add("c");
        assertEquals(List.of("a", "b", "c"), fromArraysAsList);

        List<String> unmodifiable = Collections.unmodifiableList(new ArrayList<>(List.of("u")));
        List<String> fromUnmodifiable = ProtoLists.ensureMutableList(unmodifiable, ArrayList::new);
        fromUnmodifiable.add("v");
        assertEquals(List.of("u", "v"), fromUnmodifiable);
    }

    @Test
    void ensureMutablePrimitiveListUpgradesEmptyNonPrimitiveAndKeepsPrimitiveInPlace() {
        Supplier<IntArrayList> throwingInts = () -> {
            throw new AssertionError("must not invoke supplier");
        };

        List<Integer> emptyArrayList = new ArrayList<>();
        List<Integer> upgraded = ProtoLists.ensureMutablePrimitiveList(emptyArrayList, IntArrayList::new);
        assertInstanceOf(IntArrayList.class, upgraded);
        assertTrue(upgraded.isEmpty());

        List<Integer> fromNull = ProtoLists.ensureMutablePrimitiveList(null, IntArrayList::new);
        assertInstanceOf(IntArrayList.class, fromNull);

        List<Integer> fromEmptyImmutable = ProtoLists.ensureMutablePrimitiveList(List.of(), IntArrayList::new);
        assertInstanceOf(IntArrayList.class, fromEmptyImmutable);

        IntArrayList emptyPrim = new IntArrayList(16);
        assertSame(emptyPrim, ProtoLists.ensureMutablePrimitiveList(emptyPrim, throwingInts));

        IntArrayList nonEmptyPrim = new IntArrayList(List.of(1, 2));
        assertSame(nonEmptyPrim, ProtoLists.ensureMutablePrimitiveList(nonEmptyPrim, throwingInts));

        List<Integer> nonEmptyArrayList = new ArrayList<>(List.of(1, 2));
        assertSame(nonEmptyArrayList, ProtoLists.ensureMutablePrimitiveList(nonEmptyArrayList, throwingInts));

        // PECS: concrete Supplier<IntArrayList> accepted when input is List<Integer>.
        Supplier<IntArrayList> intFactory = IntArrayList::new;
        List<Integer> copied = ProtoLists.ensureMutablePrimitiveList(List.of(1, 2), intFactory);
        assertInstanceOf(IntArrayList.class, copied);
        copied.add(3);
        assertEquals(List.of(1, 2, 3), copied);
    }

    @Test
    void ensureMutableSetSupportsConcreteAndImmutableTypes() {
        HashSet<String> emptyHashSet = new HashSet<>();
        HashSet<String> ensuredHashSet = ProtoLists.ensureMutableSet(emptyHashSet, HashSet::new);
        assertSame(emptyHashSet, ensuredHashSet);

        LinkedHashSet<String> emptyLinked = new LinkedHashSet<>();
        LinkedHashSet<String> ensuredLinked = ProtoLists.ensureMutableSet(emptyLinked, LinkedHashSet::new);
        assertSame(emptyLinked, ensuredLinked);

        TreeSet<String> emptyTree = new TreeSet<>();
        TreeSet<String> ensuredTree = ProtoLists.ensureMutableSet(emptyTree, TreeSet::new);
        assertSame(emptyTree, ensuredTree);

        CopyOnWriteArraySet<String> cow = new CopyOnWriteArraySet<>();
        assertSame(cow, ProtoLists.ensureMutableSet(cow, CopyOnWriteArraySet::new));

        ConcurrentSkipListSet<String> skip = new ConcurrentSkipListSet<>();
        assertSame(skip, ProtoLists.ensureMutableSet(skip, ConcurrentSkipListSet::new));

        EnumSet<SampleKey> enumSet = EnumSet.noneOf(SampleKey.class);
        assertSame(enumSet, ProtoLists.ensureMutableSet(enumSet, () -> EnumSet.noneOf(SampleKey.class)));

        Set<String> fromNull = ProtoLists.ensureMutableSet(null, LinkedHashSet::new);
        fromNull.add("a");
        assertEquals(Set.of("a"), fromNull);

        Set<String> fromEmptyImmutable = ProtoLists.ensureMutableSet(Set.of(), LinkedHashSet::new);
        fromEmptyImmutable.add("a");
        assertEquals(Set.of("a"), fromEmptyImmutable);

        Set<String> fromImmutable = ProtoLists.ensureMutableSet(Set.of("a"), LinkedHashSet::new);
        fromImmutable.add("b");
        assertEquals(Set.of("a", "b"), fromImmutable);

        Set<String> fromCollectionsEmpty = ProtoLists.ensureMutableSet(Collections.emptySet(), HashSet::new);
        fromCollectionsEmpty.add("c");
        assertEquals(Set.of("c"), fromCollectionsEmpty);
    }

    @Test
    void ensureMutableCollectionSupportsConcreteAndImmutableTypes() {
        Collection<String> fromImmutableList = ProtoLists.ensureMutableCollection(List.of("a"), ArrayList::new);
        fromImmutableList.add("b");
        assertEquals(List.of("a", "b"), fromImmutableList);

        Collection<String> fromImmutableSet = ProtoLists.ensureMutableCollection(Set.of("a"), HashSet::new);
        fromImmutableSet.add("b");
        assertEquals(Set.of("a", "b"), fromImmutableSet);

        Collection<String> fromEmptyImmutable = ProtoLists.ensureMutableCollection(Collections.emptyList(), ArrayList::new);
        fromEmptyImmutable.add("z");
        assertEquals(List.of("z"), fromEmptyImmutable);

        ArrayList<String> emptyArrayList = new ArrayList<>();
        ArrayList<String> ensuredArrayList = ProtoLists.ensureMutableCollection(emptyArrayList, () -> {
            throw new AssertionError("must not invoke supplier");
        });
        assertSame(emptyArrayList, ensuredArrayList);

        ArrayDeque<String> deque = new ArrayDeque<>();
        ArrayDeque<String> ensuredDeque = ProtoLists.ensureMutableCollection(deque, () -> {
            throw new AssertionError("must not invoke supplier");
        });
        assertSame(deque, ensuredDeque);

        LinkedList<String> linked = new LinkedList<>();
        LinkedList<String> ensuredLinked = ProtoLists.ensureMutableCollection(linked, LinkedList::new);
        assertSame(linked, ensuredLinked);

        TreeSet<String> tree = new TreeSet<>();
        TreeSet<String> ensuredTree = ProtoLists.ensureMutableCollection(tree, TreeSet::new);
        assertSame(tree, ensuredTree);

        IntArrayList ints = new IntArrayList();
        IntArrayList ensuredInts = ProtoLists.ensureMutableCollection(ints, IntArrayList::new);
        assertSame(ints, ensuredInts);

        Collection<String> fromNull = ProtoLists.ensureMutableCollection(null, ArrayList::new);
        fromNull.add("x");
        assertEquals(List.of("x"), fromNull);
    }

    @Test
    void ensureMutableMapSupportsConcreteAndImmutableTypes() {
        HashMap<String, Integer> emptyHashMap = new HashMap<>();
        HashMap<String, Integer> ensuredHashMap = ProtoLists.ensureMutableMap(emptyHashMap, HashMap::new);
        assertSame(emptyHashMap, ensuredHashMap);

        LinkedHashMap<String, Integer> emptyLinked = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> ensuredLinked = ProtoLists.ensureMutableMap(emptyLinked, LinkedHashMap::new);
        assertSame(emptyLinked, ensuredLinked);

        TreeMap<String, Integer> emptyTree = new TreeMap<>();
        TreeMap<String, Integer> ensuredTree = ProtoLists.ensureMutableMap(emptyTree, TreeMap::new);
        assertSame(emptyTree, ensuredTree);

        ConcurrentHashMap<String, Integer> chm = new ConcurrentHashMap<>();
        assertSame(chm, ProtoLists.ensureMutableMap(chm, ConcurrentHashMap::new));

        ConcurrentSkipListMap<String, Integer> cslm = new ConcurrentSkipListMap<>();
        assertSame(cslm, ProtoLists.ensureMutableMap(cslm, ConcurrentSkipListMap::new));

        EnumMap<SampleKey, Integer> enumMap = new EnumMap<>(SampleKey.class);
        assertSame(enumMap, ProtoLists.ensureMutableMap(enumMap, () -> new EnumMap<>(SampleKey.class)));

        IdentityHashMap<String, Integer> idMap = new IdentityHashMap<>();
        assertSame(idMap, ProtoLists.ensureMutableMap(idMap, IdentityHashMap::new));

        Map<String, Integer> fromNull = ProtoLists.ensureMutableMap(null, LinkedHashMap::new);
        fromNull.put("a", 1);
        assertEquals(Map.of("a", 1), fromNull);

        Map<String, Integer> fromEmptyImmutable = ProtoLists.ensureMutableMap(Map.of(), LinkedHashMap::new);
        fromEmptyImmutable.put("a", 1);
        assertEquals(Map.of("a", 1), fromEmptyImmutable);

        Map<String, Integer> fromImmutable = ProtoLists.ensureMutableMap(Map.of("a", 1), LinkedHashMap::new);
        fromImmutable.put("b", 2);
        assertEquals(Map.of("a", 1, "b", 2), fromImmutable);

        Map<String, Integer> fromCollectionsEmpty = ProtoLists.ensureMutableMap(Collections.emptyMap(), HashMap::new);
        fromCollectionsEmpty.put("k", 9);
        assertEquals(Map.of("k", 9), fromCollectionsEmpty);
    }

    @Test
    void isMutableChecksHandleNullAndImmutable() {
        assertFalse(ProtoLists.isMutableList(null));
        assertFalse(ProtoLists.isMutableList(List.of()));
        assertFalse(ProtoLists.isMutableList(Arrays.asList(1, 2)));
        assertTrue(ProtoLists.isMutableList(new IntArrayList()));
        assertTrue(ProtoLists.isMutableList(new LongArrayList()));
        assertTrue(ProtoLists.isMutableList(new FloatArrayList()));
        assertTrue(ProtoLists.isMutableList(new DoubleArrayList()));
        assertTrue(ProtoLists.isMutableList(new BooleanArrayList()));

        assertFalse(ProtoLists.isMutableSet(null));
        assertFalse(ProtoLists.isMutableSet(Set.of()));
        assertTrue(ProtoLists.isMutableSet(new LinkedHashSet<>()));
        assertTrue(ProtoLists.isMutableSet(new TreeSet<>()));

        assertFalse(ProtoLists.isMutableCollection(null));
        assertFalse(ProtoLists.isMutableCollection(List.of()));
        assertTrue(ProtoLists.isMutableCollection(new ArrayDeque<>()));

        assertFalse(ProtoLists.isMutableMap(null));
        assertFalse(ProtoLists.isMutableMap(Map.of()));
        assertTrue(ProtoLists.isMutableMap(new LinkedHashMap<>()));
        assertTrue(ProtoLists.isMutableMap(new TreeMap<>()));
    }

    @Test
    void protoListsDispatchesToAddIntAndGuardsCapacityOverflow() {
        IntArrayList list = new IntArrayList();
        ProtoLists.addInt(list, 9);
        ProtoLists.ensureIntCapacity(list, 0);
        ProtoLists.ensureIntCapacity(list, -5);
        ProtoLists.ensureIntCapacity(list, 8);
        ProtoLists.addInt(list, 10);
        assertEquals(List.of(9, 10), list);
        assertThrows(OutOfMemoryError.class, () -> ProtoLists.ensureIntCapacity(list, Integer.MAX_VALUE));

        LongArrayList longs = new LongArrayList(List.of(1L));
        assertThrows(OutOfMemoryError.class, () -> ProtoLists.ensureLongCapacity(longs, Integer.MAX_VALUE));

        FloatArrayList floats = new FloatArrayList(List.of(1f));
        assertThrows(OutOfMemoryError.class, () -> ProtoLists.ensureFloatCapacity(floats, Integer.MAX_VALUE));

        DoubleArrayList doubles = new DoubleArrayList(List.of(1d));
        assertThrows(OutOfMemoryError.class, () -> ProtoLists.ensureDoubleCapacity(doubles, Integer.MAX_VALUE));

        BooleanArrayList booleans = new BooleanArrayList(List.of(true));
        assertThrows(OutOfMemoryError.class, () -> ProtoLists.ensureBooleanCapacity(booleans, Integer.MAX_VALUE));
    }
}
