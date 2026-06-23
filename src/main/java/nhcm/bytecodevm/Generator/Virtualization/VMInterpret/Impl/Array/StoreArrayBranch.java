package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class StoreArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_ARRAY.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        switch (opcode)
        {
            case IASTORE, CASTORE, SASTORE ->
            {
                popInt(ib, context);
                ib.istore(InterpretContext.LEFT_VALUE);
            }
            case LASTORE ->
            {
                popLong(ib, context);
                ib.lstore(InterpretContext.LEFT_VALUE);
            }
            case FASTORE ->
            {
                popFloat(ib, context);
                ib.fstore(InterpretContext.LEFT_VALUE);
            }
            case DASTORE ->
            {
                popDouble(ib, context);
                ib.dstore(InterpretContext.LEFT_VALUE);
            }
            case AASTORE, BASTORE ->
            {
                popObject(ib, context);
                ib.astore(InterpretContext.LEFT_VALUE);
            }
        }
        popInt(ib, context);
        ib.istore(InterpretContext.MIDDLE_VALUE);
        popObject(ib, context);
        ib.astore(InterpretContext.RIGHT_VALUE);
        if (opcode == Opcs.BASTORE)
        {
            LabelNode byteArray = new LabelNode();
            LabelNode falseValue = new LabelNode();
            LabelNode storeBoolean = new LabelNode();
            LabelNode done = new LabelNode();

            ib.aload(InterpretContext.RIGHT_VALUE);
            ib.instanceOf("[Z");
            ib.ifeq(byteArray);

            ib.aload(InterpretContext.RIGHT_VALUE);
            ib.checkCast("[Z");
            ib.iload(InterpretContext.MIDDLE_VALUE);
            ib.aload(InterpretContext.LEFT_VALUE);
            ib.checkCast("java/lang/Integer");
            ib.invokeVirtual("java/lang/Integer", "intValue", "()I");

            ib.ifeq(falseValue);
            ib.iconst1();
            ib.goto_(storeBoolean);

            ib.label(falseValue);
            ib.iconst0();

            ib.label(storeBoolean);
            ib.bastore();
            ib.goto_(done);

            ib.label(byteArray);
            ib.aload(InterpretContext.RIGHT_VALUE);
            ib.checkCast("[B");
            ib.iload(InterpretContext.MIDDLE_VALUE);
            ib.aload(InterpretContext.LEFT_VALUE);
            ib.checkCast("java/lang/Number");
            ib.invokeVirtual("java/lang/Number", "byteValue", "()B");
            ib.bastore();

            ib.label(done);
        }
        else
        {
            ib.aload(InterpretContext.RIGHT_VALUE);
            switch (opcode)
            {
                case IASTORE ->
                {
                    ib.checkCast("[I");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.iload(InterpretContext.LEFT_VALUE);
                    ib.iastore();
                }
                case LASTORE ->
                {
                    ib.checkCast("[J");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.lload(InterpretContext.LEFT_VALUE);
                    ib.lastore();
                }
                case FASTORE ->
                {
                    ib.checkCast("[F");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.fload(InterpretContext.LEFT_VALUE);
                    ib.fastore();
                }
                case DASTORE ->
                {
                    ib.checkCast("[D");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.dload(InterpretContext.LEFT_VALUE);
                    ib.dastore();
                }
                case AASTORE ->
                {
                    ib.checkCast("[Ljava/lang/Object;");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.aload(InterpretContext.LEFT_VALUE);
                    ib.aastore();
                }
                case CASTORE ->
                {
                    ib.checkCast("[C");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.iload(InterpretContext.LEFT_VALUE);
                    ib.castore();
                }
                case SASTORE ->
                {
                    ib.checkCast("[S");
                    ib.iload(InterpretContext.MIDDLE_VALUE);
                    ib.iload(InterpretContext.LEFT_VALUE);
                    ib.sastore();
                }
                default -> throw new IllegalArgumentException("Unsupported array store opcode: " + opcode);
            }
        }
        return ib.toInsnList();
    }
}
