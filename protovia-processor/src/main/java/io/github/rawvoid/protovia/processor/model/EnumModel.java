package io.github.rawvoid.protovia.processor.model;

import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Parsed {@code @ProtoEnum}.
 *
 * @author Rawvoid
 */
public final class EnumModel {

    public final TypeElement type;
    public final String typeName;
    public final List<Constant> constants;
    /**
     * Java-only sentinel name, or {@code null}.
     */
    public final String unrecognized;

    public EnumModel(TypeElement type, String typeName, List<Constant> constants, String unrecognized) {
        this.type = type;
        this.typeName = typeName;
        this.constants = List.copyOf(constants);
        this.unrecognized = unrecognized;
    }

    public record Constant(String name, int number) {
    }
}
