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

package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.collect.IntArrayList;
import io.github.rawvoid.protovia.itest.model.CollectionsPojo;
import io.github.rawvoid.protovia.itest.model.CollectionsRecord;
import io.github.rawvoid.protovia.itest.model.CustomScoreMap;
import io.github.rawvoid.protovia.itest.model.CustomStringList;
import io.github.rawvoid.protovia.itest.model.OptionalBytesHolder;
import io.github.rawvoid.protovia.itest.model.OptionalBytesRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Rawvoid
 */
class CollectionsAndOptionalBytesRoundTripTest {

    @Test
    void optionalBytesPojoDistinguishesAbsentFromEmpty() {
        OptionalBytesHolder absent = new OptionalBytesHolder();
        byte[] absentBytes = Protovia.toBytes(absent);
        assertEquals(0, absentBytes.length);
        OptionalBytesHolder absentBack = Protovia.fromBytes(absentBytes, OptionalBytesHolder.class);
        assertNull(absentBack.raw);
        assertTrue(absentBack.opt.isEmpty());

        OptionalBytesHolder emptyPresent = new OptionalBytesHolder();
        emptyPresent.raw = new byte[0];
        emptyPresent.opt = Optional.of(new byte[0]);
        byte[] emptyBytes = Protovia.toBytes(emptyPresent);
        assertArrayEquals(new byte[]{0x0a, 0x00, 0x12, 0x00}, emptyBytes);
        OptionalBytesHolder emptyBack = Protovia.fromBytes(emptyBytes, OptionalBytesHolder.class);
        assertNotNull(emptyBack.raw);
        assertEquals(0, emptyBack.raw.length);
        assertTrue(emptyBack.opt.isPresent());
        assertEquals(0, emptyBack.opt.get().length);

        OptionalBytesHolder populated = new OptionalBytesHolder();
        populated.raw = new byte[]{1, 2, 3};
        populated.opt = Optional.of(new byte[]{4, 5});
        OptionalBytesHolder popBack = Protovia.fromBytes(Protovia.toBytes(populated), OptionalBytesHolder.class);
        assertArrayEquals(new byte[]{1, 2, 3}, popBack.raw);
        assertTrue(popBack.opt.isPresent());
        assertArrayEquals(new byte[]{4, 5}, popBack.opt.get());
    }

    @Test
    void optionalBytesRecordDistinguishesAbsentFromEmpty() {
        OptionalBytesRecord absent = new OptionalBytesRecord(null, Optional.empty());
        byte[] absentBytes = Protovia.toBytes(absent);
        assertEquals(0, absentBytes.length);
        OptionalBytesRecord absentBack = Protovia.fromBytes(absentBytes, OptionalBytesRecord.class);
        assertNull(absentBack.raw());
        assertTrue(absentBack.opt().isEmpty());

        OptionalBytesRecord emptyPresent = new OptionalBytesRecord(new byte[0], Optional.of(new byte[0]));
        byte[] emptyBytes = Protovia.toBytes(emptyPresent);
        assertArrayEquals(new byte[]{0x0a, 0x00, 0x12, 0x00}, emptyBytes);
        OptionalBytesRecord emptyBack = Protovia.fromBytes(emptyBytes, OptionalBytesRecord.class);
        assertNotNull(emptyBack.raw());
        assertEquals(0, emptyBack.raw().length);
        assertTrue(emptyBack.opt().isPresent());
        assertEquals(0, emptyBack.opt().get().length);

        OptionalBytesRecord populated = new OptionalBytesRecord(new byte[]{9, 8}, Optional.of(new byte[]{7}));
        OptionalBytesRecord popBack = Protovia.fromBytes(Protovia.toBytes(populated), OptionalBytesRecord.class);
        assertArrayEquals(new byte[]{9, 8}, popBack.raw());
        assertArrayEquals(new byte[]{7}, popBack.opt().orElseThrow());
    }

