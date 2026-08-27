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

import java.util.List;
import java.util.Objects;

/**
 * Handwritten immutable message: public all-args constructor, getters only.
 *
 * @author Rawvoid
 */
@ProtoMessage
public final class ImmutableUser {

    @ProtoField(number = 1)
    private final String name;

    @ProtoField(number = 2)
    private final int age;

    @ProtoField(number = 3)
    private final Address address;

    @ProtoField(number = 4)
    private final List<String> tags;

    public ImmutableUser(String name, int age, Address address, List<String> tags) {
        this.name = name;
        this.age = age;
        this.address = address;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public Address getAddress() {
        return address;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableUser other)) {
            return false;
        }
        return age == other.age
            && Objects.equals(name, other.name)
            && Objects.equals(address, other.address)
            && Objects.equals(tags, other.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, address, tags);
    }
}
