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
import io.github.rawvoid.protovia.itest.model.AdaptedSorted;
import io.github.rawvoid.protovia.itest.model.SortedByField;
import io.github.rawvoid.protovia.itest.model.SortedByType;
import io.github.rawvoid.protovia.itest.model.SortedLeaf;
import io.github.rawvoid.protovia.itest.model.SortedOuter;
import io.github.rawvoid.protovia.itest.model.UnsignedSorted;
import io.github.rawvoid.protovia.itest.model.UnsortedInner;
import io.github.rawvoid.protovia.itest.model.User;
import io.github.rawvoid.protovia.itest.model.detpkg.PackagedMaps;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Rawvoid
 */
class DeterministicMapTest {

    @Test
    void unmarkedMapFollowsInsertionOrder() {
        User a = new User();
        a.setScores(linked("b", 1, "a", 2));
        User b = new User();
        b.setScores(linked("a", 2, "b", 1));
        assertFalse(Arrays.equals(Protovia.toBytes(a), Protovia.toBytes(b)));
    }

    @Test
    void fieldAnnotationStabilizesBytes() {
        SortedByField a = new SortedByField();
        a.labels = linked("b", 1, "a", 2, "c", 3);
        SortedByField b = new SortedByField();
        b.labels = linked("c", 3, "a", 2, "b", 1);
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));
        assertEquals(a.labels, Protovia.fromBytes(Protovia.toBytes(a), SortedByField.class).labels);
    }

    @Test
    void typeAnnotationSortsUnlessFieldOptsOut() {
        SortedByType a = new SortedByType();
        a.headers = linked("b", 1, "a", 2);
        a.blobs = linked("b", 1, "a", 2);
        SortedByType b = new SortedByType();
        b.headers = linked("a", 2, "b", 1);
        b.blobs = linked("b", 1, "a", 2);
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));

        SortedByType c = new SortedByType();
        c.headers = linked("a", 2, "b", 1);
        c.blobs = linked("a", 2, "b", 1);
        assertFalse(Arrays.equals(Protovia.toBytes(a), Protovia.toBytes(c)));
    }

    @Test
    void mixinAnnotationSortsInheritedAndLeafMaps() {
        SortedLeaf a = new SortedLeaf();
        a.inherited = linked("b", 1, "a", 2);
        a.own = linked("z", 9, "m", 8);
        SortedLeaf b = new SortedLeaf();
        b.inherited = linked("a", 2, "b", 1);
        b.own = linked("m", 8, "z", 9);
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));
    }

    @Test
    void packageInfoSortsMapsInThatPackage() {
        PackagedMaps a = new PackagedMaps();
        a.labels = linked("b", 1, "a", 2);
        PackagedMaps b = new PackagedMaps();
        b.labels = linked("a", 2, "b", 1);
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));
    }

    @Test
    void nestedMessageMapsAreIndependent() {
        SortedOuter a = new SortedOuter();
        a.inner = new UnsortedInner();
        a.inner.items = linked("b", 1, "a", 2);
        SortedOuter b = new SortedOuter();
        b.inner = new UnsortedInner();
        b.inner.items = linked("a", 2, "b", 1);
        assertFalse(Arrays.equals(Protovia.toBytes(a), Protovia.toBytes(b)));
    }

    @Test
    void uint64KeysUseUnsignedOrder() {
        UnsignedSorted a = new UnsignedSorted();
        a.values.put(-1L, "max");
        a.values.put(1L, "one");
        UnsignedSorted b = new UnsignedSorted();
        b.values.put(1L, "one");
        b.values.put(-1L, "max");
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));
        assertEquals(a.values, Protovia.fromBytes(Protovia.toBytes(a), UnsignedSorted.class).values);
    }

    @Test
    void adaptedKeysSortByWireValue() {
        UUID late = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID early = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AdaptedSorted a = new AdaptedSorted();
        a.ids.put(late, 2);
        a.ids.put(early, 1);
        AdaptedSorted b = new AdaptedSorted();
        b.ids.put(early, 1);
        b.ids.put(late, 2);
        assertArrayEquals(Protovia.toBytes(a), Protovia.toBytes(b));
    }

    private static Map<String, Integer> linked(Object... kv) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return map;
    }
}