    @Test
    void concreteSortedAndPrimitiveCollectionsPojoRoundTrip() {
        CollectionsPojo pojo = new CollectionsPojo();
        pojo.linked = new LinkedList<>(List.of("b", "a"));
        pojo.treeSet = new TreeSet<>(List.of("c", "a", "b"));
        pojo.sortedSet = new TreeSet<>(List.of("z", "m"));
        pojo.navigableSet = new TreeSet<>(List.of("y", "x"));
        pojo.collection = List.of("one", "two");
        pojo.intCollection = List.of(10, 20, 30);
        pojo.treeMap = new TreeMap<>();
        pojo.treeMap.put("k2", 2);
        pojo.treeMap.put("k1", 1);
        pojo.sortedMap = new TreeMap<>();
        pojo.sortedMap.put("s2", 20);
        pojo.sortedMap.put("s1", 10);
        pojo.navigableMap = new TreeMap<>();
        pojo.navigableMap.put("n2", 200);
        pojo.navigableMap.put("n1", 100);
        pojo.ints = new IntArrayList(List.of(7, 8, 9));
        pojo.deque = new ArrayDeque<>(List.of("first", "second"));
        pojo.fixedInts = new IntArrayList(List.of(0x11223344, -1));

        CollectionsPojo back = Protovia.fromBytes(Protovia.toBytes(pojo), CollectionsPojo.class);
        assertInstanceOf(LinkedList.class, back.linked);
        assertEquals(List.of("b", "a"), back.linked);
        assertInstanceOf(TreeSet.class, back.treeSet);
        assertEquals(List.of("a", "b", "c"), new ArrayList<>(back.treeSet));
        assertInstanceOf(TreeSet.class, back.sortedSet);
        assertEquals(List.of("m", "z"), new ArrayList<>(back.sortedSet));
        assertInstanceOf(TreeSet.class, back.navigableSet);
        assertEquals(List.of("x", "y"), new ArrayList<>(back.navigableSet));
        assertEquals(List.of("one", "two"), new ArrayList<>(back.collection));
        assertInstanceOf(IntArrayList.class, back.intCollection);
        assertEquals(List.of(10, 20, 30), new ArrayList<>(back.intCollection));
        assertInstanceOf(TreeMap.class, back.treeMap);
        assertEquals(List.of("k1", "k2"), new ArrayList<>(back.treeMap.keySet()));
        assertEquals(1, back.treeMap.get("k1"));
        assertInstanceOf(TreeMap.class, back.sortedMap);
        assertEquals(List.of("s1", "s2"), new ArrayList<>(back.sortedMap.keySet()));
        assertInstanceOf(TreeMap.class, back.navigableMap);
        assertEquals(List.of("n1", "n2"), new ArrayList<>(back.navigableMap.keySet()));
        assertInstanceOf(IntArrayList.class, back.ints);
        assertEquals(List.of(7, 8, 9), back.ints);
        assertInstanceOf(ArrayDeque.class, back.deque);
        assertEquals(List.of("first", "second"), new ArrayList<>(back.deque));
        assertInstanceOf(IntArrayList.class, back.fixedInts);
        assertEquals(List.of(0x11223344, -1), back.fixedInts);
    }

    @Test
    void concreteSortedAndPrimitiveCollectionsPojoMergeFromUnmodifiable() {
        CollectionsPojo existing = new CollectionsPojo();
        existing.sortedSet = Collections.unmodifiableSortedSet(new TreeSet<>(List.of("a")));
        existing.sortedMap = Collections.unmodifiableSortedMap(new TreeMap<>(java.util.Map.of("k1", 1)));
        existing.collection = List.of("x");
        existing.intCollection = List.of(1);

        CollectionsPojo incoming = new CollectionsPojo();
        incoming.sortedSet = new TreeSet<>(List.of("b"));
        incoming.sortedMap = new TreeMap<>(java.util.Map.of("k2", 2));
        incoming.collection = List.of("y");
        incoming.intCollection = List.of(2);

        CollectionsPojo merged = Protovia.mergeFrom(Protovia.toBytes(incoming), existing);
        assertEquals(List.of("a", "b"), new ArrayList<>(merged.sortedSet));
        assertEquals(List.of("k1", "k2"), new ArrayList<>(merged.sortedMap.keySet()));
        assertEquals(List.of("x", "y"), new ArrayList<>(merged.collection));
        assertEquals(List.of(1, 2), new ArrayList<>(merged.intCollection));
    }

