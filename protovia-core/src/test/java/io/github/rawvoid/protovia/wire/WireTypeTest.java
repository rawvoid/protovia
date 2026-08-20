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

package io.github.rawvoid.protovia.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Rawvoid
 */
class WireTypeTest {

    @Test
    void validFieldNumbers() {
        assertTrue(WireType.isValidFieldNumber(1));
        assertTrue(WireType.isValidFieldNumber(18999));
        assertTrue(WireType.isValidFieldNumber(20000));
        assertTrue(WireType.isValidFieldNumber(WireType.MAX_FIELD_NUMBER));
        assertFalse(WireType.isValidFieldNumber(0));
        assertFalse(WireType.isValidFieldNumber(19000));
        assertFalse(WireType.isValidFieldNumber(19999));
        assertFalse(WireType.isValidFieldNumber(WireType.MAX_FIELD_NUMBER + 1));
    }

    @Test
    void validFieldNumberRange() {
        assertTrue(WireType.isValidFieldNumberRange(10, 12));
        assertTrue(WireType.isValidFieldNumberRange(1, 18999));
        assertTrue(WireType.isValidFieldNumberRange(20000, 20010));
        assertFalse(WireType.isValidFieldNumberRange(12, 10));
        assertFalse(WireType.isValidFieldNumberRange(0, 5));
        assertFalse(WireType.isValidFieldNumberRange(19000, 19000));
        assertFalse(WireType.isValidFieldNumberRange(18999, 20000));
    }
}
