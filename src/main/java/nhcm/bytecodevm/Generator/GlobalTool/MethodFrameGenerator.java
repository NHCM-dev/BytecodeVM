package nhcm.bytecodevm.Generator.GlobalTool;

import lombok.Getter;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.FieldUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
import org.objectweb.asm.tree.*;

import java.util.List;

public class MethodFrameGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;

    public MethodFrameGenerator(String className) {
        super(className);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC}, className);
        List<FieldNode> fields = cn.fields;
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "locals", "[Ljava/lang/Object;"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "stack", "[Ljava/lang/Object;"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "stackWords", "[J"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "stackTypes", "[I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "stackWidths", "[I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "programCounter", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "stackPointer", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returnValue", "Ljava/lang/Object;"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returned", "Z"));
        cn.methods.add(this.genInitMethod(className));
        cn.methods.add(this.genPushMethod(className));
        cn.methods.add(this.genPushWithWidthMethod(className));
        cn.methods.add(this.genPushIntMethod(className));
        cn.methods.add(this.genPushLongMethod(className));
        cn.methods.add(this.genPushFloatMethod(className));
        cn.methods.add(this.genPushDoubleMethod(className));
        cn.methods.add(this.genPopMethod(className));
        cn.methods.add(this.genPopIntMethod(className));
        cn.methods.add(this.genPopLongMethod(className));
        cn.methods.add(this.genPopFloatMethod(className));
        cn.methods.add(this.genPopDoubleMethod(className));
        cn.methods.add(this.genPeekWidthMethod(className));
        cn.methods.add(this.genReplaceIdentityMethod(className));
        this.classNode = cn;
    }

    private MethodNode genInitMethod(String className) {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "<init>", "(II)V");
        InsnBuilder ib = new InsnBuilder();
        ib.aload(0);
        ib.invokeSpecial("java/lang/Object", "<init>", "()V");
        ib.aload(0);
        ib.iload(1);
        ib.aneArray("java/lang/Object");
        ib.putField(className, "locals", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.iload(2);
        ib.aneArray("java/lang/Object");
        ib.putField(className, "stack", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.iload(2);
        ib.newArray(org.objectweb.asm.Opcodes.T_LONG);
        ib.putField(className, "stackWords", "[J");
        ib.aload(0);
        ib.iload(2);
        ib.newArray(org.objectweb.asm.Opcodes.T_INT);
        ib.putField(className, "stackTypes", "[I");
        ib.aload(0);
        ib.iload(2);
        ib.newArray(org.objectweb.asm.Opcodes.T_INT);
        ib.putField(className, "stackWidths", "[I");
        ib._return();
        methodNode.instructions.add(ib.toInsnList());
        return methodNode;
    }

    private MethodNode genPushMethod(String className) {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "push", "(Ljava/lang/Object;)V");
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);
        ib.aload(0);
        ib.aload(1);
        ib.iconst1();
        ib.invokeVirtual(className, "push", "(Ljava/lang/Object;I)V");
        ib._return();
        return methodNode;
    }

    private MethodNode genPushWithWidthMethod(String className)
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                "push",
                "(Ljava/lang/Object;I)V");
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);

        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.aload(1);
        ib.aastore();
        ib.aload(0);
        ib.getField(className, "stackTypes", "[I");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.iconst0();
        ib.iastore();

        ib.aload(0);
        ib.getField(className, "stackWidths", "[I");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.iload(2);
        ib.iastore();

        ib.aload(0);
        ib.dup();
        ib.getField(className, "stackPointer", "I");
        ib.iconst1();
        ib.iadd();
        ib.putField(className, "stackPointer", "I");
        ib._return();
        return methodNode;
    }

    private MethodNode genPopMethod(String className) {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "pop", "()Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);
        int index = 1;
        int type = 2;
        int object = 3;
        LabelNode objectValue = new LabelNode();
        LabelNode longValue = new LabelNode();
        LabelNode floatValue = new LabelNode();
        LabelNode doubleValue = new LabelNode();

        emitRawPopIndexAndType(ib, className, index, type);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aaload();
        ib.astore(object);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aconstNull();
        ib.aastore();

        ib.iload(type);
        ib.iconst1();
        ib.ifIcmpNe(longValue);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(index);
        ib.laload();
        ib.l2i();
        ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
        ib.areturn();

        ib.label(longValue);
        ib.iload(type);
        ib.iconst2();
        ib.ifIcmpNe(floatValue);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(index);
        ib.laload();
        ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
        ib.areturn();

        ib.label(floatValue);
        ib.iload(type);
        ib.iconst3();
        ib.ifIcmpNe(doubleValue);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(index);
        ib.laload();
        ib.l2i();
        ib.invokeStatic("java/lang/Float", "intBitsToFloat", "(I)F");
        ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
        ib.areturn();

        ib.label(doubleValue);
        ib.iload(type);
        ib.iconst4();
        ib.ifIcmpNe(objectValue);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(index);
        ib.laload();
        ib.invokeStatic("java/lang/Double", "longBitsToDouble", "(J)D");
        ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
        ib.areturn();

        ib.label(objectValue);
        ib.aload(object);
        ib.areturn();
        return methodNode;
    }

    private MethodNode genPushIntMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "pushInt", "(I)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        emitPushWordPrefix(ib, className, 2);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(2);
        ib.iload(1);
        ib.i2l();
        ib.lastore();
        emitPushWordSuffix(ib, className, 2, 1, 1);
        return method;
    }

    private MethodNode genPushLongMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "pushLong", "(J)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        emitPushWordPrefix(ib, className, 3);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(3);
        ib.lload(1);
        ib.lastore();
        emitPushWordSuffix(ib, className, 3, 2, 2);
        return method;
    }

    private MethodNode genPushFloatMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "pushFloat", "(F)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        emitPushWordPrefix(ib, className, 2);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(2);
        ib.fload(1);
        ib.invokeStatic("java/lang/Float", "floatToRawIntBits", "(F)I");
        ib.i2l();
        ib.lastore();
        emitPushWordSuffix(ib, className, 2, 3, 1);
        return method;
    }

    private MethodNode genPushDoubleMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "pushDouble", "(D)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        emitPushWordPrefix(ib, className, 3);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(3);
        ib.dload(1);
        ib.invokeStatic("java/lang/Double", "doubleToRawLongBits", "(D)J");
        ib.lastore();
        emitPushWordSuffix(ib, className, 3, 4, 2);
        return method;
    }

    private MethodNode genPopIntMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "popInt", "()I");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode boxed = new LabelNode();
        emitPopWordPrefix(ib, className, 1, 2, boxed, 1);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(1);
        ib.laload();
        ib.l2i();
        ib.ireturn();
        ib.label(boxed);
        emitLoadAndClearBoxedStackValue(ib, className, 1, 3);
        ib.aload(3);
        nhcm.bytecodevm.Utils.TypeUtils.unboxIntLike(ib);
        ib.ireturn();
        return method;
    }

    private MethodNode genPopLongMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "popLong", "()J");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode boxed = new LabelNode();
        emitPopWordPrefix(ib, className, 1, 2, boxed, 2);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(1);
        ib.laload();
        ib.lreturn();
        ib.label(boxed);
        emitLoadAndClearBoxedStackValue(ib, className, 1, 3);
        ib.aload(3);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "longValue", "()J");
        ib.lreturn();
        return method;
    }

    private MethodNode genPopFloatMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "popFloat", "()F");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode boxed = new LabelNode();
        emitPopWordPrefix(ib, className, 1, 2, boxed, 3);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(1);
        ib.laload();
        ib.l2i();
        ib.invokeStatic("java/lang/Float", "intBitsToFloat", "(I)F");
        ib.freturn();
        ib.label(boxed);
        emitLoadAndClearBoxedStackValue(ib, className, 1, 3);
        ib.aload(3);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "floatValue", "()F");
        ib.freturn();
        return method;
    }

    private MethodNode genPopDoubleMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "popDouble", "()D");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode boxed = new LabelNode();
        emitPopWordPrefix(ib, className, 1, 2, boxed, 4);
        ib.aload(0);
        ib.getField(className, "stackWords", "[J");
        ib.iload(1);
        ib.laload();
        ib.invokeStatic("java/lang/Double", "longBitsToDouble", "(J)D");
        ib.dreturn();
        ib.label(boxed);
        emitLoadAndClearBoxedStackValue(ib, className, 1, 3);
        ib.aload(3);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "doubleValue", "()D");
        ib.dreturn();
        return method;
    }

    private MethodNode genPeekWidthMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "peekWidth", "()I");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.getField(className, "stackWidths", "[I");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.iconst1();
        ib.isub();
        ib.iaload();
        ib.ireturn();
        return method;
    }

    private static void emitPushWordPrefix(InsnBuilder ib, String className, int indexLocal)
    {
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.istore(indexLocal);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(indexLocal);
        ib.aconstNull();
        ib.aastore();
    }

    private static void emitPushWordSuffix(
            InsnBuilder ib,
            String className,
            int indexLocal,
            int typeTag,
            int width)
    {
        ib.aload(0);
        ib.getField(className, "stackTypes", "[I");
        ib.iload(indexLocal);
        ib.pushInt(typeTag);
        ib.iastore();
        ib.aload(0);
        ib.getField(className, "stackWidths", "[I");
        ib.iload(indexLocal);
        ib.pushInt(width);
        ib.iastore();
        ib.aload(0);
        ib.iload(indexLocal);
        ib.iconst1();
        ib.iadd();
        ib.putField(className, "stackPointer", "I");
        ib._return();
    }

    private static void emitRawPopIndexAndType(
            InsnBuilder ib,
            String className,
            int indexLocal,
            int typeLocal)
    {
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.iconst1();
        ib.isub();
        ib.istore(indexLocal);
        ib.aload(0);
        ib.iload(indexLocal);
        ib.putField(className, "stackPointer", "I");
        ib.aload(0);
        ib.getField(className, "stackTypes", "[I");
        ib.iload(indexLocal);
        ib.iaload();
        ib.istore(typeLocal);
        ib.aload(0);
        ib.getField(className, "stackTypes", "[I");
        ib.iload(indexLocal);
        ib.iconst0();
        ib.iastore();
        ib.aload(0);
        ib.getField(className, "stackWidths", "[I");
        ib.iload(indexLocal);
        ib.iconst0();
        ib.iastore();
    }

    private static void emitPopWordPrefix(
            InsnBuilder ib,
            String className,
            int indexLocal,
            int typeLocal,
            LabelNode boxed,
            int expectedType)
    {
        emitRawPopIndexAndType(ib, className, indexLocal, typeLocal);
        ib.iload(typeLocal);
        ib.pushInt(expectedType);
        ib.ifIcmpNe(boxed);
    }

    private static void emitLoadAndClearBoxedStackValue(
            InsnBuilder ib,
            String className,
            int indexLocal,
            int objectLocal)
    {
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(indexLocal);
        ib.aaload();
        ib.astore(objectLocal);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(indexLocal);
        ib.aconstNull();
        ib.aastore();
    }

    private MethodNode genReplaceIdentityMethod(String className)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                "replaceIdentity",
                "(Ljava/lang/Object;Ljava/lang/Object;)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int index = 3;

        ib.iconst0();
        ib.istore(index);
        LabelNode localsLoop = new LabelNode();
        LabelNode localsNext = new LabelNode();
        LabelNode stackStart = new LabelNode();
        ib.label(localsLoop);
        ib.iload(index);
        ib.aload(0);
        ib.getField(className, "locals", "[Ljava/lang/Object;");
        ib.arrayLength();
        ib.ifIcmpGe(stackStart);
        ib.aload(0);
        ib.getField(className, "locals", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aaload();
        ib.aload(1);
        ib.ifAcmpNe(localsNext);
        ib.aload(0);
        ib.getField(className, "locals", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aload(2);
        ib.aastore();
        ib.label(localsNext);
        ib.iinc(index, 1);
        ib.goto_(localsLoop);

        ib.label(stackStart);
        ib.iconst0();
        ib.istore(index);
        LabelNode stackLoop = new LabelNode();
        LabelNode stackNext = new LabelNode();
        LabelNode done = new LabelNode();
        ib.label(stackLoop);
        ib.iload(index);
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.ifIcmpGe(done);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aaload();
        ib.aload(1);
        ib.ifAcmpNe(stackNext);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.iload(index);
        ib.aload(2);
        ib.aastore();
        ib.label(stackNext);
        ib.iinc(index, 1);
        ib.goto_(stackLoop);

        ib.label(done);
        ib._return();
        return method;
    }
}
