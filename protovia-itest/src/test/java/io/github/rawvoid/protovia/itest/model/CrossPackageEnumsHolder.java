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
import java.util.Map;
import java.util.Optional;

/**
 * @author Rawvoid
 */
@ProtoMessage
public class CrossPackageEnumsHolder {

    @ProtoField(number = 1)
    public io.github.rawvoid.protovia.itest.model.enuma.Status statusA;

    @ProtoField(number = 2)
    public io.github.rawvoid.protovia.itest.model.enumb.Status statusB;

    @ProtoField(number = 3)
    public Optional<io.github.rawvoid.protovia.itest.model.enumb.Status> optStatusB = Optional.empty();

    @ProtoField(number = 4)
    public List<io.github.rawvoid.protovia.itest.model.enuma.Status> listA;

    @ProtoField(number = 5)
    public Map<String, io.github.rawvoid.protovia.itest.model.enumb.Status> mapB;
}