    @Test
    void concreteSortedAndPrimitiveCollectionsRecordRoundTripAndMerge() {
        TreeMap<String, Integer> map1 = new TreeMap<>();
        map1.put("b", 2);
        map1.put("a", 1);
        CustomStringList customList1 = new CustomStringList();
        customList1.addAll(List.of("alpha", "beta"));
        CustomScoreMap customMap1 = new CustomScoreMap();
        customMap1.put("x", 1);
        CollectionsRecord record = new CollectionsRecord(
            new LinkedList<>(List.of("first", "second")),
            new TreeSet<>(List.of("z", "a")),
            new TreeSet<>(List.of("m", "b")),
            new TreeSet<>(List.of("q", "p")),
            List.of("c1", "c2"),
            List.of(5, 6),
            map1,
            new TreeMap<>(map1),
            new TreeMap<>(map1),
            new IntArrayList(List.of(100, 200)),
            new IntArrayList(List.of(42, -7)),
            customList1,
            customMap1);

        CollectionsRecord back = Protovia.fromBytes(Protovia.toBytes(record), CollectionsRecord.class);
        assertEquals(record, back);
        assertInstanceOf(TreeSet.class, back.sortedSet());
        assertInstanceOf(TreeSet.class, back.navigableSet());
        assertInstanceOf(TreeMap.class, back.sortedMap());
        assertInstanceOf(TreeMap.class, back.navigableMap());
        assertInstanceOf(IntArrayList.class, back.intCollection());
        assertInstanceOf(IntArrayList.class, back.ints());
        assertInstanceOf(IntArrayList.class, back.fixedInts());
        assertEquals(List.of(42, -7), back.fixedInts());
        assertInstanceOf(CustomStringList.class, back.customList());
        assertEquals(List.of("alpha", "beta"), back.customList());
        assertInstanceOf(CustomScoreMap.class, back.customMap());
        assertEquals(1, back.customMap().get("x"));

        CustomStringList customListDelta = new CustomStringList();
        customListDelta.add("gamma");
        CustomScoreMap customMapDelta = new CustomScoreMap();
        customMapDelta.put("y", 2);
        CollectionsRecord delta = new CollectionsRecord(
            new LinkedList<>(List.of("third")),
            new TreeSet<>(List.of("k")),
            new TreeSet<>(List.of("a")),
            new TreeSet<>(List.of("r")),
            List.of("c3"),
            List.of(7),
            new TreeMap<>(java.util.Map.of("c", 3)),
            new TreeMap<>(java.util.Map.of("c", 3)),
            new TreeMap<>(java.util.Map.of("c", 3)),
            new IntArrayList(List.of(300)),
            new IntArrayList(List.of(99)),
            customListDelta,
            customMapDelta);
        CollectionsRecord merged = Protovia.mergeFrom(Protovia.toBytes(delta), record);
        assertEquals(List.of("first", "second", "third"), merged.linked());
        assertEquals(List.of("a", "k", "z"), new ArrayList<>(merged.treeSet()));
        assertEquals(List.of("a", "b", "m"), new ArrayList<>(merged.sortedSet()));
        assertEquals(List.of(100, 200, 300), merged.ints());
        assertEquals(List.of(42, -7, 99), merged.fixedInts());
        assertEquals(List.of("a", "b", "c"), new ArrayList<>(merged.sortedMap().keySet()));
        assertEquals(List.of("alpha", "beta", "gamma"), merged.customList());
        assertEquals(1, merged.customMap().get("x"));
        assertEquals(2, merged.customMap().get("y"));
        // Original record collections were not mutated
        assertFalse(record.ints().contains(300));
        assertFalse(record.fixedInts().contains(99));
        assertFalse(record.sortedSet().contains("a"));
        assertFalse(record.customList().contains("gamma"));
        assertFalse(record.customMap().containsKey("y"));
    }
}
