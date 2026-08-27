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

package io.github.rawvoid.protovia.processor.proto;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a {@code .proto} to {@code CLASS_OUTPUT} and optionally mirrors it to
 * {@code -Aprotovia.protoOut}.
 *
 * @author Rawvoid
 */
public final class ProtoFileWriter {

    public static final String PROTO_OUT_OPTION = "protovia.protoOut";

    private final ProcessingEnvironment env;

    public ProtoFileWriter(ProcessingEnvironment env) {
        this.env = env;
    }

    public void write(TypeElement origin, String protoFullName, String text) {
        String relative = ProtoNames.filePath(protoFullName);
        writeClassOutput(origin, relative, text);
        String protoOut = env.getOptions().get(PROTO_OUT_OPTION);
        if (protoOut != null && !protoOut.isBlank()) {
            mirror(origin, protoOut.trim(), relative, text);
        }
    }

    private void writeClassOutput(TypeElement origin, String relative, String text) {
        int slash = relative.lastIndexOf('/');
        String pkg = slash < 0 ? "" : relative.substring(0, slash).replace('/', '.');
        String fileName = slash < 0 ? relative : relative.substring(slash + 1);
        try {
            FileObject file = env.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT, pkg, fileName, origin);
            try (Writer writer = file.openWriter()) {
                writer.write(text);
            }
        } catch (IOException e) {
            env.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "failed to write " + relative + ": " + e.getMessage(),
                origin);
        }
    }

    private void mirror(TypeElement origin, String protoOut, String relative, String text) {
        Path root = Path.of(protoOut).toAbsolutePath().normalize();
        Path dest = root.resolve(relative).normalize();
        if (!dest.startsWith(root)) {
            env.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "proto path escapes " + PROTO_OUT_OPTION + ": " + relative,
                origin);
            return;
        }
        try {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(dest, text);
        } catch (IOException e) {
            env.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "failed to write " + dest + ": " + e.getMessage(),
                origin);
        }
    }
}
