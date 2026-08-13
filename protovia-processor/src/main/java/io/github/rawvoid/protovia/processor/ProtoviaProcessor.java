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
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.SchemaParser;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Isolating annotation processor that writes {@code XxxProtoCodec} next to each
 * {@code @ProtoMessage} type.
 *
 * @author Rawvoid
 */
@SupportedAnnotationTypes({
    "io.github.rawvoid.protovia.annotation.ProtoMessage",
    "io.github.rawvoid.protovia.annotation.ProtoEnum"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ProtoviaProcessor extends AbstractProcessor {

    private final CodecGenerator generator = new CodecGenerator();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        SchemaParser parser = new SchemaParser(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager());

        for (Element element : roundEnv.getElementsAnnotatedWith(ProtoEnum.class)) {
            if (element instanceof TypeElement type) {
                parser.parseEnum(type);
            }
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(ProtoMessage.class)) {
            if (!(element instanceof TypeElement type)) {
                continue;
            }
            MessageModel model = parser.parseMessage(type);
            if (model == null) {
                continue;
            }
            writeCodec(type, model);
        }
        return false;
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
