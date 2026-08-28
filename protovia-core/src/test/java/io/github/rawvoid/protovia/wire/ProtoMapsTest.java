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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    private static <K, V> List<K> keys(Iterable<Map.Entry<K, V>> entries) {
        List<K> keys = new ArrayList<>();
        for (Map.Entry<K, V> e : entries) {
            keys.add(e.getKey());
        }
        return keys;
    }
}
