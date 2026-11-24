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

import io.github.rawvoid.protovia.ProtoException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Map write helpers used by generated codecs.
 *
 * @author Rawvoid
 */
public final class ProtoMaps {

    private static final Map.Entry<?, ?>[] EMPTY_ENTRIES = new Map.Entry<?, ?>[0];
    private static final KeyedEntry<?, ?>[] EMPTY_KEYED_ENTRIES = new KeyedEntry<?, ?>[0];
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private ProtoMaps() {
    }

    /**
     * Pre-adapted map entry pairing a converted wire key with its original value.
     *
     * @param <W> wire key type
     * @param <V> map value type
     */
    public record KeyedEntry<W, V>(W wireKey, V value) {
    }

    /**
     * Entries of {@code map} ordered by {@code byKey}. Maps of size {@code 0}
     * or {@code 1} are returned as-is after validating non-null keys.
     *
     * @param map   non-null map
     * @param byKey non-null key comparator (wire key order)
     * @return a stable iteration of {@code map}'s entries
     */
    public static <K, V> Iterable<Map.Entry<K, V>> sortedEntries(Map<K, V> map, Comparator<? super K> byKey) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(byKey, "byKey");
        if (map.size() < 2) {
            Set<Map.Entry<K, V>> entrySet = map.entrySet();
            for (Map.Entry<K, V> entry : entrySet) {
                if (entry == null || entry.getKey() == null) {
                    throw new ProtoException("map key cannot be null");
                }
            }
            return entrySet;
        }
        return Arrays.asList(sortedEntriesArray(map, byKey));
    }

    /**
     * Snapshot array of {@code map}'s entries ordered by {@code byKey}.
     * Rejects {@code null} keys with {@link ProtoException} prior to sorting.
     *
     * @param map   non-null map
     * @param byKey non-null key comparator (wire key order)
     * @return sorted array of map entries
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map.Entry<K, V>[] sortedEntriesArray(Map<K, V> map, Comparator<? super K> byKey) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(byKey, "byKey");
        Map.Entry<K, V>[] entries = (Map.Entry<K, V>[]) map.entrySet().toArray(EMPTY_ENTRIES);
        for (Map.Entry<K, V> entry : entries) {
            if (entry == null || entry.getKey() == null) {
                throw new ProtoException("map key cannot be null");
            }
        }
        if (entries.length >= 2) {
            Arrays.sort(entries, (a, b) -> byKey.compare(a.getKey(), b.getKey()));
        }
        return entries;
    }

    /**
     * Converts each map key once via {@code toWire} (Schwartzian transform) and returns the entries
     * ordered by {@code byWireKey}. Rejects {@code null} keys, {@code null} values, and {@code null}
     * adapted keys with {@link ProtoException}.
     *
     * @param map       non-null map
     * @param toWire    non-null key adapter function (invoked once per entry)
     * @param byWireKey non-null wire key comparator
     * @return sorted array of {@link KeyedEntry} instances
     */
    @SuppressWarnings("unchecked")
    public static <K, W, V> KeyedEntry<W, V>[] sortedAdaptedEntries(
        Map<K, V> map, Function<? super K, ? extends W> toWire, Comparator<? super W> byWireKey) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(toWire, "toWire");
        Objects.requireNonNull(byWireKey, "byWireKey");
        int initialSize = map.size();
        KeyedEntry<W, V>[] entries = initialSize == 0
            ? (KeyedEntry<W, V>[]) EMPTY_KEYED_ENTRIES
            : (KeyedEntry<W, V>[]) new KeyedEntry<?, ?>[initialSize];
        int count = 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry == null) {
                throw new ProtoException("map entry cannot contain null");
            }
            K key = entry.getKey();
            V value = entry.getValue();
            if (key == null || value == null) {
                throw new ProtoException("map entry cannot contain null");
            }
            W wireKey = toWire.apply(key);
            if (wireKey == null) {
                throw new ProtoException("adapted map key cannot be null");
            }
            if (count == entries.length) {
                int nextLen = entries.length == 0
                    ? 4
                    : (entries.length > (MAX_ARRAY_SIZE >>> 1) ? MAX_ARRAY_SIZE : entries.length << 1);
                if (count >= nextLen) {
                    throw new ProtoException("map too large to sort");
                }
                entries = Arrays.copyOf(entries, nextLen);
            }
            entries[count++] = new KeyedEntry<>(wireKey, value);
        }
        if (count == 0) {
            return (KeyedEntry<W, V>[]) EMPTY_KEYED_ENTRIES;
        }
        if (count != entries.length) {
            entries = Arrays.copyOf(entries, count);
        }
        if (entries.length >= 2) {
            Arrays.sort(entries, (a, b) -> byWireKey.compare(a.wireKey(), b.wireKey()));
        }
        return entries;
    }
}
