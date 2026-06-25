package nhcm.bytecodevm.Generator.GlobalClass;

import lombok.Getter;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
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
        cn.methods.add(genConstructor());
        cn.methods.add(genObjectGetter("code", "[I"));
        cn.methods.add(genObjectGetter("constants", "[Ljava/lang/Object;"));
        cn.methods.add(genObjectGetter("exceptionHandlers", "[I"));
        cn.methods.add(genIntGetter("maxLocals"));
        cn.methods.add(genIntGetter("maxStack"));
        this.classNode = cn;
    }

    private MethodNode genConstructor()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.init.name(), // <init>
                layout.init.descriptor() // ([I[Ljava/lang/Object;[III)V
        );

        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Local code = ib.getLocal("code", "[I", 1);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 2);
        Local exceptionHandlers = ib.getLocal("exceptionHandlers", "[I", 3);
        Local maxLocals = ib.getLocal("maxLocals", "I", 4);
        Local maxStack = ib.getLocal("maxStack", "I", 5);

        ib.callNoArgSuperConstructor("java/lang/Object");
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.codeField), code);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.constantsField), constants);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.exceptionHandlersField), exceptionHandlers);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxLocalsField), maxLocals);
        ib.set(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.maxStackField), maxStack);
        ib.returnVoid();
        return method;
    }

    private MethodNode genObjectGetter(String fieldName, String descriptor)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                fieldName,
                "()" + descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.owner, fieldName, descriptor));
        return method;
    }

    private MethodNode genIntGetter(String fieldName)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                fieldName,
                "()I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.returnValue(AdvInsnBuilder.field(AdvInsnBuilder.self(layout.owner), layout.owner, fieldName, "I"));
        return method;
    }
}
