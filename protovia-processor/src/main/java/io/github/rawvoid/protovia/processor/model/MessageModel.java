package io.github.rawvoid.protovia.processor.model;

import javax.lang.model.element.TypeElement;
import java.util.List;

public final class MessageModel {

    public final TypeElement type;
    public final String packageName;
    public final String typeName;
    public final String codecSimpleName;
    public final boolean record;
    public final List<FieldModel> fields;
    public final List<RecordComponentModel> recordComponents;

    public MessageModel(
            TypeElement type,
            String packageName,
            String typeName,
            String codecSimpleName,
            boolean record,
            List<FieldModel> fields,
            List<RecordComponentModel> recordComponents) {
        this.type = type;
        this.packageName = packageName;
        this.typeName = typeName;
        this.codecSimpleName = codecSimpleName;
        this.record = record;
        this.fields = List.copyOf(fields);
        this.recordComponents = List.copyOf(recordComponents);
    }

    public record RecordComponentModel(String name, String typeName, String defaultExpr, FieldModel field) {
    }
}
