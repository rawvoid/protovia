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

import java.time.Instant;
import java.util.Optional;

/**
 * @author Rawvoid
 */
@ProtoMessage
public class OptionalMessageEnumPojo {

    @ProtoField(number = 1)
    public Optional<Address> addr = Optional.empty();

    @ProtoField(number = 2)
    public Optional<Status> status = Optional.empty();

    @ProtoField(number = 3)
    public Optional<Kind> kind = Optional.empty();

    @ProtoField(number = 4)
    public Optional<Instant> at = Optional.empty();

    @ProtoUnknown
    public UnknownFields unknown;
}
