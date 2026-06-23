package nhcm.bytecodevm.Generator.GlobalTool;

import lombok.Getter;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.FieldUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMProgramGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;

    public VMProgramGenerator(String className)
    {
        super(className);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, "code", "[I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, "constants", "[Ljava/lang/Object;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, "exceptionHandlers", "[I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, "maxLocals", "I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, "maxStack", "I"));
        cn.methods.add(generateConstructor());
        cn.methods.add(generateObjectGetter("code", "[I"));
        cn.methods.add(generateObjectGetter("constants", "[Ljava/lang/Object;"));
        cn.methods.add(generateObjectGetter("exceptionHandlers", "[I"));
        cn.methods.add(generateIntGetter("maxLocals"));
        cn.methods.add(generateIntGetter("maxStack"));
        this.classNode = cn;
    }

    public String constructorDescriptor()
    {
        return "([I[Ljava/lang/Object;[III)V";
    }

    private MethodNode generateConstructor()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                "<init>",
                constructorDescriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.invokeSpecial("java/lang/Object", "<init>", "()V");
        ib.aload(0);
        ib.aload(1);
        ib.putField(className(), "code", "[I");
        ib.aload(0);
        ib.aload(2);
        ib.putField(className(), "constants", "[Ljava/lang/Object;");
        ib.aload(0);
        ib.aload(3);
        ib.putField(className(), "exceptionHandlers", "[I");
        ib.aload(0);
        ib.iload(4);
        ib.putField(className(), "maxLocals", "I");
        ib.aload(0);
        ib.iload(5);
        ib.putField(className(), "maxStack", "I");
        ib._return();
        return method;
    }

    private MethodNode generateObjectGetter(String fieldName, String descriptor)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                fieldName,
                "()" + descriptor);
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.getField(className(), fieldName, descriptor);
        ib.areturn();
        return method;
    }

    private MethodNode generateIntGetter(String fieldName)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                fieldName,
                "()I");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.getField(className(), fieldName, "I");
        ib.ireturn();
        return method;
    }
}
