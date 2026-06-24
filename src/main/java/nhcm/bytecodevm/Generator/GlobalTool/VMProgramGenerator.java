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
    @Getter
    public final VMProgramLayout layout;

    public VMProgramGenerator(String className)
    {
        super(className);
        this.layout = new VMProgramLayout(className);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.codeField.name(), layout.codeField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.constantsField.name(), layout.constantsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.exceptionHandlersField.name(), layout.exceptionHandlersField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxLocalsField.name(), layout.maxLocalsField.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.FINAL}, layout.maxStackField.name(), layout.maxStackField.descriptor()));
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
        return layout.init.descriptor();
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
        layout.codeField.put(ib);
        ib.aload(0);
        ib.aload(2);
        layout.constantsField.put(ib);
        ib.aload(0);
        ib.aload(3);
        layout.exceptionHandlersField.put(ib);
        ib.aload(0);
        ib.iload(4);
        layout.maxLocalsField.put(ib);
        ib.aload(0);
        ib.iload(5);
        layout.maxStackField.put(ib);
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
