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
import io.github.rawvoid.protovia.collect.DoubleArrayList;
import io.github.rawvoid.protovia.collect.IntArrayList;

import java.util.List;

/**
 * @author Rawvoid
 */
@ProtoMessage
public class PackedOptModel {

    @ProtoField(number = 1)
    public List<Double> doubles;

    @ProtoField(number = 2, type = ProtoType.FIXED64)
    public List<Long> fixed64s;

    @ProtoField(number = 3, type = ProtoType.SFIXED64)
    public List<Long> sfixed64s;

    @ProtoField(number = 4)
    public List<Float> floats;

    @ProtoField(number = 5, type = ProtoType.FIXED32)
    public List<Integer> fixed32s;

    @ProtoField(number = 6, type = ProtoType.SFIXED32)
    public List<Integer> sfixed32s;

    @ProtoField(number = 7)
    public List<Long> varintLongs;

    @ProtoField(number = 8)
    public List<Boolean> bools;

    @ProtoField(number = 9)
    public double[] doubleArr;

    @ProtoField(number = 10)
    public float[] floatArr;

    @ProtoField(number = 11, type = ProtoType.FIXED64)
    public long[] fixed64Arr;

    @ProtoField(number = 12, type = ProtoType.FIXED32)
    public int[] fixed32Arr;

    @ProtoField(number = 13)
    public IntArrayList concreteInts;

    @ProtoField(number = 14)
    public DoubleArrayList concreteDoubles;
}
