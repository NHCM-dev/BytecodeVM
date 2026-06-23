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
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, "stackWidths", "[I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "programCounter", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "stackPointer", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returnValue", "Ljava/lang/Object;"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returned", "Z"));
        cn.methods.add(this.genInitMethod(className));
        cn.methods.add(this.genPushMethod(className));
        cn.methods.add(this.genPushWithWidthMethod(className));
        cn.methods.add(this.genPopMethod(className));
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
        InsnBuilder ib = new InsnBuilder();
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.dup();
        ib.getField(className, "stackPointer", "I");
        ib.iconst1();
        ib.isub();
        ib.dupX1();
        ib.putField(className, "stackPointer", "I");
        ib.aaload();
        ib.astore(1);
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.aconstNull();
        ib.aastore();
        ib.aload(0);
        ib.getField(className, "stackWidths", "[I");
        ib.aload(0);
        ib.getField(className, "stackPointer", "I");
        ib.iconst0();
        ib.iastore();
        ib.aload(1);
        ib.areturn();
        methodNode.instructions.add(ib.toInsnList());
        return methodNode;
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
