package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generates package-agnostic integrity entries and one-shot cold wrappers. */
public final class IntegrityEntryTransformer
{
    private static final String ENTRY_DESCRIPTOR =
            "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";

    private final ClassNode carrierClass;
    private final VMIntegrityPlan plan;
    private final String vmClassName;
    private final GeneratedMemberNamer namer;
    private int sequence;

    public IntegrityEntryTransformer(
            ClassNode carrierClass,
            VMIntegrityPlan plan,
            String vmClassName,
            GeneratedMemberNamer namer)
    {
        this.carrierClass = Objects.requireNonNull(carrierClass, "carrierClass");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.vmClassName = Objects.requireNonNull(vmClassName, "vmClassName");
        this.namer = Objects.requireNonNull(namer, "namer");
        if (!carrierClass.name.equals(plan.owner()))
        {
            throw new IllegalArgumentException("Integrity plan owner does not match carrier class");
        }
    }

    public Result transform(List<CompiledMethod> methods, Set<MethodNode> protectedMethods)
    {
        List<GeneratedColdEntry> coldEntries = new ArrayList<>();
        Map<MethodNode, String> entryNames = new LinkedHashMap<>();
        for (CompiledMethod method : methods)
        {
            if (!protectedMethods.contains(method.source))
            {
                continue;
            }

            String coldName = uniqueMethodName("integrityCold");
            MethodNode coldMethod = generateColdEntry(method, coldName);
            carrierClass.methods.add(coldMethod);

            String entryName = uniqueMethodName("integrityEntry");
            carrierClass.methods.add(generateHotEntry(method, entryName, coldName));
            replaceApplicationStub(method, entryName);
            coldEntries.add(new GeneratedColdEntry(carrierClass, coldMethod));
            entryNames.put(method.source, entryName);
        }
        return new Result(List.copyOf(coldEntries), Map.copyOf(entryNames));
    }

