package io.github.rawvoid.protovia.processor.model;

import javax.lang.model.element.TypeElement;
import java.util.List;

public final class MessageModel {

    public final TypeElement type;
    public final String packageName;
    public final String protoPackage;
    public final String protoMessageName;
    public final String typeName;
    public final String codecSimpleName;
    public final boolean record;
    public final List<FieldModel> fields;
    public final List<RecordComponentModel> recordComponents;
    public final UnknownField unknown;

    public MessageModel(
            TypeElement type,
            String packageName,
            String protoPackage,
            String protoMessageName,
            String typeName,
            String codecSimpleName,
            boolean record,
            List<FieldModel> fields,
            List<RecordComponentModel> recordComponents,
            UnknownField unknown) {
        this.type = type;
        this.packageName = packageName;
        this.protoPackage = protoPackage;
        this.protoMessageName = protoMessageName;
        this.typeName = typeName;
        this.codecSimpleName = codecSimpleName;
        this.record = record;
        this.fields = List.copyOf(fields);
        this.recordComponents = List.copyOf(recordComponents);
        this.unknown = unknown;
    }

    public record RecordComponentModel(String name, String typeName, String defaultExpr, FieldModel field) {
    }

    public record UnknownField(
            AccessKind accessKind,
            String name,
            String localName,
            String readExpr,
            String setterName,
            String fieldName) {
    }
}
