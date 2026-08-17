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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ProtoMessage
public class User {

    @ProtoField(number = 1)
    private String name;

    @ProtoField(number = 2)
    private int age;

    @ProtoField(number = 3, type = ProtoType.SINT64)
    private long score;

    @Builder.Default
    @ProtoField(number = 4)
    private List<String> tags = new ArrayList<>();

    @ProtoField(number = 5)
    private Address address;

    @Builder.Default
    @ProtoField(number = 6)
    private Map<String, Integer> scores = new LinkedHashMap<>();

    @ProtoField(number = 7)
    private Status status;

    @ProtoField(number = 8, optional = true)
    private Integer level;

    @Builder.Default
    @ProtoField(number = 9)
    private List<Integer> ranks = new ArrayList<>();

    @Builder.Default
    @ProtoField(number = 10, packed = false)
    private List<Integer> unpacked = new ArrayList<>();

    @ProtoField(number = 11)
    private byte[] payload;
}
