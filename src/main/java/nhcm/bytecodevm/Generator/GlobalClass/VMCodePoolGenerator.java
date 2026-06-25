package nhcm.bytecodevm.Generator.GlobalClass;

import lombok.Getter;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMCodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    public final VMProgramGenerator vmProgramGenerator;

    public VMCodePoolGenerator(String className, VMProgramGenerator vmProgramGenerator)
    {
        super(className);
        this.vmProgramGenerator = vmProgramGenerator;

        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.INTERFACE, Acc.ABSTRACT}, className);
        MethodNode find = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.ABSTRACT}, "find", "(I)" + vmProgramGenerator.descriptor());
        cn.methods.add(find);
        this.classNode = cn;
    }
}
