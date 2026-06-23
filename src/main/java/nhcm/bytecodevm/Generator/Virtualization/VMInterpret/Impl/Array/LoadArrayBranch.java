package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class LoadArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_ARRAY.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        popInt(ib, context);
        ib.istore(InterpretContext.MIDDLE_VALUE);
        popObject(ib, context);
        ib.astore(InterpretContext.RIGHT_VALUE);

        if (opcode == Opcs.BALOAD)
        {
            generateByteOrBooleanLoad(ib, context);
        }
        else
        {
            ib.aload(InterpretContext.RIGHT_VALUE);
            switch (opcode)
            {
                case IALOAD ->
                {
                    ib.checkCast("[I");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.iaload();
                    pushInt(ib, context);
                }
                case LALOAD ->
                {
                    ib.checkCast("[J");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.laload();
                    pushLong(ib, context);
                }
                case FALOAD ->
                {
                    ib.checkCast("[F");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.faload();
                    pushFloat(ib, context);
                }
                case DALOAD ->
                {
                    ib.checkCast("[D");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.daload();
                    pushDouble(ib, context);
                }
                case AALOAD ->
                {
                    ib.checkCast("[Ljava/lang/Object;");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.aaload();
                    pushObject(ib, context);
                }
                case CALOAD ->
                {
                    ib.checkCast("[C");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.caload();
                    pushInt(ib, context);
                }
                case SALOAD ->
                {
                    ib.checkCast("[S");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.saload();
                    pushInt(ib, context);
                }
                default -> throw new IllegalArgumentException("Unsupported array load opcode: " + opcode);
            }
        }

        return ib.toInsnList();
    }

    private static void generateByteOrBooleanLoad(
            InsnBuilder ib,
            InterpretContext context)
    {
        LabelNode byteArray = new LabelNode();
        LabelNode done = new LabelNode();

        ib.aload(InterpretContext.RIGHT_VALUE);
        ib.instanceOf("[Z");
        ib.ifeq(byteArray);

        ib.aload(InterpretContext.RIGHT_VALUE);
        ib.checkCast("[Z");
        ib.iload(InterpretContext.MIDDLE_VALUE);
        ib.baload();
        pushInt(ib, context);
        ib.goto_(done);

        ib.label(byteArray);
        ib.aload(InterpretContext.RIGHT_VALUE);
        ib.checkCast("[B");
        ib.iload(InterpretContext.MIDDLE_VALUE);
        ib.baload();
        pushInt(ib, context);

        ib.label(done);
    }
}
