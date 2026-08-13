package io.github.rawvoid.protovia.processor.model;

import javax.lang.model.element.TypeElement;
import java.util.List;

public final class EnumModel {

    public final TypeElement type;
    public final String typeName;
    public final List<Constant> constants;

    public EnumModel(TypeElement type, String typeName, List<Constant> constants) {
        this.type = type;
        this.typeName = typeName;
        this.constants = List.copyOf(constants);
    }

    public record Constant(String name, int number) {
    }
}
