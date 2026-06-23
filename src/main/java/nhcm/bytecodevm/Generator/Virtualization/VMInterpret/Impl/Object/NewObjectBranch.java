package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Object;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class NewObjectBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_OBJECT.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        // An Object[1] is an identity marker for the JVM's uninitialized value.
        // Its owner string is useful for diagnostics and keeps the marker runtime-only.
        ib.iconst1();
        ib.aneArray("java/lang/Object");
        ib.dup();
        ib.iconst0();
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        ib.aaload();
        ib.aastore();
        pushObject(ib, context);
        return ib.toInsnList();
    }
}
