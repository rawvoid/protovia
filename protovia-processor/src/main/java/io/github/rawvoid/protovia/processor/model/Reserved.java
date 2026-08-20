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

package io.github.rawvoid.protovia.processor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Retired field numbers and proto names. Ranges are stored, never expanded.
 *
 * @author Rawvoid
 */
public final class Reserved {

    public static final Reserved EMPTY = new Reserved(
        Collections.emptySortedSet(), List.of(), Set.of());

    /**
     * Inclusive number range.
     *
     * @param from first reserved number
     * @param to   last reserved number
     */
    public record Range(int from, int to) {

        public boolean contains(int number) {
            return number >= from && number <= to;
        }
    }

    private final SortedSet<Integer> numbers;
    private final List<Range> ranges;
    private final Set<String> names;

    private Reserved(SortedSet<Integer> numbers, List<Range> ranges, Set<String> names) {
        this.numbers = numbers;
        this.ranges = ranges;
        this.names = names;
    }

    public boolean isEmpty() {
        return numbers.isEmpty() && ranges.isEmpty() && names.isEmpty();
    }

    public boolean containsNumber(int number) {
        if (numbers.contains(number)) {
            return true;
        }
        for (Range range : ranges) {
            if (range.contains(number)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsName(String name) {
        return names.contains(name);
    }

    public SortedSet<Integer> numbers() {
        return numbers;
    }

    public List<Range> ranges() {
        return ranges;
    }

    public Set<String> names() {
        return names;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final SortedSet<Integer> numbers = new TreeSet<>();
        private final List<Range> ranges = new ArrayList<>();
        private final Set<String> names = new LinkedHashSet<>();

        public Builder addNumber(int number) {
            numbers.add(number);
            return this;
        }

        public Builder addRange(int from, int to) {
            ranges.add(new Range(from, to));
            return this;
        }

        public Builder addName(String name) {
            names.add(name);
            return this;
        }

        public Reserved build() {
            if (numbers.isEmpty() && ranges.isEmpty() && names.isEmpty()) {
                return EMPTY;
            }
            return new Reserved(
                Collections.unmodifiableSortedSet(new TreeSet<>(numbers)),
                List.copyOf(ranges),
                Collections.unmodifiableSet(new LinkedHashSet<>(names)));
        }
    }
}
