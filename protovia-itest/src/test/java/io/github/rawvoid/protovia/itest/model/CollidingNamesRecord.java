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
public record CollidingNamesRecord(
    @ProtoField(number = 1) int size,
    @ProtoField(number = 2) String value,
    @ProtoField(number = 3) int tag,
    @ProtoField(number = 4) String msg,
    @ProtoField(number = 5) String cache,
    @ProtoField(number = 6) String _n,
    @ProtoField(number = 7) List<Integer> foo,
    @ProtoField(number = 8) List<Integer> Foo,
    @ProtoField(number = 9) Map<String, Integer> existing,
    @ProtoUnknown UnknownFields builder,
    int reader
) {
}
