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

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.collect.IntArrayList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POJO with counting setters to verify lazy {@code _col_<n>} caching during decode.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class CountingSetterPojo {

    @ProtoField(number = 1)
    private List<Integer> ints = new ArrayList<>();

    @ProtoField(number = 2)
    private List<String> strings = new ArrayList<>();

    @ProtoField(number = 3)
    private Map<String, Integer> scores = new LinkedHashMap<>();

    @ProtoField(number = 4)
    private IntArrayList concreteInts = new IntArrayList();

    private int intsSetCount;
    private int stringsSetCount;
    private int scoresSetCount;
    private int concreteIntsSetCount;

    public List<Integer> getInts() {
        return ints;
    }

    public void setInts(List<Integer> ints) {
        this.intsSetCount++;
        this.ints = ints;
    }

    public List<String> getStrings() {
        return strings;
    }

    public void setStrings(List<String> strings) {
        this.stringsSetCount++;
        this.strings = strings;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scoresSetCount++;
        this.scores = scores;
    }

    public IntArrayList getConcreteInts() {
        return concreteInts;
    }

    public void setConcreteInts(IntArrayList concreteInts) {
        this.concreteIntsSetCount++;
        this.concreteInts = concreteInts;
    }

    public int intsSetCount() {
        return intsSetCount;
    }

    public int stringsSetCount() {
        return stringsSetCount;
    }

    public int scoresSetCount() {
        return scoresSetCount;
    }

    public int concreteIntsSetCount() {
        return concreteIntsSetCount;
    }
}
