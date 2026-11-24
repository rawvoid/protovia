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

package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.collect.IntArrayList;

import java.util.Collection;
import java.util.LinkedList;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @author Rawvoid
 */
@ProtoMessage
public record CollectionsRecord(
    @ProtoField(number = 1) LinkedList<String> linked,
    @ProtoField(number = 2) TreeSet<String> treeSet,
    @ProtoField(number = 3) SortedSet<String> sortedSet,
    @ProtoField(number = 4) NavigableSet<String> navigableSet,
    @ProtoField(number = 5) Collection<String> collection,
    @ProtoField(number = 6) Collection<Integer> intCollection,
    @ProtoField(number = 7) TreeMap<String, Integer> treeMap,
    @ProtoField(number = 8) SortedMap<String, Integer> sortedMap,
    @ProtoField(number = 9) NavigableMap<String, Integer> navigableMap,
    @ProtoField(number = 10) IntArrayList ints,
    @ProtoField(number = 11, type = ProtoType.FIXED32) IntArrayList fixedInts,
    @ProtoField(number = 12) CustomStringList customList,
    @ProtoField(number = 13) CustomScoreMap customMap
) {
}