    private MethodNode generateColdEntry(CompiledMethod method, String name)
    {
        MethodNode cold = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNTHETIC},
                name,
                ENTRY_DESCRIPTOR);
        AdvIBdr ib = new AdvIBdr(cold);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 0);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 1);
        Local capability = ib.var("integrityCapability", "I");
        ib.set(capability, AdvIBdr.callStatic(
                plan.owner(),
                plan.methodName(),
                "I"));
        ib.returnValue(execute(ib, method, receiver, arguments, capability, null));
        return cold;
    }

    private MethodNode generateHotEntry(
            CompiledMethod method,
            String name,
            String coldName)
    {
        MethodNode entry = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.SYNTHETIC},
                name,
                ENTRY_DESCRIPTOR);
        AdvIBdr ib = new AdvIBdr(entry);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 0);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 1);
        if (plan.probeMethodName() != null)
        {
            ib.directCall(AdvIBdr.callStatic(
                    carrierClass.name,
                    plan.probeMethodName(),
                    "V"));
        }
        Local envelope = ib.var("stateEnvelope", "J");
        ib.set(envelope, AdvIBdr.staticField(
                carrierClass.name,
                plan.stateFieldName(),
                "J"));

        ib.ifCondition(
                AdvIBdr.equal(envelope, AdvIBdr.constant(0L)),
                miss -> miss.returnValue(AdvIBdr.callStatic(
                        carrierClass.name,
                        coldName,
                        "java/lang/Object",
                        receiver,
                        arguments)));

        Local capability = IntegrityCacheCodec.emitDecode(
                ib,
                envelope,
                plan.cacheLayout(),
                "entry" + sequence);
        ib.returnValue(execute(
                ib,
                method,
                receiver,
                arguments,
                capability,
                DispatchBinding.random()));
        return entry;
    }

    private Expr execute(
            AdvIBdr ib,
            CompiledMethod method,
            Expr receiver,
            Expr arguments,
            Local capability,
            DispatchBinding binding)
    {
        if (method.isSegmented())
        {
            Local codeIds = ib.var("boundCodeIds", "[I");
            ib.set(codeIds, AdvIBdr.newArray(
                    "int",
                    AdvIBdr.constant(method.codeIds.size())));
            for (int index = 0; index < method.codeIds.size(); index++)
            {
                Expr codeId = binding == null
                        ? AdvIBdr.constant(method.codeIds.get(index))
                        : decodeCodeId(capability, method.codeIds.get(index), binding);
                ib.setArray(codeIds, AdvIBdr.constant(index), codeId);
            }
            return AdvIBdr.callStatic(
                    vmClassName,
                    "execute",
                    "java/lang/Object",
                    codeIds,
                    receiver,
                    arguments,
                    capability);
        }
        Expr codeId = binding == null
                ? AdvIBdr.constant(method.codeId)
                : decodeCodeId(capability, method.codeId, binding);
        return AdvIBdr.callStatic(
                vmClassName,
                "execute",
                "java/lang/Object",
                codeId,
                receiver,
                arguments,
                capability);
    }

    private void replaceApplicationStub(CompiledMethod method, String entryName)
    {
        MethodNode source = method.source;
        source.instructions.clear();
        source.tryCatchBlocks.clear();
        source.localVariables = null;
        source.visibleLocalVariableAnnotations = null;
        source.invisibleLocalVariableAnnotations = null;
        source.maxStack = 0;
        source.maxLocals = method.isStatic ? 0 : 1;

        AdvIBdr ib = new AdvIBdr(source);
        Type[] parameterTypes = Type.getArgumentTypes(method.descriptor);
        int parameterSlots = 0;
        for (Type parameterType : parameterTypes)
        {
            parameterSlots += parameterType.getSize();
        }
        Local arguments = ib.var("entryArguments", "[Ljava/lang/Object;");
        ib.set(arguments, AdvIBdr.newArray(
                "java/lang/Object",
                AdvIBdr.constant(parameterSlots)));

        int sourceLocal = method.isStatic ? 0 : 1;
        int argumentSlot = 0;
        for (int index = 0; index < parameterTypes.length; index++)
        {
            Type parameterType = parameterTypes[index];
            Local parameter = ib.getLocal("entryArgument" + index, parameterType, sourceLocal);
            ib.setArray(arguments, AdvIBdr.constant(argumentSlot), parameter);
            sourceLocal += parameterType.getSize();
            argumentSlot += parameterType.getSize();
        }

        Expr receiver = method.isStatic
                ? AdvIBdr.constant(null)
                : AdvIBdr.cast(AdvIBdr.self(method.owner.name), "java/lang/Object");
        Expr entryCall = AdvIBdr.callStatic(
                carrierClass.name,
                entryName,
                "java/lang/Object",
                receiver,
                arguments);
        Type returnType = Type.getReturnType(method.descriptor);
        if (returnType.equals(Type.VOID_TYPE))
        {
            ib.directCall(entryCall);
            ib.returnVoid();
        }
        else
        {
            ib.returnValue(AdvIBdr.cast(entryCall, returnType));
        }
    }

    private Expr decodeCodeId(Local capability, int codeId, DispatchBinding binding)
    {
        int encoded = codeId ^ binding.mask(plan.expectedCapability());
        Expr x = AdvIBdr.bitXor(capability, AdvIBdr.constant(binding.salt()));
        Expr rotated = AdvIBdr.bitOr(
                AdvIBdr.shiftLeft(x, AdvIBdr.constant(binding.rotation())),
                AdvIBdr.unsignedShiftRight(
                        x,
                        AdvIBdr.constant(32 - binding.rotation())));
        Expr runtimeMask = AdvIBdr.bitXor(
                AdvIBdr.multiply(rotated, AdvIBdr.constant(binding.multiplier())),
                AdvIBdr.constant(binding.addend()));
        return AdvIBdr.bitXor(AdvIBdr.constant(encoded), runtimeMask);
    }

    private String uniqueMethodName(String purpose)
    {
        while (true)
        {
            String semanticName = "$vm$" + purpose + '$' + sequence++;
            String candidate = namer.method(carrierClass.name, semanticName, ENTRY_DESCRIPTOR);
            boolean occupied = carrierClass.methods.stream()
                    .anyMatch(method -> method.name.equals(candidate) &&
                            method.desc.equals(ENTRY_DESCRIPTOR));
            if (!occupied)
            {
                return candidate;
            }
        }
    }

    public record GeneratedColdEntry(ClassNode owner, MethodNode method)
    {
    }

    public record Result(List<GeneratedColdEntry> coldEntries, Map<MethodNode, String> entryNames)
    {
        public static Result empty()
        {
            return new Result(List.of(), Map.of());
        }
    }

    private record DispatchBinding(int salt, int rotation, int multiplier, int addend)
    {
        private static DispatchBinding random()
        {
            return new DispatchBinding(
                    nonZeroRandom(),
                    5 + RandomUtils.randomInt(22),
                    nonZeroRandom() | 1,
                    nonZeroRandom());
        }

        private int mask(int capability)
        {
            return Integer.rotateLeft(capability ^ salt, rotation) * multiplier ^ addend;
        }

        private static int nonZeroRandom()
        {
            int value;
            do
            {
                value = RandomUtils.randomInt();
            } while (value == 0);
            return value;
        }
    }
}
