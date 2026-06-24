package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class LoadConstantBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_CONSTANT.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        ib.aaload();
        ib.astore(InterpretContext.DUP_VALUE_1);
        ib.aload(InterpretContext.DUP_VALUE_1);
        context.loadFrame(ib);
        context.vm.resolveConstant.invokeStatic(ib);
        ib.astore(InterpretContext.DUP_VALUE_1);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        ib.aload(InterpretContext.DUP_VALUE_1);
        ib.instanceOf("java/lang/Long");
        ib.ifne(category2);
        ib.aload(InterpretContext.DUP_VALUE_1);
        ib.instanceOf("java/lang/Double");
        ib.ifne(category2);

        pushObject(ib, context, InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        ib.label(category2);
        ib.aload(InterpretContext.DUP_VALUE_1);
        pushObjectWithWidth(ib, context, 2);
        ib.label(done);
        return ib.toInsnList();
    }
}
