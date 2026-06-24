package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.TypeUtils;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class IncrementBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INCREMENT.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        if (opcode != Opcs.IINC)
        {
            throw new IllegalArgumentException("Unsupported increment opcode: " + opcode);
        }

        InsnBuilder ib = new InsnBuilder();

        // int localIndex = nextToken(code, frame);
        context.nextToken(ib);
        ib.istore(InterpretContext.RIGHT_VALUE);

        // int increment = nextToken(code, frame);
        context.nextToken(ib);
        ib.istore(InterpretContext.LEFT_VALUE);

        // frame.locals[localIndex] = intValue(frame.locals[localIndex]) + increment;
        context.loadFrame(ib);
        context.frame.locals.get(ib);
        ib.iload(InterpretContext.RIGHT_VALUE);
        ib.dup2();
        ib.aaload();
        TypeUtils.unboxIntLike(ib);
        ib.iload(InterpretContext.LEFT_VALUE);
        ib.iadd();
        ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
        ib.aastore();

        return ib.toInsnList();
    }
}
