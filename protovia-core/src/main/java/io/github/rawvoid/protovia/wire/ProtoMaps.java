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

package io.github.rawvoid.protovia.wire;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Map write helpers used by generated codecs.
 *
 * @author Rawvoid
 */
public final class ProtoMaps {

    private ProtoMaps() {
    }

    /**
     * Entries of {@code map} ordered by {@code byKey}. Maps of size {@code 0}
     * or {@code 1} are returned as-is.
     *
     * @param map   non-null map
     * @param byKey non-null key comparator (wire key order)
     * @return a stable iteration of {@code map}'s entries
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Iterable<Map.Entry<K, V>> sortedEntries(Map<K, V> map, Comparator<? super K> byKey) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(byKey, "byKey");
        int size = map.size();
        if (size < 2) {
            return map.entrySet();
        }
        Map.Entry<K, V>[] entries = (Map.Entry<K, V>[]) new Map.Entry<?, ?>[size];
        map.entrySet().toArray(entries);
        Arrays.sort(entries, Map.Entry.comparingByKey(byKey));
        return Arrays.asList(entries);
    }
}
