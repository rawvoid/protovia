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

package io.github.rawvoid.protovia.processor.parse;

import io.github.rawvoid.protovia.processor.model.FieldModel;

/**
 * Result of trying adapters before {@link TypeClassifier#classify}.
 *
 * @param field adapted model when applied
 * @param done  {@code true} if classify must not run (applied or rejected)
 * @author Rawvoid
 */
record AdapterApplication(FieldModel field, boolean done) {

    static AdapterApplication applied(FieldModel field) {
        return new AdapterApplication(field, true);
    }

    static AdapterApplication rejected() {
        return new AdapterApplication(null, true);
    }

    static AdapterApplication skip() {
        return new AdapterApplication(null, false);
    }
}
