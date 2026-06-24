package nhcm.bytecodevm.Utils.Builder;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class InsnBuilder
{
    private final InsnList insns;

    public InsnBuilder(InsnList insnNodes)
    {
        this.insns = insnNodes;
    }

    public InsnBuilder()
    {
        this.insns = new InsnList();
    }

    public InsnList toInsnList()
    {
        return insns;
    }

    public void aload(int index)
    {
        insns.add(new VarInsnNode(Opcodes.ALOAD, index));
    }

    public void astore(int index)
    {
        insns.add(new VarInsnNode(Opcodes.ASTORE, index));
    }

    public void iload(int index)
    {
        insns.add(new VarInsnNode(Opcodes.ILOAD, index));
    }

    public void istore(int index)
    {
        insns.add(new VarInsnNode(Opcodes.ISTORE, index));
    }

    public void ldc(Object value)
    {
        insns.add(new LdcInsnNode(value));
    }

    public void pop()
    {
        insns.add(new InsnNode(Opcodes.POP));
    }

    public void dup()
    {
        insns.add(new InsnNode(Opcodes.DUP));
    }

    public void iadd()
    {
        insns.add(new InsnNode(Opcodes.IADD));
    }

    public void isub()
    {
        insns.add(new InsnNode(Opcodes.ISUB));
    }

    public void imul()
    {
        insns.add(new InsnNode(Opcodes.IMUL));
    }

    public void idiv()
    {
        insns.add(new InsnNode(Opcodes.IDIV));
    }

    public void _return()
    {
        insns.add(new InsnNode(Opcodes.RETURN));
    }

    public void ireturn()
    {
        insns.add(new InsnNode(Opcodes.IRETURN));
    }

    public void areturn()
    {
        insns.add(new InsnNode(Opcodes.ARETURN));
    }

    public void getStatic(String owner, String name, String desc)
    {
        insns.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                owner,
                name,
                desc
        ));
    }

    public void getStatic(FieldRef field)
    {
        getStatic(field.owner(), field.name(), field.descriptor());
    }

    public void putStatic(String owner, String name, String desc)
    {
        insns.add(new FieldInsnNode(
                Opcodes.PUTSTATIC,
                owner,
                name,
                desc
        ));
    }

    public void putStatic(FieldRef field)
    {
        putStatic(field.owner(), field.name(), field.descriptor());
    }

    public void getField(String owner, String name, String desc)
    {
        insns.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                owner,
                name,
                desc
        ));
    }

    public void getField(FieldRef field)
    {
        getField(field.owner(), field.name(), field.descriptor());
    }

    public void putField(String owner, String name, String desc)
    {
        insns.add(new FieldInsnNode(
                Opcodes.PUTFIELD,
                owner,
                name,
                desc
        ));
    }

    public void putField(FieldRef field)
    {
        putField(field.owner(), field.name(), field.descriptor());
    }

    public void invokeVirtual(
            String owner,
            String name,
            String desc)
    {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                owner,
                name,
                desc,
                false
        ));
    }

    public void invokeVirtual(MethodRef method)
    {
        invokeVirtual(method.owner(), method.name(), method.descriptor());
    }

    public void invokeStatic(
            String owner,
            String name,
            String desc)
    {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner,
                name,
                desc,
                false
        ));
    }

    public void invokeStatic(MethodRef method)
    {
        invokeStatic(method.owner(), method.name(), method.descriptor());
    }

    public void invokeSpecial(
            String owner,
            String name,
            String desc)
    {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                owner,
                name,
                desc,
                false
        ));
    }

    public void invokeSpecial(MethodRef method)
    {
        invokeSpecial(method.owner(), method.name(), method.descriptor());
    }

    public void invokeInterface(
            String owner,
            String name,
            String desc)
    {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                owner,
                name,
                desc,
                true
        ));
    }

    public void invokeInterface(MethodRef method)
    {
        invokeInterface(method.owner(), method.name(), method.descriptor());
    }

    public void new_(String type)
    {
        insns.add(new TypeInsnNode(
                Opcodes.NEW,
                type
        ));
    }

    public void checkCast(String type)
    {
        insns.add(new TypeInsnNode(
                Opcodes.CHECKCAST,
                type
        ));
    }

    public void instanceOf(String type)
    {
        insns.add(new TypeInsnNode(
                Opcodes.INSTANCEOF,
                type
        ));
    }

    public void aneArray(String type)
    {
        insns.add(new TypeInsnNode(
                Opcodes.ANEWARRAY,
                type
        ));
    }

    public void nop() { insns.add(new InsnNode(Opcodes.NOP)); }

    public void aconstNull() { insns.add(new InsnNode(Opcodes.ACONST_NULL)); }

    public void iconstM1() { insns.add(new InsnNode(Opcodes.ICONST_M1)); }
    public void iconst0() { insns.add(new InsnNode(Opcodes.ICONST_0)); }
    public void iconst1() { insns.add(new InsnNode(Opcodes.ICONST_1)); }
    public void iconst2() { insns.add(new InsnNode(Opcodes.ICONST_2)); }
    public void iconst3() { insns.add(new InsnNode(Opcodes.ICONST_3)); }
    public void iconst4() { insns.add(new InsnNode(Opcodes.ICONST_4)); }
    public void iconst5() { insns.add(new InsnNode(Opcodes.ICONST_5)); }

    public void lconst0() { insns.add(new InsnNode(Opcodes.LCONST_0)); }
    public void lconst1() { insns.add(new InsnNode(Opcodes.LCONST_1)); }

    public void fconst0() { insns.add(new InsnNode(Opcodes.FCONST_0)); }
    public void fconst1() { insns.add(new InsnNode(Opcodes.FCONST_1)); }
    public void fconst2() { insns.add(new InsnNode(Opcodes.FCONST_2)); }

    public void dconst0() { insns.add(new InsnNode(Opcodes.DCONST_0)); }
    public void dconst1() { insns.add(new InsnNode(Opcodes.DCONST_1)); }

    public void bipush(int value) { insns.add(new IntInsnNode(Opcodes.BIPUSH, value)); }
    public void sipush(int value) { insns.add(new IntInsnNode(Opcodes.SIPUSH, value)); }

    public void lload(int index) { insns.add(new VarInsnNode(Opcodes.LLOAD, index)); }
    public void fload(int index) { insns.add(new VarInsnNode(Opcodes.FLOAD, index)); }
    public void dload(int index) { insns.add(new VarInsnNode(Opcodes.DLOAD, index)); }

    public void lstore(int index) { insns.add(new VarInsnNode(Opcodes.LSTORE, index)); }
    public void fstore(int index) { insns.add(new VarInsnNode(Opcodes.FSTORE, index)); }
    public void dstore(int index) { insns.add(new VarInsnNode(Opcodes.DSTORE, index)); }

    public void iaload() { insns.add(new InsnNode(Opcodes.IALOAD)); }
    public void laload() { insns.add(new InsnNode(Opcodes.LALOAD)); }
    public void faload() { insns.add(new InsnNode(Opcodes.FALOAD)); }
    public void daload() { insns.add(new InsnNode(Opcodes.DALOAD)); }
    public void aaload() { insns.add(new InsnNode(Opcodes.AALOAD)); }
    public void baload() { insns.add(new InsnNode(Opcodes.BALOAD)); }
    public void caload() { insns.add(new InsnNode(Opcodes.CALOAD)); }
    public void saload() { insns.add(new InsnNode(Opcodes.SALOAD)); }

    public void iastore() { insns.add(new InsnNode(Opcodes.IASTORE)); }
    public void lastore() { insns.add(new InsnNode(Opcodes.LASTORE)); }
    public void fastore() { insns.add(new InsnNode(Opcodes.FASTORE)); }
    public void dastore() { insns.add(new InsnNode(Opcodes.DASTORE)); }
    public void aastore() { insns.add(new InsnNode(Opcodes.AASTORE)); }
    public void bastore() { insns.add(new InsnNode(Opcodes.BASTORE)); }
    public void castore() { insns.add(new InsnNode(Opcodes.CASTORE)); }
    public void sastore() { insns.add(new InsnNode(Opcodes.SASTORE)); }

    public void pop2() { insns.add(new InsnNode(Opcodes.POP2)); }
    public void dupX1() { insns.add(new InsnNode(Opcodes.DUP_X1)); }
    public void dupX2() { insns.add(new InsnNode(Opcodes.DUP_X2)); }
    public void dup2() { insns.add(new InsnNode(Opcodes.DUP2)); }
    public void dup2X1() { insns.add(new InsnNode(Opcodes.DUP2_X1)); }
    public void dup2X2() { insns.add(new InsnNode(Opcodes.DUP2_X2)); }
    public void swap() { insns.add(new InsnNode(Opcodes.SWAP)); }

    public void ladd() { insns.add(new InsnNode(Opcodes.LADD)); }
    public void fadd() { insns.add(new InsnNode(Opcodes.FADD)); }
    public void dadd() { insns.add(new InsnNode(Opcodes.DADD)); }

    public void lsub() { insns.add(new InsnNode(Opcodes.LSUB)); }
    public void fsub() { insns.add(new InsnNode(Opcodes.FSUB)); }
    public void dsub() { insns.add(new InsnNode(Opcodes.DSUB)); }

    public void lmul() { insns.add(new InsnNode(Opcodes.LMUL)); }
    public void fmul() { insns.add(new InsnNode(Opcodes.FMUL)); }
    public void dmul() { insns.add(new InsnNode(Opcodes.DMUL)); }

    public void ldiv() { insns.add(new InsnNode(Opcodes.LDIV)); }
    public void fdiv() { insns.add(new InsnNode(Opcodes.FDIV)); }
    public void ddiv() { insns.add(new InsnNode(Opcodes.DDIV)); }

    public void irem() { insns.add(new InsnNode(Opcodes.IREM)); }
    public void lrem() { insns.add(new InsnNode(Opcodes.LREM)); }
    public void frem() { insns.add(new InsnNode(Opcodes.FREM)); }
    public void drem() { insns.add(new InsnNode(Opcodes.DREM)); }

    public void ineg() { insns.add(new InsnNode(Opcodes.INEG)); }
    public void lneg() { insns.add(new InsnNode(Opcodes.LNEG)); }
    public void fneg() { insns.add(new InsnNode(Opcodes.FNEG)); }
    public void dneg() { insns.add(new InsnNode(Opcodes.DNEG)); }

    public void ishl() { insns.add(new InsnNode(Opcodes.ISHL)); }
    public void lshl() { insns.add(new InsnNode(Opcodes.LSHL)); }
    public void ishr() { insns.add(new InsnNode(Opcodes.ISHR)); }
    public void lshr() { insns.add(new InsnNode(Opcodes.LSHR)); }
    public void iushr() { insns.add(new InsnNode(Opcodes.IUSHR)); }
    public void lushr() { insns.add(new InsnNode(Opcodes.LUSHR)); }

    public void iand() { insns.add(new InsnNode(Opcodes.IAND)); }
    public void land() { insns.add(new InsnNode(Opcodes.LAND)); }
    public void ior() { insns.add(new InsnNode(Opcodes.IOR)); }
    public void lor() { insns.add(new InsnNode(Opcodes.LOR)); }
    public void ixor() { insns.add(new InsnNode(Opcodes.IXOR)); }
    public void lxor() { insns.add(new InsnNode(Opcodes.LXOR)); }

    public void iinc(int index, int increment) {
        insns.add(new IincInsnNode(index, increment));
    }

    public void i2l() { insns.add(new InsnNode(Opcodes.I2L)); }
    public void i2f() { insns.add(new InsnNode(Opcodes.I2F)); }
    public void i2d() { insns.add(new InsnNode(Opcodes.I2D)); }

    public void l2i() { insns.add(new InsnNode(Opcodes.L2I)); }
    public void l2f() { insns.add(new InsnNode(Opcodes.L2F)); }
    public void l2d() { insns.add(new InsnNode(Opcodes.L2D)); }

    public void f2i() { insns.add(new InsnNode(Opcodes.F2I)); }
    public void f2l() { insns.add(new InsnNode(Opcodes.F2L)); }
    public void f2d() { insns.add(new InsnNode(Opcodes.F2D)); }

    public void d2i() { insns.add(new InsnNode(Opcodes.D2I)); }
    public void d2l() { insns.add(new InsnNode(Opcodes.D2L)); }
    public void d2f() { insns.add(new InsnNode(Opcodes.D2F)); }

    public void i2b() { insns.add(new InsnNode(Opcodes.I2B)); }
    public void i2c() { insns.add(new InsnNode(Opcodes.I2C)); }
    public void i2s() { insns.add(new InsnNode(Opcodes.I2S)); }

    public void lcmp() { insns.add(new InsnNode(Opcodes.LCMP)); }
    public void fcmpl() { insns.add(new InsnNode(Opcodes.FCMPL)); }
    public void fcmpg() { insns.add(new InsnNode(Opcodes.FCMPG)); }
    public void dcmpl() { insns.add(new InsnNode(Opcodes.DCMPL)); }
    public void dcmpg() { insns.add(new InsnNode(Opcodes.DCMPG)); }

    public void ifeq(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFEQ, label)); }
    public void ifne(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFNE, label)); }
    public void iflt(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFLT, label)); }
    public void ifge(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFGE, label)); }
    public void ifgt(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFGT, label)); }
    public void ifle(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFLE, label)); }

    public void ifIcmpEq(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, label)); }
    public void ifIcmpNe(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPNE, label)); }
    public void ifIcmpLt(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPLT, label)); }
    public void ifIcmpGe(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPGE, label)); }
    public void ifIcmpGt(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPGT, label)); }
    public void ifIcmpLe(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ICMPLE, label)); }

    public void ifAcmpEq(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, label)); }
    public void ifAcmpNe(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IF_ACMPNE, label)); }

    public void goto_(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.GOTO, label)); }
    public void jsr(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.JSR, label)); }
    public void ret(int index) { insns.add(new VarInsnNode(Opcodes.RET, index)); }

    public void tableSwitch(int min, int max, LabelNode dflt, LabelNode... labels) {
        insns.add(new TableSwitchInsnNode(min, max, dflt, labels));
    }

    public void lookupSwitch(LabelNode dflt, int[] keys, LabelNode[] labels) {
        insns.add(new LookupSwitchInsnNode(dflt, keys, labels));
    }

    public void lreturn() { insns.add(new InsnNode(Opcodes.LRETURN)); }
    public void freturn() { insns.add(new InsnNode(Opcodes.FRETURN)); }
    public void dreturn() { insns.add(new InsnNode(Opcodes.DRETURN)); }

    public void arrayLength() { insns.add(new InsnNode(Opcodes.ARRAYLENGTH)); }
    public void athrow() { insns.add(new InsnNode(Opcodes.ATHROW)); }

    public void monitorEnter() { insns.add(new InsnNode(Opcodes.MONITORENTER)); }
    public void monitorExit() { insns.add(new InsnNode(Opcodes.MONITOREXIT)); }

    public void ifNull(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFNULL, label)); }
    public void ifNonNull(LabelNode label) { insns.add(new JumpInsnNode(Opcodes.IFNONNULL, label)); }

    public void newArray(int type) {
        insns.add(new IntInsnNode(Opcodes.NEWARRAY, type));
    }

    public void multiANewArray(String desc, int dimensions) {
        insns.add(new MultiANewArrayInsnNode(desc, dimensions));
    }

    public void invokeDynamic(String name, String desc, Handle bootstrap, Object... args) {
        insns.add(new InvokeDynamicInsnNode(name, desc, bootstrap, args));
    }

    public void frame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        insns.add(new FrameNode(type, numLocal, local, numStack, stack));
    }

    public LabelNode label() {
        LabelNode label = new LabelNode(new Label());
        insns.add(label);
        return label;
    }

    public void label(LabelNode label) {
        insns.add(label);
    }

    public void line(int line, LabelNode start) {
        insns.add(new LineNumberNode(line, start));
    }

    public void pushInt(int value) {
        if (value == -1) {
            iconstM1();
        } else if (value >= 0 && value <= 5) {
            insns.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            bipush(value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            sipush(value);
        } else {
            ldc(value);
        }
    }

    public void pushLong(long value) {
        if (value == 0L) {
            lconst0();
        } else if (value == 1L) {
            lconst1();
        } else {
            ldc(value);
        }
    }

    public void pushFloat(float value) {
        if (value == 0.0F) {
            fconst0();
        } else if (value == 1.0F) {
            fconst1();
        } else if (value == 2.0F) {
            fconst2();
        } else {
            ldc(value);
        }
    }

    public void pushDouble(double value) {
        if (value == 0.0D) {
            dconst0();
        } else if (value == 1.0D) {
            dconst1();
        } else {
            ldc(value);
        }
    }

    public void pushString(String value) {
        ldc(value);
    }

    public void pushClass(Type type) {
        ldc(type);
    }

    public void add(AbstractInsnNode insn)
    {
        insns.add(insn);
    }
}
