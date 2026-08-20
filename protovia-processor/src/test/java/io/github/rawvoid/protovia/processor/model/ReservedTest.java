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

package io.github.rawvoid.protovia.processor.model;

import io.github.rawvoid.protovia.wire.WireType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class ReservedTest {

    @Test
    void emptyContainsNothing() {
        assertTrue(Reserved.EMPTY.isEmpty());
        assertFalse(Reserved.EMPTY.containsNumber(1));
        assertFalse(Reserved.EMPTY.containsName("name"));
    }

    @Test
    void singlesAndNames() {
        Reserved reserved = Reserved.builder()
            .addNumber(4)
            .addNumber(4)
            .addNumber(5)
            .addName("legacy_tag")
            .addName("legacy_tag")
            .build();
        assertTrue(reserved.containsNumber(4));
        assertTrue(reserved.containsNumber(5));
        assertFalse(reserved.containsNumber(6));
        assertTrue(reserved.containsName("legacy_tag"));
        assertFalse(reserved.containsName("name"));
        assertEquals(2, reserved.numbers().size());
        assertEquals(1, reserved.names().size());
    }

    @Test
    void rangeContainsInclusiveWithoutExpanding() {
        Reserved reserved = Reserved.builder()
            .addRange(10, 12)
            .addRange(10, 12)
            .build();
        assertFalse(reserved.containsNumber(9));
        assertTrue(reserved.containsNumber(10));
        assertTrue(reserved.containsNumber(11));
        assertTrue(reserved.containsNumber(12));
        assertFalse(reserved.containsNumber(13));
        assertEquals(2, reserved.ranges().size());
    }

    @Test
    void overlappingRangeAndSingleAreIdempotent() {
        Reserved reserved = Reserved.builder()
            .addNumber(11)
            .addRange(10, 12)
            .build();
        assertTrue(reserved.containsNumber(10));
        assertTrue(reserved.containsNumber(11));
        assertTrue(reserved.containsNumber(12));
    }

    @Test
    void largeRangeDoesNotAllocatePerNumber() {
        Reserved reserved = Reserved.builder()
            .addRange(1, WireType.MAX_FIELD_NUMBER)
            .build();
        assertTrue(reserved.containsNumber(1));
        assertTrue(reserved.containsNumber(100_000));
        assertTrue(reserved.containsNumber(WireType.MAX_FIELD_NUMBER));
        assertFalse(reserved.containsNumber(0));
        assertEquals(1, reserved.ranges().size());
        assertTrue(reserved.numbers().isEmpty());
    }

    @Test
    void emptyBuilderIsEmptySingleton() {
        assertSame(Reserved.EMPTY, Reserved.builder().build());
    }

    @Test
    void addAllUnionsNumbersNamesAndRanges() {
        Reserved parent = Reserved.builder()
            .addNumber(4)
            .addRange(10, 12)
            .addName("legacy")
            .build();
        Reserved union = Reserved.builder()
            .addAll(parent)
            .addNumber(5)
            .build();
        assertTrue(union.containsNumber(4));
        assertTrue(union.containsNumber(5));
        assertTrue(union.containsNumber(11));
        assertTrue(union.containsName("legacy"));
        assertSame(Reserved.EMPTY, Reserved.builder().addAll(Reserved.EMPTY).build());
    }

    @Test
    void protoIdent() {
        assertTrue(ProtoIdent.isIdentifier("name"));
        assertTrue(ProtoIdent.isIdentifier("_x"));
        assertTrue(ProtoIdent.isIdentifier("legacy_tag"));
        assertTrue(ProtoIdent.isIdentifier("A1"));
        assertTrue(ProtoIdent.isIdentifier("string"));
        assertFalse(ProtoIdent.isIdentifier(""));
        assertFalse(ProtoIdent.isIdentifier(null));
        assertFalse(ProtoIdent.isIdentifier("1abc"));
        assertFalse(ProtoIdent.isIdentifier("legacy-tag"));
        assertFalse(ProtoIdent.isIdentifier("foo.bar"));
        assertFalse(ProtoIdent.isIdentifier("a b"));
        assertTrue(ProtoIdent.isKeyword("string"));
        assertTrue(ProtoIdent.isKeyword("message"));
        assertFalse(ProtoIdent.isKeyword("name"));
        assertFalse(ProtoIdent.isKeyword("String"));
        assertTrue(ProtoIdent.isExportName("display_name"));
        assertFalse(ProtoIdent.isExportName("string"));
        assertFalse(ProtoIdent.isExportName("1abc"));
        assertTrue(ProtoIdent.isPackageName("example"));
        assertTrue(ProtoIdent.isPackageName("example.v1"));
        assertFalse(ProtoIdent.isPackageName(""));
        assertFalse(ProtoIdent.isPackageName(".v1"));
        assertFalse(ProtoIdent.isPackageName("example."));
        assertFalse(ProtoIdent.isPackageName("example..v1"));
        assertFalse(ProtoIdent.isPackageName("1example.v1"));
    }
}
