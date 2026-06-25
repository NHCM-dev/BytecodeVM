package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class LoadConstantBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_CONSTANT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local constantIndex = context.intLocal("constantIndex", InterpretContext.RIGHT_VALUE);
        Local constant = context.objectLocal("constant", InterpretContext.DUP_VALUE_1);

        context.nextToken(ib, constantIndex);
        ib.set(constant, AdvInsnBuilder.arrayAt(context.constants(), constantIndex));
        ib.set(constant, AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.resolveConstant.name(),
                "java/lang/Object",
                constant,
                context.frame()));

        ib.ifElse(
                AdvInsnBuilder.or(
                        AdvInsnBuilder.isInstanceOf(constant, "java/lang/Long"),
                        AdvInsnBuilder.isInstanceOf(constant, "java/lang/Double")),
                category2 -> pushObjectWithWidth(category2, context, constant, AdvInsnBuilder.constant(2)),
                category1 -> pushObject(category1, context, constant));
    }
}
