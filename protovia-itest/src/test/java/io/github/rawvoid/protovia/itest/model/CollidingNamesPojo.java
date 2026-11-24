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

import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

import java.util.List;
import java.util.Map;

/**
 * @author Rawvoid
 */
@ProtoMessage
public class CollidingNamesPojo {

    @ProtoField(number = 1)
    public int size;

    @ProtoField(number = 2)
    public String value;

    @ProtoField(number = 3)
    public int tag;

    @ProtoField(number = 4)
    private List<String> msg;

    @ProtoField(number = 5)
    public String cache;

    @ProtoField(number = 6)
    public String writer;

    @ProtoField(number = 7)
    public String reader;

    @ProtoField(number = 8)
    private Map<String, Integer> existing;

    @ProtoField(number = 9)
    public Status _n;

    @ProtoField(number = 10)
    public List<Integer> foo;

    @ProtoField(number = 11)
    public List<Integer> Foo;

    @ProtoField(number = 12)
    public Address x;

    @ProtoField(number = 13)
    public int xSize;

    @ProtoField(number = 14)
    public int xSlot;

    @ProtoField(number = 15)
    public int[] arr;

    @ProtoField(number = 16)
    public String arrBuilder;

    @ProtoUnknown
    public UnknownFields _old;

    public List<String> getMsg() {
        return msg;
    }

    public void setMsg(List<String> msg) {
        this.msg = msg;
    }

    public Map<String, Integer> getExisting() {
        return existing;
    }

    public void setExisting(Map<String, Integer> existing) {
        this.existing = existing;
    }
}
