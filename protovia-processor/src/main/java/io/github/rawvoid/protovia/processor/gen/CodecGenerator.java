package io.github.rawvoid.protovia.processor.gen;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.wire.WireType;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CodecGenerator {

    public String generate(MessageModel model) {
        JavaWriter w = new JavaWriter();
        if (!model.packageName.isEmpty()) {
            w.line("package " + model.packageName + ";");
            w.line("");
        }
        w.line("import io.github.rawvoid.protovia.ProtoException;");
        w.line("import io.github.rawvoid.protovia.codec.ProtoCodec;");
        w.line("import io.github.rawvoid.protovia.wire.CodedSize;");
        w.line("import io.github.rawvoid.protovia.wire.ProtoReader;");
        w.line("import io.github.rawvoid.protovia.wire.ProtoWriter;");
        w.line("import io.github.rawvoid.protovia.wire.SizeCache;");
        w.line("import io.github.rawvoid.protovia.wire.WireType;");
        w.line("");
        w.open("public final class " + model.codecSimpleName + " implements ProtoCodec<" + model.typeName + ">");
        w.line("public static final " + model.codecSimpleName + " INSTANCE = new " + model.codecSimpleName + "();");
        w.line("");
        w.open("private " + model.codecSimpleName + "()");
        w.close();
        w.line("");
        emitTags(w, model);
        w.line("@Override");
        w.open("public Class<" + model.typeName + "> type()");
        w.line("return " + model.typeName + ".class;");
        w.close();
        w.line("");
        emitComputeSize(w, model);
        w.line("");
        emitWriteTo(w, model);
        w.line("");
        emitReadFrom(w, model);
        emitEnumHelpers(w, model);
        emitPackedSizeHelpers(w, model);
        emitMapHelpers(w, model);
        emitArrayCopyHelpers(w, model);
        w.close();
        return w.toString();
    }

    private void emitTags(JavaWriter w, MessageModel model) {
        for (FieldModel field : model.fields) {
            String tag = Names.tagConstant(field.name);
            int unpacked = unpackedWire(field);
            w.line("private static final int " + tag + " = " + WireType.tag(field.number, unpacked) + ";");
            if (field.kind == FieldKind.REPEATED && field.packable()) {
                w.line("private static final int " + tag + "_PACKED = "
                        + WireType.tag(field.number, WireType.LEN) + ";");
            }
        }
        if (!model.fields.isEmpty()) {
            w.line("");
        }
    }

    private void emitComputeSize(JavaWriter w, MessageModel model) {
        w.line("@Override");
        w.open("public int computeSize(" + model.typeName + " value)");
        w.line("return computeSize(value, SizeCache.NOOP);");
        w.close();
        w.line("");
        w.line("@Override");
        w.open("public int computeSize(" + model.typeName + " value, SizeCache cache)");
        w.line("int size = 0;");
        for (FieldModel field : model.fields) {
            emitComputeField(w, field);
        }
        w.line("return size;");
        w.close();
    }

    private void emitComputeField(JavaWriter w, FieldModel field) {
        w.line(field.javaTypeName + " " + field.localName + " = " + field.readExpr + ";");
        switch (field.kind) {
            case SCALAR -> emitComputeScalar(w, field, field.localName, field.number, field.optional, field.javaOptional);
            case ENUM -> emitComputeEnum(w, field, field.localName, field.number, field.optional);
            case MESSAGE -> {
                w.open("if (" + field.localName + " != null)");
                w.line("int " + field.localName + "Slot = cache.reserve();");
                w.line("int " + field.localName + "Size = " + field.codecName + ".INSTANCE.computeSize("
                        + field.localName + ", cache);");
                w.line("cache.set(" + field.localName + "Slot, " + field.localName + "Size);");
                w.line("size += CodedSize.message(" + field.number + ", " + field.localName + "Size);");
                w.close();
            }
            case REPEATED -> emitComputeRepeated(w, field);
            case MAP -> emitComputeMap(w, field);
        }
    }

    private void emitComputeScalar(
            JavaWriter w, FieldModel field, String var, int number, boolean optional, boolean javaOptional) {
        String valueExpr = javaOptional ? var + ".get()" : var;
        w.open("if (" + presentCondition(field, var, optional, javaOptional) + ")");
        w.line("size += " + sizeCall(field, number, valueExpr) + ";");
        w.close();
    }

    private void emitComputeEnum(JavaWriter w, FieldModel field, String var, int number, boolean optional) {
        String helper = enumNumberHelper(field.enumModel);
        if (optional) {
            w.open("if (" + var + " != null)");
            w.line("size += CodedSize.enumValue(" + number + ", " + helper + "(" + var + "));");
            w.close();
        } else {
            w.open("if (" + var + " != null)");
            w.line("int " + var + "Number = " + helper + "(" + var + ");");
            w.open("if (" + var + "Number != 0)");
            w.line("size += CodedSize.enumValue(" + number + ", " + var + "Number);");
            w.close();
            w.close();
        }
    }

    private void emitComputeRepeated(JavaWriter w, FieldModel field) {
        String empty = field.array ? field.localName + ".length == 0" : field.localName + ".isEmpty()";
        w.open("if (" + field.localName + " != null && !" + empty + ")");
        if (field.packed && field.packable()) {
            w.line("int " + field.localName + "Packed = " + packedSizeHelper(field) + "(" + field.localName + ");");
            w.line("cache.push(" + field.localName + "Packed);");
            w.line("size += CodedSize.lengthDelimited(" + field.number + ", " + field.localName + "Packed);");
        } else {
            w.open("for (" + field.element.javaTypeName + " item : " + field.localName + ")");
            emitNullElementCheck(w, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                w.line("size += CodedSize.enumValue(" + field.number + ", "
                        + enumNumberHelper(field.element.enumModel) + "(item));");
            } else if (field.element.kind == FieldKind.MESSAGE) {
                w.line("int itemSlot = cache.reserve();");
                w.line("int itemSize = " + field.element.codecName + ".INSTANCE.computeSize(item, cache);");
                w.line("cache.set(itemSlot, itemSize);");
                w.line("size += CodedSize.message(" + field.number + ", itemSize);");
            } else {
                w.line("size += " + sizeCall(field.element, field.number, "item") + ";");
            }
            w.close();
        }
        w.close();
    }

    private void emitComputeMap(JavaWriter w, FieldModel field) {
        w.open("if (" + field.localName + " != null && !" + field.localName + ".isEmpty())");
        w.open("for (java.util.Map.Entry<" + boxed(field.mapKey) + ", " + boxed(field.mapValue) + "> e : "
                + field.localName + ".entrySet())");
        w.line("size += CodedSize.lengthDelimited(" + field.number + ", "
                + mapEntrySizeHelper(field) + "(e.getKey(), e.getValue(), cache));");
        w.close();
        w.close();
    }

    private void emitMapEntrySizeAdd(JavaWriter w, FieldModel part, String var, int number, String sizeVar) {
        if (part.kind == FieldKind.MESSAGE) {
            w.line("int " + var + "Slot = cache.reserve();");
            w.line("int " + var + "Size = " + part.codecName + ".INSTANCE.computeSize(" + var + ", cache);");
            w.line("cache.set(" + var + "Slot, " + var + "Size);");
            w.line(sizeVar + " += CodedSize.message(" + number + ", " + var + "Size);");
            return;
        }
        if (part.kind == FieldKind.ENUM) {
            w.line("int " + var + "N = " + enumNumberHelper(part.enumModel) + "(" + var + ");");
            w.open("if (" + var + "N != 0)");
            w.line(sizeVar + " += CodedSize.enumValue(" + number + ", " + var + "N);");
            w.close();
            return;
        }
        w.open("if (" + mapDefaultSkip(part, var) + ")");
        w.line(sizeVar + " += " + sizeCall(part, number, var) + ";");
        w.close();
    }

    private void emitWriteTo(JavaWriter w, MessageModel model) {
        w.line("@Override");
        w.open("public void writeTo(ProtoWriter writer, " + model.typeName + " value)");
        for (FieldModel field : model.fields) {
            emitWriteField(w, field);
        }
        w.close();
    }

    private void emitWriteField(JavaWriter w, FieldModel field) {
        w.line(field.javaTypeName + " " + field.localName + " = " + field.readExpr + ";");
        switch (field.kind) {
            case SCALAR -> {
                String valueExpr = field.javaOptional ? field.localName + ".get()" : field.localName;
                w.open("if (" + presentCondition(field, field.localName, field.optional, field.javaOptional) + ")");
                w.line(writeCall("writer", field, field.number, valueExpr) + ";");
                w.close();
            }
            case ENUM -> {
                String helper = enumNumberHelper(field.enumModel);
                if (field.optional) {
                    w.open("if (" + field.localName + " != null)");
                    w.line("writer.writeEnum(" + field.number + ", " + helper + "(" + field.localName + "));");
                    w.close();
                } else {
                    w.open("if (" + field.localName + " != null)");
                    w.line("int " + field.localName + "Number = " + helper + "(" + field.localName + ");");
                    w.open("if (" + field.localName + "Number != 0)");
                    w.line("writer.writeEnum(" + field.number + ", " + field.localName + "Number);");
                    w.close();
                    w.close();
                }
            }
            case MESSAGE -> {
                w.open("if (" + field.localName + " != null)");
                w.line("writer.writeMessage(" + field.number + ", " + field.codecName + ".INSTANCE, "
                        + field.localName + ");");
                w.close();
            }
            case REPEATED -> emitWriteRepeated(w, field);
            case MAP -> emitWriteMap(w, field);
        }
    }

    private void emitWriteRepeated(JavaWriter w, FieldModel field) {
        String empty = field.array ? field.localName + ".length == 0" : field.localName + ".isEmpty()";
        w.open("if (" + field.localName + " != null && !" + empty + ")");
        if (field.packed && field.packable()) {
            w.line("int " + field.localName + "Packed = writer.hasCachedSize()");
            w.line("        ? writer.takeSize()");
            w.line("        : " + packedSizeHelper(field) + "(" + field.localName + ");");
            w.line("writer.writeTag(" + field.number + ", WireType.LEN);");
            w.line("writer.writeUInt32NoTag(" + field.localName + "Packed);");
            w.open("for (" + field.element.javaTypeName + " item : " + field.localName + ")");
            if (field.element.kind == FieldKind.ENUM) {
                w.line("writer.writeInt32NoTag(" + enumNumberHelper(field.element.enumModel) + "(item));");
            } else {
                w.line(writeNoTag("writer", field.element, "item") + ";");
            }
            w.close();
        } else {
            w.open("for (" + field.element.javaTypeName + " item : " + field.localName + ")");
            emitNullElementCheck(w, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                w.line("writer.writeEnum(" + field.number + ", " + enumNumberHelper(field.element.enumModel)
                        + "(item));");
            } else if (field.element.kind == FieldKind.MESSAGE) {
                w.line("writer.writeMessage(" + field.number + ", " + field.element.codecName + ".INSTANCE, item);");
            } else {
                w.line(writeCall("writer", field.element, field.number, "item") + ";");
            }
            w.close();
        }
        w.close();
    }

    private void emitWriteMap(JavaWriter w, FieldModel field) {
        w.open("if (" + field.localName + " != null && !" + field.localName + ".isEmpty())");
        w.open("for (java.util.Map.Entry<" + boxed(field.mapKey) + ", " + boxed(field.mapValue) + "> e : "
                + field.localName + ".entrySet())");
        w.line("write" + Names.capitalize(field.name) + "Entry(writer, e.getKey(), e.getValue());");
        w.close();
        w.close();
    }

    private void emitReadFrom(JavaWriter w, MessageModel model) {
        w.line("@Override");
        w.open("public " + model.typeName + " readFrom(ProtoReader reader)");
        if (model.record) {
            emitReadRecord(w, model);
        } else {
            emitReadPojo(w, model);
        }
        w.close();
    }

    private void emitReadPojo(JavaWriter w, MessageModel model) {
        w.line(model.typeName + " msg = new " + model.typeName + "();");
        for (FieldModel field : model.fields) {
            if (field.array) {
                w.line("java.util.ArrayList<" + boxed(field.element) + "> " + field.localName
                        + "Builder = null;");
            }
        }
        w.line("int tag;");
        w.open("while ((tag = reader.readTag()) != 0)");
        w.open("switch (tag)");
        for (FieldModel field : model.fields) {
            emitReadCases(w, field, false);
        }
        w.line("default -> reader.skipField();");
        w.close();
        w.close();
        for (FieldModel field : model.fields) {
            if (field.array) {
                w.open("if (" + field.localName + "Builder != null)");
                emitAssign(w, field, "msg", toArray(field, field.localName + "Builder"));
                w.close();
            }
        }
        w.line("return msg;");
    }

    private void emitReadRecord(JavaWriter w, MessageModel model) {
        for (MessageModel.RecordComponentModel component : model.recordComponents) {
            w.line(component.typeName() + " " + Names.safeLocal(component.name()) + " = " + component.defaultExpr() + ";");
        }
        for (FieldModel field : model.fields) {
            if (field.array) {
                w.line("java.util.ArrayList<" + boxed(field.element) + "> " + field.localName
                        + "Builder = null;");
            }
        }
        w.line("int tag;");
        w.open("while ((tag = reader.readTag()) != 0)");
        w.open("switch (tag)");
        for (FieldModel field : model.fields) {
            emitReadCases(w, field, true);
        }
        w.line("default -> reader.skipField();");
        w.close();
        w.close();
        for (FieldModel field : model.fields) {
            if (field.array) {
                w.open("if (" + field.localName + "Builder != null)");
                w.line(field.localName + " = " + toArray(field, field.localName + "Builder") + ";");
                w.close();
            }
        }
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < model.recordComponents.size(); i++) {
            if (i > 0) {
                args.append(", ");
            }
            args.append(Names.safeLocal(model.recordComponents.get(i).name()));
        }
        w.line("return new " + model.typeName + "(" + args + ");");
    }

    private void emitReadCases(JavaWriter w, FieldModel field, boolean record) {
        String tag = Names.tagConstant(field.name);
        switch (field.kind) {
            case SCALAR -> {
                w.open("case " + tag + " ->");
                String read = wrapOptional(field, readCall(field));
                emitStore(w, field, record, read);
                w.close();
            }
            case ENUM -> {
                w.open("case " + tag + " ->");
                String decoded = enumFromHelper(field.enumModel) + "(reader.readEnum())";
                if (record) {
                    w.line(field.enumModel.typeName + " _e = " + decoded + ";");
                    w.open("if (_e != null)");
                    w.line(storeTarget(field) + " = " + wrapOptional(field, "_e") + ";");
                    w.close();
                } else {
                    w.line(field.enumModel.typeName + " _e = " + decoded + ";");
                    w.open("if (_e != null)");
                    emitAssign(w, field, "msg", wrapOptional(field, "_e"));
                    w.close();
                }
                w.close();
            }
            case MESSAGE -> {
                w.open("case " + tag + " ->");
                emitStore(w, field, record, wrapOptional(field, "reader.readMessage(" + field.codecName + ".INSTANCE)"));
                w.close();
            }
            case REPEATED -> emitReadRepeated(w, field, record, tag);
            case MAP -> {
                w.open("case " + tag + " ->");
                emitEnsureMap(w, field, record);
                w.line("read" + Names.capitalize(field.name) + "Entry(reader, " + mapVar(field, record) + ");");
                w.close();
            }
        }
    }

    private void emitReadRepeated(JavaWriter w, FieldModel field, boolean record, String tag) {
        if (field.packable()) {
            w.open("case " + tag + ", " + tag + "_PACKED ->");
            emitEnsureRepeated(w, field, record);
            w.open("if (reader.wireType() == WireType.LEN)");
            w.line("int oldLimit = reader.beginPacked();");
            w.open("while (reader.remaining() > 0)");
            emitRepeatedAdd(w, field, record, true);
            w.close();
            w.line("reader.popLimit(oldLimit);");
            w.close();
            w.open("else");
            emitRepeatedAdd(w, field, record, false);
            w.close();
            w.close();
        } else {
            w.open("case " + tag + " ->");
            emitEnsureRepeated(w, field, record);
            emitRepeatedAdd(w, field, record, false);
            w.close();
        }
    }

    private void emitEnsureRepeated(JavaWriter w, FieldModel field, boolean record) {
        if (field.array) {
            w.open("if (" + field.localName + "Builder == null)");
            w.line(field.localName + "Builder = new java.util.ArrayList<>();");
            w.close();
            return;
        }
        if (record) {
            w.open("if (" + field.localName + " == null)");
            w.line(field.localName + " = new " + field.implTypeName + "();");
            w.close();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            w.open("if (msg." + field.fieldName + " == null)");
            w.line("msg." + field.fieldName + " = new " + field.implTypeName + "();");
            w.close();
        } else {
            w.line(field.javaTypeName + " " + field.localName + " = " + field.readExpr.replace("value.", "msg.") + ";");
            w.open("if (" + field.localName + " == null)");
            w.line(field.localName + " = new " + field.implTypeName + "();");
            emitAssign(w, field, "msg", field.localName);
            w.close();
        }
    }

    private void emitRepeatedAdd(JavaWriter w, FieldModel field, boolean record, boolean packed) {
        String addend;
        if (field.element.kind == FieldKind.ENUM) {
            w.line(field.element.enumModel.typeName + " _item = "
                    + enumFromHelper(field.element.enumModel) + "(reader.readEnum());");
            w.open("if (_item != null)");
            addend = "_item";
            emitRepeatedAddValue(w, field, record, addend);
            w.close();
            return;
        }
        if (field.element.kind == FieldKind.MESSAGE) {
            addend = "reader.readMessage(" + field.element.codecName + ".INSTANCE)";
        } else {
            addend = readCall(field.element);
        }
        emitRepeatedAddValue(w, field, record, addend);
    }

    private void emitRepeatedAddValue(JavaWriter w, FieldModel field, boolean record, String addend) {
        if (field.array) {
            w.line(field.localName + "Builder.add(" + addend + ");");
        } else if (record) {
            w.line(field.localName + ".add(" + addend + ");");
        } else if (field.accessKind == AccessKind.FIELD) {
            w.line("msg." + field.fieldName + ".add(" + addend + ");");
        } else {
            w.line(field.localName + ".add(" + addend + ");");
        }
    }

    private void emitEnsureMap(JavaWriter w, FieldModel field, boolean record) {
        if (record) {
            w.open("if (" + field.localName + " == null)");
            w.line(field.localName + " = new " + field.implTypeName + "();");
            w.close();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            w.open("if (msg." + field.fieldName + " == null)");
            w.line("msg." + field.fieldName + " = new " + field.implTypeName + "();");
            w.close();
        } else {
            w.line(field.javaTypeName + " " + field.localName + " = " + field.readExpr.replace("value.", "msg.") + ";");
            w.open("if (" + field.localName + " == null)");
            w.line(field.localName + " = new " + field.implTypeName + "();");
            emitAssign(w, field, "msg", field.localName);
            w.close();
        }
    }

    private String mapVar(FieldModel field, boolean record) {
        if (record) {
            return field.localName;
        }
        if (field.accessKind == AccessKind.FIELD) {
            return "msg." + field.fieldName;
        }
        return field.localName;
    }

    private void emitStore(JavaWriter w, FieldModel field, boolean record, String expr) {
        if (record) {
            w.line(storeTarget(field) + " = " + expr + ";");
        } else {
            emitAssign(w, field, "msg", expr);
        }
    }

    private String storeTarget(FieldModel field) {
        return field.localName;
    }

    private void emitAssign(JavaWriter w, FieldModel field, String target, String expr) {
        if (field.accessKind == AccessKind.FIELD) {
            w.line(target + "." + field.fieldName + " = " + expr + ";");
        } else {
            w.line(target + "." + field.setterName + "(" + expr + ");");
        }
    }

    private void emitEnumHelpers(JavaWriter w, MessageModel model) {
        Set<String> seen = new LinkedHashSet<>();
        for (FieldModel field : model.fields) {
            collectEnums(field, seen, w);
        }
    }

    private void collectEnums(FieldModel field, Set<String> seen, JavaWriter w) {
        if (field.kind == FieldKind.ENUM && field.enumModel != null) {
            emitEnumHelper(w, field.enumModel, seen);
        }
        if (field.element != null) {
            collectEnums(field.element, seen, w);
        }
        if (field.mapKey != null) {
            collectEnums(field.mapKey, seen, w);
        }
        if (field.mapValue != null) {
            collectEnums(field.mapValue, seen, w);
        }
    }

    private void emitEnumHelper(JavaWriter w, EnumModel model, Set<String> seen) {
        String key = model.type.getQualifiedName().toString();
        if (!seen.add(key)) {
            return;
        }
        w.line("");
        w.open("static int " + enumNumberHelper(model) + "(" + model.typeName + " value)");
        w.open("return switch (value)");
        for (EnumModel.Constant c : model.constants) {
            w.line("case " + c.name() + " -> " + c.number() + ";");
        }
        w.close();
        w.line(";");
        w.close();
        w.line("");
        w.open("static " + model.typeName + " " + enumFromHelper(model) + "(int number)");
        w.open("return switch (number)");
        for (EnumModel.Constant c : model.constants) {
            w.line("case " + c.number() + " -> " + model.typeName + "." + c.name() + ";");
        }
        w.line("default -> null;");
        w.close();
        w.line(";");
        w.close();
    }

    private void emitPackedSizeHelpers(JavaWriter w, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.kind != FieldKind.REPEATED || !field.packed || !field.packable()) {
                continue;
            }
            w.line("");
            w.open("private static int " + packedSizeHelper(field) + "(" + field.javaTypeName + " values)");
            w.line("int packed = 0;");
            w.open("for (" + field.element.javaTypeName + " item : values)");
            emitNullElementCheck(w, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                w.line("packed += CodedSize.enumValue(" + enumNumberHelper(field.element.enumModel) + "(item));");
            } else {
                w.line("packed += " + sizeNoTag(field.element, "item") + ";");
            }
            w.close();
            w.line("return packed;");
            w.close();
        }
    }

    private void emitMapHelpers(JavaWriter w, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.kind != FieldKind.MAP) {
                continue;
            }
            w.line("");
            w.open("private static int " + mapEntrySizeHelper(field) + "("
                    + boxed(field.mapKey) + " k, " + boxed(field.mapValue) + " v, SizeCache cache)");
            w.open("if (k == null || v == null)");
            w.line("throw new ProtoException(\"map entry for field " + field.name + " cannot contain null\");");
            w.close();
            w.line("int entrySlot = cache.reserve();");
            w.line("int entrySize = 0;");
            emitMapEntrySizeAdd(w, field.mapKey, "k", 1, "entrySize");
            emitMapEntrySizeAdd(w, field.mapValue, "v", 2, "entrySize");
            w.line("cache.set(entrySlot, entrySize);");
            w.line("return entrySize;");
            w.close();
            w.line("");
            w.open("private static void write" + Names.capitalize(field.name) + "Entry(ProtoWriter writer, "
                    + boxed(field.mapKey) + " k, " + boxed(field.mapValue) + " v)");
            w.open("if (k == null || v == null)");
            w.line("throw new ProtoException(\"map entry for field " + field.name + " cannot contain null\");");
            w.close();
            w.line("int entrySize = writer.hasCachedSize()");
            w.line("        ? writer.takeSize()");
            w.line("        : " + mapEntrySizeHelper(field) + "(k, v, SizeCache.NOOP);");
            w.line("writer.writeTag(" + field.number + ", WireType.LEN);");
            w.line("writer.writeUInt32NoTag(entrySize);");
            emitMapEntryWrite(w, field.mapKey, "k", 1);
            emitMapEntryWrite(w, field.mapValue, "v", 2);
            w.close();
            w.line("");
            w.open("private static void read" + Names.capitalize(field.name) + "Entry(ProtoReader reader, "
                    + field.javaTypeName + " target)");
            w.line(boxed(field.mapKey) + " k = " + mapMissingDefault(field.mapKey) + ";");
            w.line(boxed(field.mapValue) + " v = " + mapMissingDefault(field.mapValue) + ";");
            w.line("int oldLimit = reader.beginPacked();");
            w.line("int tag;");
            w.open("while ((tag = reader.readTag()) != 0)");
            w.open("switch (tag)");
            w.open("case " + WireType.tag(1, unpackedWire(field.mapKey)) + " ->");
            w.line("k = " + mapReadExpr(field.mapKey) + ";");
            w.close();
            w.open("case " + WireType.tag(2, unpackedWire(field.mapValue)) + " ->");
            w.line("v = " + mapReadExpr(field.mapValue) + ";");
            w.close();
            w.line("default -> reader.skipField();");
            w.close();
            w.close();
            w.line("reader.popLimit(oldLimit);");
            w.line("target.put(k, v);");
            w.close();
        }
    }

    private void emitMapEntryWrite(JavaWriter w, FieldModel part, String var, int number) {
        if (part.kind == FieldKind.MESSAGE) {
            w.line("writer.writeMessage(" + number + ", " + part.codecName + ".INSTANCE, " + var + ");");
            return;
        }
        if (part.kind == FieldKind.ENUM) {
            w.line("int " + var + "N = " + enumNumberHelper(part.enumModel) + "(" + var + ");");
            w.open("if (" + var + "N != 0)");
            w.line("writer.writeEnum(" + number + ", " + var + "N);");
            w.close();
            return;
        }
        w.open("if (" + mapDefaultSkip(part, var) + ")");
        w.line(writeCall("writer", part, number, var) + ";");
        w.close();
    }

    private String mapReadExpr(FieldModel part) {
        if (part.kind == FieldKind.MESSAGE) {
            return "reader.readMessage(" + part.codecName + ".INSTANCE)";
        }
        if (part.kind == FieldKind.ENUM) {
            return enumFromHelper(part.enumModel) + "(reader.readEnum())";
        }
        return readCall(part);
    }

    private String mapMissingDefault(FieldModel part) {
        if (part.kind == FieldKind.MESSAGE) {
            return part.codecName + ".INSTANCE.readFrom(new ProtoReader(new byte[0]))";
        }
        if (part.kind == FieldKind.ENUM) {
            return enumFromHelper(part.enumModel) + "(0)";
        }
        if (part.byteArray) {
            return "new byte[0]";
        }
        return switch (part.protoType) {
            case BOOL -> "false";
            case STRING -> "\"\"";
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "0L";
            default -> "0";
        };
    }

    private String mapDefaultSkip(FieldModel part, String var) {
        if (part.byteArray) {
            return var + ".length != 0";
        }
        if (part.byteBuffer) {
            return var + ".remaining() != 0";
        }
        return switch (part.protoType) {
            case BOOL -> var;
            case STRING -> "!" + var + ".isEmpty()";
            case FLOAT -> "Float.floatToRawIntBits(" + var + ") != 0";
            case DOUBLE -> "Double.doubleToRawLongBits(" + var + ") != 0L";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != 0L";
            default -> var + " != 0";
        };
    }

    private void emitNullElementCheck(JavaWriter w, FieldModel element, String var, String fieldName) {
        if (!element.primitive) {
            w.open("if (" + var + " == null)");
            w.line("throw new ProtoException(\"null element in field " + fieldName + "\");");
            w.close();
        }
    }

    private String presentCondition(FieldModel field, String var, boolean optional, boolean javaOptional) {
        if (javaOptional) {
            return var + " != null && " + var + ".isPresent()";
        }
        if (optional) {
            return var + " != null";
        }
        if (field.primitive) {
            return switch (field.protoType) {
                case BOOL -> var;
                case FLOAT -> "Float.floatToRawIntBits(" + var + ") != 0";
                case DOUBLE -> "Double.doubleToRawLongBits(" + var + ") != 0L";
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != 0L";
                default -> var + " != 0";
            };
        }
        if (field.byteArray) {
            return var + " != null && " + var + ".length != 0";
        }
        if (field.byteBuffer) {
            return var + " != null && " + var + ".remaining() != 0";
        }
        return switch (field.protoType) {
            case STRING -> var + " != null && !" + var + ".isEmpty()";
            case BOOL -> var + " != null && " + var;
            case FLOAT -> var + " != null && Float.floatToRawIntBits(" + var + ") != 0";
            case DOUBLE -> var + " != null && Double.doubleToRawLongBits(" + var + ") != 0L";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != null && " + var + " != 0L";
            default -> var + " != null && " + var + " != 0";
        };
    }

    private String wrapOptional(FieldModel field, String expr) {
        if (field.javaOptional) {
            return "java.util.Optional.of(" + expr + ")";
        }
        return expr;
    }

    private void emitArrayCopyHelpers(JavaWriter w, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (!field.array) {
                continue;
            }
            String component = field.arrayComponentType;
            if (!"float".equals(component) && !"boolean".equals(component)) {
                continue;
            }
            w.line("");
            w.open("private static " + component + "[] copy" + Names.capitalize(field.name)
                    + "Array(java.util.List<" + boxed(field.element) + "> values)");
            w.line(component + "[] array = new " + component + "[values.size()];");
            w.open("for (int i = 0; i < array.length; i++)");
            w.line("array[i] = values.get(i);");
            w.close();
            w.line("return array;");
            w.close();
        }
    }

    private String toArray(FieldModel field, String listVar) {
        String component = field.arrayComponentType;
        if ("int".equals(component)) {
            return listVar + ".stream().mapToInt(i -> i).toArray()";
        }
        if ("long".equals(component)) {
            return listVar + ".stream().mapToLong(i -> i).toArray()";
        }
        if ("double".equals(component)) {
            return listVar + ".stream().mapToDouble(i -> i).toArray()";
        }
        if ("float".equals(component) || "boolean".equals(component)) {
            return "copy" + Names.capitalize(field.name) + "Array(" + listVar + ")";
        }
        return listVar + ".toArray(new " + component + "[0])";
    }

    private String boxed(FieldModel field) {
        if (field.kind == FieldKind.MESSAGE || field.kind == FieldKind.ENUM) {
            return field.kind == FieldKind.ENUM ? field.enumModel.typeName : field.javaTypeName;
        }
        if (!field.primitive) {
            return field.javaTypeName;
        }
        return switch (field.javaTypeName) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            default -> field.javaTypeName;
        };
    }

    private String packedSizeHelper(FieldModel field) {
        return "packedSizeOf" + Names.capitalize(field.name);
    }

    private String mapEntrySizeHelper(FieldModel field) {
        return "sizeOf" + Names.capitalize(field.name) + "Entry";
    }

    private String enumNumberHelper(EnumModel model) {
        return "numberOf" + sanitize(model.typeName);
    }

    private String enumFromHelper(EnumModel model) {
        return "from" + sanitize(model.typeName);
    }

    private static String sanitize(String typeName) {
        return typeName.replace(".", "_");
    }

    private int unpackedWire(FieldModel field) {
        if (field.kind == FieldKind.MAP || field.kind == FieldKind.MESSAGE) {
            return WireType.LEN;
        }
        FieldModel target = field.kind == FieldKind.REPEATED ? field.element : field;
        if (target == null || target.kind == FieldKind.MESSAGE || target.protoType == null
                || target.protoType == ProtoType.STRING || target.protoType == ProtoType.BYTES) {
            return WireType.LEN;
        }
        return switch (target.protoType) {
            case FIXED32, SFIXED32, FLOAT -> WireType.I32;
            case FIXED64, SFIXED64, DOUBLE -> WireType.I64;
            default -> WireType.VARINT;
        };
    }

    private String sizeCall(FieldModel field, int number, String value) {
        return switch (field.protoType) {
            case INT32 -> "CodedSize.int32(" + number + ", " + value + ")";
            case UINT32 -> "CodedSize.uint32(" + number + ", " + value + ")";
            case SINT32 -> "CodedSize.sint32(" + number + ", " + value + ")";
            case INT64 -> "CodedSize.int64(" + number + ", " + value + ")";
            case UINT64 -> "CodedSize.uint64(" + number + ", " + value + ")";
            case SINT64 -> "CodedSize.sint64(" + number + ", " + value + ")";
            case BOOL -> "CodedSize.bool(" + number + ", " + value + ")";
            case FIXED32, SFIXED32, FLOAT -> "CodedSize.fixed32(" + number + ")";
            case FIXED64, SFIXED64, DOUBLE -> "CodedSize.fixed64(" + number + ")";
            case STRING -> "CodedSize.string(" + number + ", " + value + ")";
            case BYTES -> "CodedSize.bytes(" + number + ", " + value + ")";
            case ENUM -> "CodedSize.enumValue(" + number + ", " + value + ")";
            default -> "CodedSize.int32(" + number + ", " + value + ")";
        };
    }

    private String sizeNoTag(FieldModel field, String value) {
        return switch (field.protoType) {
            case INT32, ENUM -> "CodedSize.int32(" + value + ")";
            case UINT32 -> "CodedSize.uint32(" + value + ")";
            case SINT32 -> "CodedSize.sint32(" + value + ")";
            case INT64 -> "CodedSize.int64(" + value + ")";
            case UINT64 -> "CodedSize.uint64(" + value + ")";
            case SINT64 -> "CodedSize.sint64(" + value + ")";
            case BOOL -> "CodedSize.bool(" + value + ")";
            case FIXED32, SFIXED32, FLOAT -> "4";
            case FIXED64, SFIXED64, DOUBLE -> "8";
            default -> "CodedSize.int32(" + value + ")";
        };
    }

    private String writeCall(String writer, FieldModel field, int number, String value) {
        return switch (field.protoType) {
            case INT32 -> writer + ".writeInt32(" + number + ", " + value + ")";
            case UINT32 -> writer + ".writeUInt32(" + number + ", " + value + ")";
            case SINT32 -> writer + ".writeSInt32(" + number + ", " + value + ")";
            case INT64 -> writer + ".writeInt64(" + number + ", " + value + ")";
            case UINT64 -> writer + ".writeUInt64(" + number + ", " + value + ")";
            case SINT64 -> writer + ".writeSInt64(" + number + ", " + value + ")";
            case BOOL -> writer + ".writeBool(" + number + ", " + value + ")";
            case FIXED32 -> writer + ".writeFixed32(" + number + ", " + value + ")";
            case SFIXED32 -> writer + ".writeSFixed32(" + number + ", " + value + ")";
            case FLOAT -> writer + ".writeFloat(" + number + ", " + value + ")";
            case FIXED64 -> writer + ".writeFixed64(" + number + ", " + value + ")";
            case SFIXED64 -> writer + ".writeSFixed64(" + number + ", " + value + ")";
            case DOUBLE -> writer + ".writeDouble(" + number + ", " + value + ")";
            case STRING -> writer + ".writeString(" + number + ", " + value + ")";
            case BYTES -> writer + ".writeBytes(" + number + ", " + value + ")";
            case ENUM -> writer + ".writeEnum(" + number + ", " + value + ")";
            default -> writer + ".writeInt32(" + number + ", " + value + ")";
        };
    }

    private String writeNoTag(String writer, FieldModel field, String value) {
        return switch (field.protoType) {
            case INT32, ENUM -> writer + ".writeInt32NoTag(" + value + ")";
            case UINT32 -> writer + ".writeUInt32NoTag(" + value + ")";
            case SINT32 -> writer + ".writeSInt32NoTag(" + value + ")";
            case INT64, UINT64 -> writer + ".writeUInt64NoTag(" + value + ")";
            case SINT64 -> writer + ".writeSInt64NoTag(" + value + ")";
            case BOOL -> writer + ".writeBoolNoTag(" + value + ")";
            case FIXED32, SFIXED32 -> writer + ".writeFixed32NoTag(" + value + ")";
            case FLOAT -> writer + ".writeFloatNoTag(" + value + ")";
            case FIXED64, SFIXED64 -> writer + ".writeFixed64NoTag(" + value + ")";
            case DOUBLE -> writer + ".writeDoubleNoTag(" + value + ")";
            default -> writer + ".writeInt32NoTag(" + value + ")";
        };
    }

    private String readCall(FieldModel field) {
        if (field.byteBuffer) {
            return "reader.readByteBuffer()";
        }
        return switch (field.protoType) {
            case INT32 -> "reader.readInt32()";
            case UINT32 -> "reader.readUInt32()";
            case SINT32 -> "reader.readSInt32()";
            case INT64 -> "reader.readInt64()";
            case UINT64 -> "reader.readUInt64()";
            case SINT64 -> "reader.readSInt64()";
            case BOOL -> "reader.readBool()";
            case FIXED32 -> "reader.readFixed32()";
            case SFIXED32 -> "reader.readSFixed32()";
            case FLOAT -> "reader.readFloat()";
            case FIXED64 -> "reader.readFixed64()";
            case SFIXED64 -> "reader.readSFixed64()";
            case DOUBLE -> "reader.readDouble()";
            case STRING -> "reader.readString()";
            case BYTES -> "reader.readBytes()";
            case ENUM -> "reader.readEnum()";
            default -> "reader.readInt32()";
        };
    }
}
