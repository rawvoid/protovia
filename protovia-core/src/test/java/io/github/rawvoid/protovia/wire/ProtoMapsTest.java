/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Rawvoid
 */
class ProtoMapsTest {

    @Test
    void smallMapsAreNotCopied() {
        Map<String, Integer> empty = new LinkedHashMap<>();
        Map<String, Integer> one = new LinkedHashMap<>();
        one.put("a", 1);
        assertSame(empty.entrySet(), ProtoMaps.sortedEntries(empty, Comparator.naturalOrder()));
        assertSame(one.entrySet(), ProtoMaps.sortedEntries(one, Comparator.naturalOrder()));
    }

    @Test
    void sortsByKey() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("c", 3);
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(List.of("a", "b", "c"), keys(ProtoMaps.sortedEntries(map, Comparator.naturalOrder())));

        Map.Entry<String, Integer>[] arr = ProtoMaps.sortedEntriesArray(map, Comparator.naturalOrder());
        assertEquals(3, arr.length);
        assertEquals("a", arr[0].getKey());
        assertEquals("b", arr[1].getKey());
        assertEquals("c", arr[2].getKey());
    }

    @Test
    void unsignedLongOrder() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(-1L, "max");
        map.put(1L, "one");
        map.put(0L, "zero");
        assertEquals(
            List.of(0L, 1L, -1L),
            keys(ProtoMaps.sortedEntries(map, Long::compareUnsigned)));
    }

    @Test
    void nullKeyThrowsProtoException() {
        Map<String, Integer> singleNull = new HashMap<>();
        singleNull.put(null, 1);
        assertThrows(ProtoException.class, () -> ProtoMaps.sortedEntries(singleNull, Comparator.naturalOrder()));
        assertThrows(ProtoException.class, () -> ProtoMaps.sortedEntriesArray(singleNull, Comparator.naturalOrder()));

        Map<String, Integer> multiWithNull = new HashMap<>();
        multiWithNull.put("a", 1);
        multiWithNull.put(null, 2);
        multiWithNull.put("b", 3);
        assertThrows(ProtoException.class, () -> ProtoMaps.sortedEntries(multiWithNull, Comparator.naturalOrder()));
        assertThrows(ProtoException.class, () -> ProtoMaps.sortedEntriesArray(multiWithNull, Comparator.naturalOrder()));
    }

    @Test
    void sortedAdaptedEntriesCallsToWireOncePerEntryAndSorts() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 10; i >= 1; i--) {
            map.put(i, "v" + i);
        }
        AtomicInteger calls = new AtomicInteger();
        ProtoMaps.KeyedEntry<String, String>[] sorted = ProtoMaps.sortedAdaptedEntries(
            map,
            k -> {
                calls.incrementAndGet();
                return String.format("%02d", k);
            },
            Comparator.naturalOrder());

        assertEquals(10, calls.get());
        assertEquals(10, sorted.length);
        for (int i = 0; i < 10; i++) {
            assertEquals(String.format("%02d", i + 1), sorted[i].wireKey());
            assertEquals("v" + (i + 1), sorted[i].value());
        }
    }

    @Test
    void sortedAdaptedEntriesUnsignedOrderAndEdgeCases() {
        Map<String, String> empty = Map.of();
        AtomicInteger emptyCalls = new AtomicInteger();
        ProtoMaps.KeyedEntry<Long, String>[] emptyResult = ProtoMaps.sortedAdaptedEntries(
            empty,
            k -> {
                emptyCalls.incrementAndGet();
                return Long.parseLong(k);
            },
            Long::compareUnsigned);
        assertEquals(0, emptyResult.length);
        assertEquals(0, emptyCalls.get());

        Map<String, String> map = new LinkedHashMap<>();
        map.put("-1", "max");
        map.put("1", "one");
        map.put("0", "zero");
        AtomicInteger calls = new AtomicInteger();
        ProtoMaps.KeyedEntry<Long, String>[] sorted = ProtoMaps.sortedAdaptedEntries(
            map,
            k -> {
                calls.incrementAndGet();
                return Long.parseLong(k);
            },
            Long::compareUnsigned);
        assertEquals(3, calls.get());
        assertEquals(0L, sorted[0].wireKey());
        assertEquals("zero", sorted[0].value());
        assertEquals(1L, sorted[1].wireKey());
        assertEquals("one", sorted[1].value());
        assertEquals(-1L, sorted[2].wireKey());
        assertEquals("max", sorted[2].value());

        // Null key, null value, null adapted wire key all throw ProtoException
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "v");
        assertThrows(ProtoException.class,
            () -> ProtoMaps.sortedAdaptedEntries(nullKey, k -> k, Comparator.naturalOrder()));

        Map<String, String> nullVal = new HashMap<>();
        nullVal.put("k", null);
        assertThrows(ProtoException.class,
            () -> ProtoMaps.sortedAdaptedEntries(nullVal, k -> k, Comparator.naturalOrder()));

        Map<String, String> valid = Map.of("k", "v");
        assertThrows(ProtoException.class,
            () -> ProtoMaps.sortedAdaptedEntries(valid, k -> null, Comparator.naturalOrder()));
    }

    @Test
    void handlesMapSizeDisagreementOnGrowAndShrink() {
        List<Map.Entry<String, Integer>> sixEntries = List.of(
            Map.entry("f", 6),
            Map.entry("b", 2),
            Map.entry("d", 4),
            Map.entry("a", 1),
            Map.entry("e", 5),
            Map.entry("c", 3)
        );
        List<Map.Entry<String, Integer>> twoEntries = List.of(
            Map.entry("z", 26),
            Map.entry("m", 13)
        );

        // Grow from reported size 0 -> actual 6
        Map<String, Integer> grewFromZero = misreportingMap(0, sixEntries);
        Map.Entry<String, Integer>[] arrFromZero = ProtoMaps.sortedEntriesArray(grewFromZero, Comparator.naturalOrder());
        assertEquals(6, arrFromZero.length);
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), keys(List.of(arrFromZero)));

        AtomicInteger callsFromZero = new AtomicInteger();
        ProtoMaps.KeyedEntry<String, Integer>[] adaptedFromZero = ProtoMaps.sortedAdaptedEntries(
            grewFromZero,
            k -> {
                callsFromZero.incrementAndGet();
                return k.toUpperCase();
            },
            Comparator.naturalOrder());
        assertEquals(6, callsFromZero.get());
        assertEquals(6, adaptedFromZero.length);
        assertEquals("A", adaptedFromZero[0].wireKey());
        assertEquals(1, adaptedFromZero[0].value());
        assertEquals("F", adaptedFromZero[5].wireKey());
        assertEquals(6, adaptedFromZero[5].value());

        // Grow from reported size 1 -> actual 6 (forces multiple doublings)
        Map<String, Integer> grewFromOne = misreportingMap(1, sixEntries);
        Map.Entry<String, Integer>[] arrFromOne = ProtoMaps.sortedEntriesArray(grewFromOne, Comparator.naturalOrder());
        assertEquals(6, arrFromOne.length);
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), keys(List.of(arrFromOne)));

        AtomicInteger callsFromOne = new AtomicInteger();
        ProtoMaps.KeyedEntry<String, Integer>[] adaptedFromOne = ProtoMaps.sortedAdaptedEntries(
            grewFromOne,
            k -> {
                callsFromOne.incrementAndGet();
                return k.toUpperCase();
            },
            Comparator.naturalOrder());
        assertEquals(6, callsFromOne.get());
        assertEquals(6, adaptedFromOne.length);
        assertEquals("A", adaptedFromOne[0].wireKey());
        assertEquals("F", adaptedFromOne[5].wireKey());

        // Shrink from reported size 5 -> actual 2 (no trailing nulls)
        Map<String, Integer> shrunkToTwo = misreportingMap(5, twoEntries);
        Map.Entry<String, Integer>[] arrShrunk = ProtoMaps.sortedEntriesArray(shrunkToTwo, Comparator.naturalOrder());
        assertEquals(2, arrShrunk.length);
        assertEquals(List.of("m", "z"), keys(List.of(arrShrunk)));

        AtomicInteger callsShrunk = new AtomicInteger();
        ProtoMaps.KeyedEntry<String, Integer>[] adaptedShrunk = ProtoMaps.sortedAdaptedEntries(
            shrunkToTwo,
            k -> {
                callsShrunk.incrementAndGet();
                return k.toUpperCase();
            },
            Comparator.naturalOrder());
        assertEquals(2, callsShrunk.get());
        assertEquals(2, adaptedShrunk.length);
        assertEquals("M", adaptedShrunk[0].wireKey());
        assertEquals(13, adaptedShrunk[0].value());
        assertEquals("Z", adaptedShrunk[1].wireKey());
        assertEquals(26, adaptedShrunk[1].value());

        // Shrink from reported size 3 -> actual 0
        Map<String, Integer> shrunkToZero = misreportingMap(3, List.of());
        Map.Entry<String, Integer>[] arrEmpty = ProtoMaps.sortedEntriesArray(shrunkToZero, Comparator.naturalOrder());
        assertEquals(0, arrEmpty.length);

        AtomicInteger callsEmpty = new AtomicInteger();
        ProtoMaps.KeyedEntry<String, Integer>[] adaptedEmpty = ProtoMaps.sortedAdaptedEntries(
            shrunkToZero,
            k -> {
                callsEmpty.incrementAndGet();
                return k;
            },
            Comparator.naturalOrder());
        assertEquals(0, callsEmpty.get());
        assertEquals(0, adaptedEmpty.length);
    }

    private static <K, V> Map<K, V> misreportingMap(int reportedSize, List<Map.Entry<K, V>> actualEntries) {
        return new AbstractMap<>() {
            @Override
            public int size() {
                return reportedSize;
            }

            @Override
            public Set<Entry<K, V>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<K, V>> iterator() {
                        return actualEntries.iterator();
                    }

                    @Override
                    public int size() {
                        return reportedSize;
                    }
                };
            }
        };
    }

    private static <K, V> List<K> keys(Iterable<Map.Entry<K, V>> entries) {
        List<K> keys = new ArrayList<>();
        for (Map.Entry<K, V> e : entries) {
            keys.add(e.getKey());
        }
        return keys;
    }
}
