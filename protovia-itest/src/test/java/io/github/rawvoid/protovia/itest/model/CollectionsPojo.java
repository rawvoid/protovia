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

import java.util.ArrayDeque;
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
public class CollectionsPojo {

    @ProtoField(number = 1)
    public LinkedList<String> linked;

    @ProtoField(number = 2)
    public TreeSet<String> treeSet;

    @ProtoField(number = 3)
    public SortedSet<String> sortedSet;

    @ProtoField(number = 4)
    public NavigableSet<String> navigableSet;

    @ProtoField(number = 5)
    public Collection<String> collection;

    @ProtoField(number = 6)
    public Collection<Integer> intCollection;

    @ProtoField(number = 7)
    public TreeMap<String, Integer> treeMap;

    @ProtoField(number = 8)
    public SortedMap<String, Integer> sortedMap;

    @ProtoField(number = 9)
    public NavigableMap<String, Integer> navigableMap;

    @ProtoField(number = 10)
    public IntArrayList ints;

    @ProtoField(number = 11)
    public ArrayDeque<String> deque;

    @ProtoField(number = 12, type = ProtoType.FIXED32)
    public IntArrayList fixedInts;
}
