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

/**
 * @author Rawvoid
 */
class NamesTest {

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
