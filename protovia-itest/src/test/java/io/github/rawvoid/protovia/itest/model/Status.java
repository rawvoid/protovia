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

import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;

/**
 * @author Rawvoid
 */
@ProtoEnum
public enum Status {
    @ProtoEnumValue(0) UNKNOWN,
    @ProtoEnumValue(1) ACTIVE,
    @ProtoEnumValue(2) BANNED,
    @ProtoUnrecognized UNRECOGNIZED
}
