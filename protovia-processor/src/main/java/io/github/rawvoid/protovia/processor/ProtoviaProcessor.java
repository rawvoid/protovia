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

package io.github.rawvoid.protovia.processor;

import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.processor.gen.CodecGenerator;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.parse.SchemaParser;
import io.github.rawvoid.protovia.processor.proto.ProtoFileWriter;
import io.github.rawvoid.protovia.processor.proto.ProtoPrinter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Isolating annotation processor that writes {@code XxxProtoCodec} and a
 * {@code .proto} resource for each {@code @ProtoMessage} / {@code @ProtoEnum}.
 *
 * @author Rawvoid
 */
@SupportedAnnotationTypes({
    "io.github.rawvoid.protovia.annotation.ProtoMessage",
    "io.github.rawvoid.protovia.annotation.ProtoEnum"
})
@SupportedOptions(ProtoFileWriter.PROTO_OUT_OPTION)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ProtoviaProcessor extends AbstractProcessor {

    private final CodecGenerator generator = new CodecGenerator();
    private final Map<String, TypeElement> deferredMessages = new LinkedHashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (!deferredMessages.isEmpty()) {
                SchemaParser parser = new SchemaParser(
                    processingEnv.getTypeUtils(),
                    processingEnv.getElementUtils(),
                    processingEnv.getMessager());
                for (TypeElement type : deferredMessages.values()) {
                    parser.parseMessage(type, false);
                }
            }
            return false;
        }
        SchemaParser parser = new SchemaParser(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager());

        ProtoFileWriter protoFiles = new ProtoFileWriter(processingEnv);

        for (Element element : roundEnv.getElementsAnnotatedWith(ProtoEnum.class)) {
            if (element instanceof TypeElement type) {
                EnumModel model = parser.parseEnum(type);
                if (model != null) {
                    protoFiles.write(type, model.protoFullName(), ProtoPrinter.print(model));
                }
            }
        }

        Map<String, TypeElement> messages = new LinkedHashMap<>(deferredMessages);
        for (Element element : roundEnv.getElementsAnnotatedWith(ProtoMessage.class)) {
            if (element instanceof TypeElement type) {
                messages.put(type.getQualifiedName().toString(), type);
            }
        }
        for (TypeElement type : messages.values()) {
            handleMessage(parser, protoFiles, type);
        }
        return false;
    }

    private void handleMessage(SchemaParser parser, ProtoFileWriter protoFiles, TypeElement type) {
        String fqcn = type.getQualifiedName().toString();
        boolean retry = deferredMessages.containsKey(fqcn);
        MessageModel model = parser.parseMessage(type, !retry);
        if (parser.wasDeferred()) {
            deferredMessages.put(fqcn, type);
            return;
        }
        deferredMessages.remove(fqcn);
        if (model == null) {
            return;
        }
        writeCodec(type, model);
        protoFiles.write(type, model.protoFullName(), ProtoPrinter.print(model));
    }

    private void writeCodec(TypeElement type, MessageModel model) {
        String fqcn = Names.codecFqcn(processingEnv.getElementUtils(), type);
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(fqcn, type);
            try (Writer writer = file.openWriter()) {
                writer.write(generator.generate(model));
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "failed to write " + fqcn + ": " + e.getMessage(),
                type);
        }
    }
}
