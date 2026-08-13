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

package io.github.rawvoid.protovia.bench.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bench entity matching {@code user.proto}.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class User {

    @ProtoField(number = 1)
    public String name;

    @ProtoField(number = 2)
    public int age;

    @ProtoField(number = 3)
    public List<String> tags = new ArrayList<>();

    @ProtoField(number = 4)
    public Address address;

    @ProtoField(number = 5)
    public Map<String, Integer> scores = new LinkedHashMap<>();

    @ProtoField(number = 6)
    public List<Integer> ranks = new ArrayList<>();

    @ProtoField(number = 7)
    public String bio;
}
