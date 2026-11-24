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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Rawvoid
 */
class NamesTest {

    @Test
    void safeLocalAlwaysPrefixesWithFUnderscore() {
        assertEquals("f_name", Names.safeLocal("name"));
        assertEquals("f_size", Names.safeLocal("size"));
        assertEquals("f_value", Names.safeLocal("value"));
        assertEquals("f_msg", Names.safeLocal("msg"));
        assertEquals("f_cache", Names.safeLocal("cache"));
        assertEquals("f_writer", Names.safeLocal("writer"));
        assertEquals("f_reader", Names.safeLocal("reader"));
        assertEquals("f_existing", Names.safeLocal("existing"));
        assertEquals("f_tag", Names.safeLocal("tag"));
        assertEquals("f_builder", Names.safeLocal("builder"));
        assertEquals("f__n", Names.safeLocal("_n"));
        assertEquals("f__sz_1", Names.safeLocal("_sz_1"));
        assertEquals("f_f_size", Names.safeLocal("f_size"));
        assertEquals("f_class", Names.safeLocal("class"));
    }

    @Test
    void fieldHelperNamesAreKeyedByFieldNumber() {
        FieldModel lower = FieldModel.builder().number(1).name("foo").build();
        FieldModel upper = FieldModel.builder().number(2).name("Foo").build();

        assertEquals("packedSizeOf_1", Names.packedSizeOf(lower));
        assertEquals("packedSizeOf_2", Names.packedSizeOf(upper));
        assertEquals("sizeOfEntry_1", Names.mapEntrySizeOf(lower));
        assertEquals("sizeOfEntry_2", Names.mapEntrySizeOf(upper));
        assertEquals("writeEntry_1", Names.mapEntryWrite(lower));
        assertEquals("writeEntry_2", Names.mapEntryWrite(upper));
        assertEquals("readEntry_1", Names.mapEntryRead(lower));
        assertEquals("readEntry_2", Names.mapEntryRead(upper));
    }

    @Test
    void sanitizeFqnIsInjectiveAcrossPackagesAndUnderscores() {
        assertEquals("com_a_Status", Names.sanitizeFqn("com.a.Status"));
        assertEquals("com_b_Status", Names.sanitizeFqn("com.b.Status"));
        assertNotEquals(Names.sanitizeFqn("a_b.E"), Names.sanitizeFqn("a.b_E"));
        assertNotEquals(Names.sanitizeFqn("a.b_c.E"), Names.sanitizeFqn("a.b.c_E"));
        assertNotEquals(Names.sanitizeFqn("a_.E"), Names.sanitizeFqn("a._E"));
        assertNotEquals(Names.sanitizeFqn("a.u.E"), Names.sanitizeFqn("a_.E"));
        assertNotEquals(Names.sanitizeFqn("a_0.E"), Names.sanitizeFqn("a_.E"));
        assertNotEquals(Names.sanitizeFqn("a__b.E"), Names.sanitizeFqn("a_b.E"));

        EnumModel a = EnumModel.builder().typeName("com.a.Status").build();
        EnumModel b = EnumModel.builder().typeName("com.b.Status").build();
        assertEquals("numberOf_com_a_Status", Names.enumNumberOf(a));
        assertEquals("from_com_a_Status", Names.enumFrom(a));
        assertEquals("numberOf_com_b_Status", Names.enumNumberOf(b));
        assertEquals("from_com_b_Status", Names.enumFrom(b));
    }

    @Test
    void rewriteReceiverRewritesInheritedCast() {
        assertEquals("((demo.Base) msg).id",
            Names.rewriteReceiver("((demo.Base) value).id", "id", "msg"));
        assertEquals("((value.Base) msg).id",
            Names.rewriteReceiver("((value.Base) value).id", "id", "msg"));
        assertEquals("((value) msg).id",
            Names.rewriteReceiver("((value) value).id", "id", "msg"));
        assertEquals("((value.PageResult<value.Item>) msg).items",
            Names.rewriteReceiver("((value.PageResult<value.Item>) value).items", "items", "msg"));
    }

    @Test
    void rewriteReceiverRewritesSimpleAccess() {
        assertEquals("msg.getId()", Names.rewriteReceiver("value.getId()", "id", "msg"));
        assertEquals("msg.id", Names.rewriteReceiver("value.id", "id", "msg"));
        assertEquals("msg.id", Names.rewriteReceiver(null, "id", "msg"));
    }
}
