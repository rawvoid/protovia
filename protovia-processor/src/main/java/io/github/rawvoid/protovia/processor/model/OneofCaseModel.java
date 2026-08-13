package io.github.rawvoid.protovia.processor.model;

import javax.lang.model.element.TypeElement;

/**
 * One permitted subtype of a {@code @ProtoOneof} sealed type.
 */
public final class OneofCaseModel {

    public final int number;
    public final TypeElement type;
    public final String typeName;
    public final String tagConstant;
    /** {@code null} when the case is an empty record. */
    public final FieldModel payload;
    /** Record component accessor, e.g. {@code value()}, or {@code null} if the case is the message itself. */
    public final String accessor;
    public final boolean selfMessage;

    public OneofCaseModel(
            int number,
            TypeElement type,
            String typeName,
            String tagConstant,
            FieldModel payload,
            String accessor,
            boolean selfMessage) {
        this.number = number;
        this.type = type;
        this.typeName = typeName;
        this.tagConstant = tagConstant;
        this.payload = payload;
        this.accessor = accessor;
        this.selfMessage = selfMessage;
    }

    public boolean empty() {
        return !selfMessage && payload == null;
    }
}
