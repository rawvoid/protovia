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

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Nested error scope for {@link SchemaParser}. {@link #push()} starts a parse;
 * {@link #popAndMerge()} ORs this frame's failures into the parent so a nested
 * {@code parseEnum} cannot see another message's errors, but still marks the
 * outer parse failed.
 *
 * @author Rawvoid
 */
final class Diagnostics {

    private final Messager messager;
    private final Deque<Boolean> frames = new ArrayDeque<>();
    private boolean current;

    Diagnostics(Messager messager) {
        this.messager = messager;
    }

    void push() {
        frames.push(current);
        current = false;
    }

    /**
     * @return {@code true} if this frame reported an error
     */
    boolean popAndMerge() {
        boolean failed = current;
        current = frames.pop() || failed;
        return failed;
    }

    boolean failed() {
        return current;
    }

    boolean hasErrors() {
        return current;
    }

    void error(Element element, String message) {
        current = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    void error(String message, Element... origins) {
        current = true;
        if (origins.length == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            return;
        }
        for (Element origin : origins) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, origin);
        }
    }
}
