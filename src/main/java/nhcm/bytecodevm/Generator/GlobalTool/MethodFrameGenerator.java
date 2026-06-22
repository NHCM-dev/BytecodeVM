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
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "programCounter", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "stackPointer", "I"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returnValue", "Ljava/lang/Object;"));
        fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PUBLIC}, "returned", "Z"));
        cn.methods.add(this.genInitMethod(className));
        cn.methods.add(this.genPushMethod(className));
        cn.methods.add(this.genPopMethod(className));
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
        ib._return();
        methodNode.instructions.add(ib.toInsnList());
        return methodNode;
    }

    private MethodNode genPushMethod(String className) {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC}, "push", "(Ljava/lang/Object;)V");
        InsnBuilder ib = new InsnBuilder();
        ib.aload(0);
        ib.getField(className, "stack", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.dup();
        ib.getField(className, "stackPointer", "I");
        ib.dupX1();
        ib.iconst1();
        ib.iadd();
        ib.putField(className, "stackPointer", "I");
        ib.aload(1);
        ib.aastore();
        ib._return();
        methodNode.instructions.add(ib.toInsnList());
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
        ib.aload(1);
        ib.areturn();
        methodNode.instructions.add(ib.toInsnList());
        return methodNode;
    }
}