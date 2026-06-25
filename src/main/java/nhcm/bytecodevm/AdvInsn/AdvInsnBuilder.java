package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.TypeUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Instruction builder with an API closer to Java source code.
 * <p>
 * This class only turns source-like expressions into ASM instructions. The final
 * VM bytecode is still produced by {@link nhcm.bytecodevm.Tools.VMMethodCompiler}.
 * <p>
 * Types can be passed as {@link Type}, {@link Class}, or strings:
 * {@code "I"}, {@code "java/lang/String"}, {@code "Ljava/lang/String;"}, {@code "[I"}.
 */
public class AdvInsnBuilder
{
    // Wrapped low-level writer used only by this DSL implementation.
    private final InsnBuilder builder;

    // When bound to a MethodNode, instructions are written directly to method.instructions.
    private final MethodNode method;

    // User-friendly local name -> JVM local slot metadata.
    private final Map<String, Local> locals = new LinkedHashMap<>();

    // Human-readable Java-like view, used only for debugging/display.
    private final StringBuilder sourceView = new StringBuilder();

    // Active flow scopes used by break/continue.
    private final Deque<FlowScope> flowScopes = new ArrayDeque<>();

    // Next local slot used by automatic local allocation.
    private int nextLocal;

    // Current indentation depth for sourceView.
    private int indent;

    // Conservative maxStack hint used when no precise stack analysis is performed.
    private int maxStackHint = 12;

    /**
     * Binds a MethodNode. All later source-like calls write directly into method.instructions.
     */
    public AdvInsnBuilder(MethodNode method)
    {
        this.builder = new InsnBuilder(method.instructions);
        this.method = method;
        this.nextLocal = initialLocal(method);
        updateMaxLocals(nextLocal);
    }

    /**
     * Create a new AdvInsnBuilder without a MethodNode with fresh insn list.
     */
    public AdvInsnBuilder(int nextLocal)
    {
        this.builder = new InsnBuilder();
        this.method = null;
        this.nextLocal = nextLocal;
    }

    /**
     * Writes directly into the given MethodNode.
     */
    public static AdvInsnBuilder into(MethodNode method)
    {
        return new AdvInsnBuilder(method);
    }

    /**
     * Returns the underlying InsnBuilder for instructions not yet covered by the DSL.
     */
    public InsnBuilder rawBuilder()
    {
        return builder;
    }

    /**
     * Runs raw InsnBuilder code for bytecode not yet wrapped by the high-level API.
     */
    public AdvInsnBuilder raw(Consumer<InsnBuilder> emitter)
    {
        emitter.accept(builder);
        appendView("// raw(builder);");
        return this;
    }

    /**
     * Calls a no-argument super constructor from an instance constructor.
     */
    public AdvInsnBuilder callNoArgSuperConstructor(String owner)
    {
        appendView("super();");
        builder.aload(0);
        builder.invokeSpecial(owner, "<init>", "()V");
        return this;
    }

    /**
     * Returns the Java-like source view so the generated logic can be inspected.
     */
    public String sourceView()
    {
        return sourceView.toString();
    }

    /**
     * Returns a previously registered local by name.
     */
    public Local getLocal(String name)
    {
        Local local = locals.get(name);
        if (local == null)
        {
            throw new IllegalArgumentException("Unknown local: " + name);
        }
        return local;
    }

    /**
     * Registers an existing local slot using a Class visible at compile time.
     */
    public Local getLocal(String name, Class<?> type, int index)
    {
        return getLocal(name, AdvInsnSupport.type(type), index);
    }

    /**
     * Registers an existing local slot using a type string, useful when target classes are unavailable here.
     */
    public Local getLocal(String name, String type, int index)
    {
        return getLocal(name, type(type), index);
    }

    /**
     * Registers an existing local slot using an ASM Type.
     */
    public Local getLocal(String name, Type type, int index)
    {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Local local = new Local(name, type, index);
        locals.put(name, local);
        updateMaxLocals(index + AdvInsnSupport.slotSize(type));
        return local;
    }

    /**
     * Allocates a new local slot automatically and returns it as an expression-ready Local.
     */
    public Local var(String name, Class<?> type)
    {
        return var(name, AdvInsnSupport.type(type));
    }

    /**
     * Allocates a new local slot automatically using a type string.
     */
    public Local var(String name, String type)
    {
        return var(name, type(type));
    }

    /**
     * Allocates a new local slot automatically using an ASM Type.
     */
    public Local var(String name, Type type)
    {
        Local local = getLocal(name, type, nextLocal);
        nextLocal += AdvInsnSupport.slotSize(type);
        updateMaxLocals(nextLocal);
        return local;
    }

    /**
     * Emits a local declaration and assignment, for example {@code int x = 1;}.
     */
    public AdvInsnBuilder createLocal(String name, Class<?> type, Expr value)
    {
        return createLocal(name, AdvInsnSupport.type(type), value);
    }

    /**
     * Emits a local declaration and assignment using a type string.
     */
    public AdvInsnBuilder createLocal(String name, String type, Expr value)
    {
        return createLocal(name, type(type), value);
    }

    /**
     * Emits a local declaration and assignment using an ASM Type.
     */
    public AdvInsnBuilder createLocal(String name, Type type, Expr value)
    {
        Local local = var(name, type);
        value = autoCastIfNeeded(local.type(), value);
        AdvInsnSupport.requireAssignable(local.type(), value.type());
        appendView(AdvInsnSupport.simpleName(type) + " " + local.name() + " = " + value.source() + ";");
        value.emit(builder);
        AdvInsnSupport.store(builder, local.type(), local.index());
        return this;
    }

    /**
     * Short alias for createLocal, making chained code read more like a declaration.
     */
    public AdvInsnBuilder let(String name, Class<?> type, Expr value)
    {
        return createLocal(name, type, value);
    }

    /**
     * Short alias for createLocal using a type string.
     */
    public AdvInsnBuilder let(String name, String type, Expr value)
    {
        return createLocal(name, type, value);
    }

    /**
     * Short alias for createLocal using an ASM Type.
     */
    public AdvInsnBuilder let(String name, Type type, Expr value)
    {
        return createLocal(name, type, value);
    }

    /**
     * Writes a value to a Local, for example {@code x = expr;}.
     */
    public AdvInsnBuilder writeLocal(Local local, Expr value)
    {
        value = autoCastIfNeeded(local.type(), value);
        AdvInsnSupport.requireAssignable(local.type(), value.type());
        appendView(local.name() + " = " + value.source() + ";");
        value.emit(builder);
        AdvInsnSupport.store(builder, local.type(), local.index());
        return this;
    }

    /**
     * Writes a value to a registered local by name.
     */
    public AdvInsnBuilder writeLocal(String localName, Expr value)
    {
        return writeLocal(getLocal(localName), value);
    }

    /**
     * Stores the value currently on top of the JVM operand stack into a local.
     * <p>
     * This is useful at DSL boundaries where an earlier operation intentionally
     * leaves a value on the stack.
     */
    public AdvInsnBuilder storeTop(Local local)
    {
        appendView(local.name() + " = <stackTop>;");
        AdvInsnSupport.store(builder, local.type(), local.index());
        return this;
    }

    /**
     * Short alias for writeLocal.
     */
    public AdvInsnBuilder set(Local local, Expr value)
    {
        return writeLocal(local, value);
    }

    /**
     * Short alias for writeLocal by local name.
     */
    public AdvInsnBuilder set(String localName, Expr value)
    {
        return writeLocal(localName, value);
    }

    /**
     * Writes an instance field, for example {@code obj.field = value;}.
     */
    public AdvInsnBuilder writeLocal(FieldAccess field, Expr value)
    {
        value = autoCastIfNeeded(field.type(), value);
        AdvInsnSupport.requireAssignable(field.type(), value.type());
        appendView(field.source() + " = " + value.source() + ";");
        field.emitOwner(builder);
        value.emit(builder);
        builder.putField(field.ownerType().getInternalName(), field.name(), field.type().getDescriptor());
        return this;
    }

    /**
     * Short alias for writing an instance field.
     */
    public AdvInsnBuilder set(FieldAccess field, Expr value)
    {
        return writeLocal(field, value);
    }

    /**
     * Writes a static field, for example {@code Owner.field = value;}.
     */
    public AdvInsnBuilder writeLocal(StaticFieldAccess field, Expr value)
    {
        value = autoCastIfNeeded(field.type(), value);
        AdvInsnSupport.requireAssignable(field.type(), value.type());
        appendView(field.source() + " = " + value.source() + ";");
        value.emit(builder);
        builder.putStatic(field.owner().getInternalName(), field.name(), field.type().getDescriptor());
        return this;
    }

    /**
     * Short alias for writing a static field.
     */
    public AdvInsnBuilder set(StaticFieldAccess field, Expr value)
    {
        return writeLocal(field, value);
    }

    /**
     * Uses IINC on an int-like local, for example {@code i += 1;}.
     */
    public AdvInsnBuilder increment(Local local, int amount)
    {
        if (!AdvInsnSupport.isIntLike(local.type()))
        {
            throw new IllegalArgumentException("Only int-like locals can use increment: " + local.type());
        }
        appendView(local.name() + " += " + amount + ";");
        builder.iinc(local.index(), amount);
        return this;
    }

    /**
     * Uses IINC on an int-like local by name.
     */
    public AdvInsnBuilder increment(String localName, int amount)
    {
        return increment(getLocal(localName), amount);
    }

    /**
     * Evaluates an expression and drops the return value, useful for method-call statements.
     */
    public AdvInsnBuilder directCall(Expr expression)
    {
        appendView(expression.source() + ";");
        expression.emit(builder);
        if (expression.type().getSort() != Type.VOID)
        {
            AdvInsnSupport.drop(builder, expression.type());
        }
        return this;
    }

    /**
     * Returns an expression value, choosing IRETURN/LRETURN/ARETURN/etc. by type.
     */
    public AdvInsnBuilder returnValue(Expr value)
    {
        if (method != null)
        {
            Type returnType = Type.getReturnType(method.desc);
            value = autoCastIfNeeded(returnType, value);
            AdvInsnSupport.requireAssignable(returnType, value.type());
        }
        appendView(value.type().getSort() == Type.VOID ? "return;" : "return " + value.source() + ";");
        value.emit(builder);
        TypeUtils.returnValue(builder, value.type());
        return this;
    }

    /**
     * Emits a void return.
     */
    public AdvInsnBuilder returnVoid()
    {
        appendView("return;");
        builder._return();
        return this;
    }

    /**
     * Throws a Throwable expression.
     */
    public AdvInsnBuilder throwValue(Expr throwable)
    {
        AdvInsnSupport.requireReference(throwable.type(), "throw value");
        appendView("throw " + throwable.source() + ";");
        throwable.emit(builder);
        builder.athrow();
        return this;
    }

    /**
     * Emits a single-branch if. thenBlock runs when the condition is true.
     */
    public AdvInsnBuilder ifCondition(Condition condition, Consumer<AdvInsnBuilder> thenBlock)
    {
        LabelNode end = new LabelNode();
        appendView("if (" + condition.source() + ") {");
        indent++;
        condition.jumpIfFalse(this, end);
        thenBlock.accept(this);
        indent--;
        appendView("}");
        builder.label(end);
        return this;
    }

    /**
     * Emits an if/else branch.
     */
    public AdvInsnBuilder ifElse(
            Condition condition,
            Consumer<AdvInsnBuilder> thenBlock,
            Consumer<AdvInsnBuilder> elseBlock)
    {
        LabelNode elseLabel = new LabelNode();
        LabelNode end = new LabelNode();
        appendView("if (" + condition.source() + ") {");
        indent++;
        condition.jumpIfFalse(this, elseLabel);
        thenBlock.accept(this);
        indent--;
        appendView("} else {");
        indent++;
        builder.goto_(end);
        builder.label(elseLabel);
        elseBlock.accept(this);
        indent--;
        appendView("}");
        builder.label(end);
        return this;
    }

    /**
     * Semantic alias for ifElse, reading more like "when ... else ...".
     */
    public AdvInsnBuilder whenElse(
            Condition condition,
            Consumer<AdvInsnBuilder> thenBlock,
            Consumer<AdvInsnBuilder> elseBlock)
    {
        return ifElse(condition, thenBlock, elseBlock);
    }

    /**
     * Emits a while loop. The condition is checked before each body execution.
     */
    public AdvInsnBuilder whileLoop(Condition condition, Consumer<AdvInsnBuilder> body)
    {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        appendView("while (" + condition.source() + ") {");
        indent++;
        builder.label(start);
        condition.jumpIfFalse(this, end);
        flowScopes.push(new FlowScope(start, end));
        body.accept(this);
        flowScopes.pop();
        indent--;
        appendView("}");
        builder.goto_(start);
        builder.label(end);
        return this;
    }

    /**
     * Emits a do/while loop. The body runs once before the condition is checked.
     */
    public AdvInsnBuilder doWhileLoop(Consumer<AdvInsnBuilder> body, Condition condition)
    {
        LabelNode start = new LabelNode();
        LabelNode conditionLabel = new LabelNode();
        LabelNode end = new LabelNode();
        appendView("do {");
        indent++;
        builder.label(start);
        flowScopes.push(new FlowScope(conditionLabel, end));
        body.accept(this);
        flowScopes.pop();
        builder.label(conditionLabel);
        condition.jumpIfTrue(this, start);
        indent--;
        appendView("} while (" + condition.source() + ");");
        builder.label(end);
        return this;
    }

    /**
     * Emits a source-like for loop: init; condition; body; update.
     */
    public AdvInsnBuilder forLoop(
            Consumer<AdvInsnBuilder> init,
            Condition condition,
            Consumer<AdvInsnBuilder> update,
            Consumer<AdvInsnBuilder> body)
    {
        LabelNode start = new LabelNode();
        LabelNode updateLabel = new LabelNode();
        LabelNode end = new LabelNode();

        appendView("for (...) {");
        init.accept(this);
        indent++;
        builder.label(start);
        condition.jumpIfFalse(this, end);
        flowScopes.push(new FlowScope(updateLabel, end));
        body.accept(this);
        flowScopes.pop();
        builder.label(updateLabel);
        update.accept(this);
        builder.goto_(start);
        indent--;
        appendView("}");
        builder.label(end);
        return this;
    }

    /**
     * Jumps to the nearest enclosing loop/switch break target.
     */
    public AdvInsnBuilder breakFlow()
    {
        FlowScope scope = requireBreakScope();
        appendView("break;");
        builder.goto_(scope.breakLabel());
        return this;
    }

    /**
     * Alias for breakFlow.
     */
    public AdvInsnBuilder breakLoop()
    {
        return breakFlow();
    }

    /**
     * Jumps to the nearest enclosing loop continue target.
     */
    public AdvInsnBuilder continueLoop()
    {
        FlowScope scope = requireContinueScope();
        appendView("continue;");
        builder.goto_(scope.continueLabel());
        return this;
    }

    /**
     * Emits a conditional jump when condition is true.
     */
    public AdvInsnBuilder jumpIf(Condition condition, LabelNode target)
    {
        appendView("// if (" + condition.source() + ") goto label");
        condition.jumpIfTrue(this, target);
        return this;
    }

    /**
     * Emits a conditional jump when condition is false.
     */
    public AdvInsnBuilder jumpIfNot(Condition condition, LabelNode target)
    {
        appendView("// if (!(" + condition.source() + ")) goto label");
        condition.jumpIfFalse(this, target);
        return this;
    }

    /**
     * Emits a monitorEnter/monitorExit synchronized block.
     * This does not currently add try/finally cleanup for exceptional exits; use raw for complex synchronization.
     */
    public AdvInsnBuilder synchronizedBlock(Expr lock, Consumer<AdvInsnBuilder> body)
    {
        AdvInsnSupport.requireReference(lock.type(), "synchronized lock");
        appendView("synchronized (" + lock.source() + ") {");
        indent++;
        lock.emit(builder);
        builder.monitorEnter();
        body.accept(this);
        lock.emit(builder);
        builder.monitorExit();
        indent--;
        appendView("}");
        return this;
    }

    /**
     * Creates a catch block description for tryCatch.
     */
    public static CatchBlock catchBlock(String exceptionType, String localName, Consumer<AdvInsnBuilder> body)
    {
        return CatchBlock.catchType(exceptionType, localName, body);
    }

    /**
     * Creates a catch-all block description for tryCatch.
     */
    public static CatchBlock catchAny(String localName, Consumer<AdvInsnBuilder> body)
    {
        return CatchBlock.catchAny(localName, body);
    }

    /**
     * Emits a try/catch block. This requires the builder to be bound to a MethodNode.
     */
    public AdvInsnBuilder tryCatch(Consumer<AdvInsnBuilder> tryBlock, CatchBlock... catches)
    {
        requireMethod("try/catch");
        if (catches.length == 0)
        {
            throw new IllegalArgumentException("tryCatch needs at least one catch block");
        }

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode done = new LabelNode();
        List<LabelNode> handlers = new ArrayList<>(catches.length);

        appendView("try {");
        indent++;
        builder.label(start);
        tryBlock.accept(this);
        builder.label(end);
        builder.goto_(done);
        indent--;
        appendView("}");

        for (CatchBlock catchBlock : catches)
        {
            LabelNode handler = new LabelNode();
            handlers.add(handler);
            method.tryCatchBlocks.add(new TryCatchBlockNode(
                    start,
                    end,
                    handler,
                    catchType(catchBlock.exceptionType())));

            String displayType = catchBlock.exceptionType() == null ? "Throwable" : AdvInsnSupport.simpleName(object(catchBlock.exceptionType()));
            appendView("catch (" + displayType + " " + catchBlock.localName() + ") {");
            indent++;
            builder.label(handler);
            storeCaughtException(catchBlock);
            catchBlock.body().accept(this);
            builder.goto_(done);
            indent--;
            appendView("}");
        }

        builder.label(done);
        return this;
    }

    /**
     * Convenience overload for one typed catch block.
     */
    public AdvInsnBuilder tryCatch(
            Consumer<AdvInsnBuilder> tryBlock,
            String exceptionType,
            String localName,
            Consumer<AdvInsnBuilder> catchBlock)
    {
        return tryCatch(tryBlock, catchBlock(exceptionType, localName, catchBlock));
    }

    /**
     * Emits a basic try/finally shape. Returns inside tryBlock will not be rewritten;
     * use raw bytecode for complex finally semantics.
     */
    public AdvInsnBuilder tryFinally(
            Consumer<AdvInsnBuilder> tryBlock,
            Consumer<AdvInsnBuilder> finallyBlock)
    {
        requireMethod("try/finally");
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode done = new LabelNode();
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));

        appendView("try {");
        indent++;
        builder.label(start);
        tryBlock.accept(this);
        builder.label(end);
        indent--;
        appendView("} finally {");
        indent++;
        finallyBlock.accept(this);
        builder.goto_(done);

        builder.label(handler);
        Local throwable = var("$finallyThrowable" + nextLocal, "java/lang/Throwable");
        AdvInsnSupport.store(builder, throwable.type(), throwable.index());
        finallyBlock.accept(this);
        throwable.emit(builder);
        builder.athrow();
        indent--;
        appendView("}");
        builder.label(done);
        return this;
    }

    /**
     * Writes an array element, for example {@code arr[index] = value;}.
     */
    public AdvInsnBuilder setArray(Expr array, Expr index, Expr value)
    {
        Type arrayType = array.type();
        if (arrayType.getSort() != Type.ARRAY)
        {
            throw new IllegalArgumentException("Not an array: " + arrayType);
        }
        Type elementType = arrayType.getElementType();
        value = autoCastIfNeeded(elementType, value);
        AdvInsnSupport.requireAssignable(elementType, value.type());
        appendView(array.source() + "[" + index.source() + "] = " + value.source() + ";");
        array.emit(builder);
        index.emit(builder);
        value.emit(builder);
        emitArrayStore(elementType);
        return this;
    }

    /**
     * Writes an array element when the value is easiest to produce inline.
     */
    public AdvInsnBuilder setArray(
            Expr array,
            Expr index,
            Type valueType,
            Consumer<AdvInsnBuilder> valueEmitter,
            String valueSource)
    {
        Type arrayType = array.type();
        if (arrayType.getSort() != Type.ARRAY)
        {
            throw new IllegalArgumentException("Not an array: " + arrayType);
        }
        Type elementType = arrayType.getElementType();
        boolean autoPrimitiveCast = shouldAutoPrimitiveCast(elementType, valueType);
        boolean autoBox = shouldAutoBox(elementType, valueType);
        Type actualValueType = autoPrimitiveCast ? elementType : (autoBox ? wrapperType(valueType) : valueType);
        AdvInsnSupport.requireAssignable(elementType, actualValueType);
        appendView(array.source() + "[" + index.source() + "] = " + valueSource + ";");
        array.emit(builder);
        index.emit(builder);
        valueEmitter.accept(this);
        if (autoPrimitiveCast)
        {
            AdvInsnSupport.convert(valueType, elementType, builder);
        }
        else if (autoBox)
        {
            emitBox(builder, valueType);
        }
        emitArrayStore(elementType);
        return this;
    }

    /**
     * Writes an array element when the value is easiest to produce inline, using a type string.
     */
    public AdvInsnBuilder setArray(
            Expr array,
            Expr index,
            String valueType,
            Consumer<AdvInsnBuilder> valueEmitter,
            String valueSource)
    {
        return setArray(array, index, type(valueType), valueEmitter, valueSource);
    }

    /**
     * Creates a switch case description.
     */
    public static SwitchCase switchCase(int key, Consumer<AdvInsnBuilder> body)
    {
        return SwitchCase.of(key, body);
    }

    /**
     * Emits a lookup switch for sparse int keys. Each case jumps to the end after its body.
     */
    public AdvInsnBuilder switchLookup(Expr selector, Consumer<AdvInsnBuilder> defaultBlock, SwitchCase... cases)
    {
        List<SwitchCase> sortedCases = new ArrayList<>(List.of(cases));
        sortedCases.sort((left, right) -> Integer.compare(left.key(), right.key()));

        LabelNode defaultLabel = new LabelNode();
        LabelNode end = new LabelNode();
        int[] keys = new int[sortedCases.size()];
        LabelNode[] labels = new LabelNode[sortedCases.size()];

        for (int i = 0; i < sortedCases.size(); i++)
        {
            keys[i] = sortedCases.get(i).key();
            labels[i] = new LabelNode();
        }

        appendView("switch (" + selector.source() + ") {");
        selector.emit(builder);
        builder.lookupSwitch(defaultLabel, keys, labels);
        flowScopes.push(new FlowScope(null, end));
        indent++;

        for (int i = 0; i < sortedCases.size(); i++)
        {
            appendView("case " + sortedCases.get(i).key() + ":");
            builder.label(labels[i]);
            indent++;
            sortedCases.get(i).body().accept(this);
            builder.goto_(end);
            indent--;
        }

        appendView("default:");
        builder.label(defaultLabel);
        indent++;
        if (defaultBlock != null)
        {
            defaultBlock.accept(this);
        }
        builder.goto_(end);
        indent--;
        flowScopes.pop();
        indent--;
        appendView("}");
        builder.label(end);
        return this;
    }

    /**
     * Emits a table switch for dense int ranges. cases[0] corresponds to min.
     */
    @SafeVarargs
    public final AdvInsnBuilder switchTable(
            Expr selector,
            int min,
            Consumer<AdvInsnBuilder> defaultBlock,
            Consumer<AdvInsnBuilder>... cases)
    {
        LabelNode defaultLabel = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode[] labels = new LabelNode[cases.length];
        for (int i = 0; i < labels.length; i++)
        {
            labels[i] = new LabelNode();
        }

        appendView("switch (" + selector.source() + ") {");
        selector.emit(builder);
        builder.tableSwitch(min, min + cases.length - 1, defaultLabel, labels);
        flowScopes.push(new FlowScope(null, end));
        indent++;

        for (int i = 0; i < cases.length; i++)
        {
            appendView("case " + (min + i) + ":");
            builder.label(labels[i]);
            indent++;
            cases[i].accept(this);
            builder.goto_(end);
            indent--;
        }

        appendView("default:");
        builder.label(defaultLabel);
        indent++;
        if (defaultBlock != null)
        {
            defaultBlock.accept(this);
        }
        builder.goto_(end);
        indent--;
        flowScopes.pop();
        indent--;
        appendView("}");
        builder.label(end);
        return this;
    }

    /**
     * Inserts a label at the current position and records its name in sourceView.
     */
    public LabelNode label(String name)
    {
        appendView("// label " + name);
        return builder.label();
    }

    /**
     * Jumps to an existing label.
     */
    public AdvInsnBuilder gotoLabel(LabelNode label)
    {
        appendView("// goto label");
        builder.goto_(label);
        return this;
    }

    /**
     * Inserts line-number debug metadata.
     */
    public AdvInsnBuilder line(int line)
    {
        LabelNode start = builder.label();
        builder.line(line, start);
        appendView("// line " + line);
        return this;
    }

    /**
     * Appends the current instructions into another InsnBuilder.
     */
    public AdvInsnBuilder writeTo(InsnBuilder target)
    {
        target.toInsnList().add(builder.toInsnList());
        return this;
    }

    /**
     * Returns the ASM InsnList generated so far.
     */
    public InsnList toInsnList()
    {
        return builder.toInsnList();
    }

    /**
     * Creates a constant expression: null, numbers, boolean, char, String, or ASM Type.
     */
    public static Expr constant(Object value)
    {
        if (value == null)
        {
            return new SimpleExpr(Type.getType(Object.class), InsnBuilder::aconstNull, "null");
        }

        if (value instanceof Boolean booleanValue)
        {
            return new SimpleExpr(Type.BOOLEAN_TYPE, ib -> ib.pushInt(booleanValue ? 1 : 0), Boolean.toString(booleanValue));
        }

        if (value instanceof Byte || value instanceof Short || value instanceof Integer)
        {
            int intValue = ((Number) value).intValue();
            return new SimpleExpr(Type.INT_TYPE, ib -> ib.pushInt(intValue), Integer.toString(intValue));
        }

        if (value instanceof Character character)
        {
            return new SimpleExpr(Type.CHAR_TYPE, ib -> ib.pushInt(character), "'" + character + "'");
        }

        if (value instanceof Long longValue)
        {
            return new SimpleExpr(Type.LONG_TYPE, ib -> ib.pushLong(longValue), longValue + "L");
        }

        if (value instanceof Float floatValue)
        {
            return new SimpleExpr(Type.FLOAT_TYPE, ib -> ib.pushFloat(floatValue), floatValue + "F");
        }

        if (value instanceof Double doubleValue)
        {
            return new SimpleExpr(Type.DOUBLE_TYPE, ib -> ib.pushDouble(doubleValue), doubleValue + "D");
        }

        if (value instanceof String stringValue)
        {
            return new SimpleExpr(Type.getType(String.class), ib -> ib.pushString(stringValue), AdvInsnSupport.quote(stringValue));
        }

        if (value instanceof Type typeValue)
        {
            return new SimpleExpr(Type.getType(Class.class), ib -> ib.pushClass(typeValue), AdvInsnSupport.simpleName(typeValue) + ".class");
        }

        throw new IllegalArgumentException("Unsupported constant: " + value.getClass().getName());
    }

    /**
     * Creates a null expression for the given reference type.
     */
    public static Expr nullValue(Class<?> type)
    {
        return nullValue(AdvInsnSupport.type(type));
    }

    /**
     * Creates a null expression for the given reference type string, internal name, or descriptor.
     */
    public static Expr nullValue(String type)
    {
        return nullValue(type(type));
    }

    /**
     * Creates a null expression for the given reference Type.
     */
    public static Expr nullValue(Type type)
    {
        AdvInsnSupport.requireReference(type, "null type");
        return new SimpleExpr(type, InsnBuilder::aconstNull, "null");
    }

    /**
     * Reads a Local as an expression.
     */
    public static Expr load(Local local)
    {
        return local;
    }

    /**
     * Creates an expression for an already-existing local slot.
     * <p>
     * This is useful when generating methods whose arguments and scratch locals
     * already have fixed indexes.
     */
    public static Local local(String name, Class<?> type, int index)
    {
        return local(name, AdvInsnSupport.type(type), index);
    }

    /**
     * Creates an expression for an already-existing local slot using a type string.
     */
    public static Local local(String name, String type, int index)
    {
        return local(name, type(type), index);
    }

    /**
     * Creates an expression for an already-existing local slot using an ASM Type.
     */
    public static Local local(String name, Type type, int index)
    {
        return new Local(name, type, index);
    }

    /**
     * Creates a this expression. owner may be an internal name or dotted class name.
     */
    public static Expr self(String owner)
    {
        return new SimpleExpr(object(owner), ib -> ib.aload(0), "this");
    }

    /**
     * Creates a this expression from a Class owner.
     */
    public static Expr self(Class<?> owner)
    {
        return self(AdvInsnSupport.type(owner).getInternalName());
    }

    /**
     * Addition expression.
     */
    public static Expr plus(Expr left, Expr right)
    {
        return math(left, right, MathOp.ADD);
    }

    /**
     * Subtraction expression.
     */
    public static Expr minus(Expr left, Expr right)
    {
        return math(left, right, MathOp.SUBTRACT);
    }

    /**
     * Multiplication expression.
     */
    public static Expr multiply(Expr left, Expr right)
    {
        return math(left, right, MathOp.MULTIPLY);
    }

    /**
     * Division expression.
     */
    public static Expr divide(Expr left, Expr right)
    {
        return math(left, right, MathOp.DIVIDE);
    }

    /**
     * Remainder expression.
     */
    public static Expr remainder(Expr left, Expr right)
    {
        return math(left, right, MathOp.REMAINDER);
    }

    /**
     * Bitwise AND expression.
     */
    public static Expr bitAnd(Expr left, Expr right)
    {
        return bitwise(left, right, MathOp.BITWISE_AND);
    }

    /**
     * Bitwise OR expression.
     */
    public static Expr bitOr(Expr left, Expr right)
    {
        return bitwise(left, right, MathOp.BITWISE_OR);
    }

    /**
     * Bitwise XOR expression.
     */
    public static Expr bitXor(Expr left, Expr right)
    {
        return bitwise(left, right, MathOp.BITWISE_XOR);
    }

    /**
     * Left-shift expression.
     */
    public static Expr shiftLeft(Expr left, Expr right)
    {
        return shift(left, right, ShiftOp.LEFT);
    }

    /**
     * Signed right-shift expression.
     */
    public static Expr shiftRight(Expr left, Expr right)
    {
        return shift(left, right, ShiftOp.RIGHT);
    }

    /**
     * Unsigned right-shift expression.
     */
    public static Expr unsignedShiftRight(Expr left, Expr right)
    {
        return shift(left, right, ShiftOp.UNSIGNED_RIGHT);
    }

    /**
     * Numeric negation expression.
     */
    public static Expr negative(Expr value)
    {
        Type type = AdvInsnSupport.numeric(value.type());
        return new SimpleExpr(type, ib -> {
            value.emit(ib);
            switch (type.getSort())
            {
                case Type.LONG -> ib.lneg();
                case Type.FLOAT -> ib.fneg();
                case Type.DOUBLE -> ib.dneg();
                default -> ib.ineg();
            }
        }, "-" + value.source());
    }

    /**
     * Bitwise NOT expression.
     */
    public static Expr bitNot(Expr value)
    {
        Type type = AdvInsnSupport.commonBitwise(value.type(), Type.INT_TYPE);
        return new SimpleExpr(type, ib -> {
            AdvInsnSupport.emitConverted(ib, value, type);
            if (type.getSort() == Type.LONG)
            {
                ib.pushLong(-1L);
                ib.lxor();
            }
            else
            {
                ib.pushInt(-1);
                ib.ixor();
            }
        }, "~" + value.source());
    }

    /**
     * Numeric conversion to int.
     */
    public static Expr toInt(Expr value)
    {
        return convert(value, Type.INT_TYPE, "int");
    }

    /**
     * Numeric conversion to long.
     */
    public static Expr toLong(Expr value)
    {
        return convert(value, Type.LONG_TYPE, "long");
    }

    /**
     * Numeric conversion to float.
     */
    public static Expr toFloat(Expr value)
    {
        return convert(value, Type.FLOAT_TYPE, "float");
    }

    /**
     * Numeric conversion to double.
     */
    public static Expr toDouble(Expr value)
    {
        return convert(value, Type.DOUBLE_TYPE, "double");
    }

    /**
     * Cast expression. Reference types emit CHECKCAST; numeric types emit conversion instructions.
     */
    public static Expr cast(Expr value, Class<?> to)
    {
        return cast(value, AdvInsnSupport.type(to));
    }

    /**
     * Cast expression using a type string.
     */
    public static Expr cast(Expr value, String to)
    {
        return cast(value, type(to));
    }

    /**
     * Cast expression using an ASM Type.
     */
    public static Expr cast(Expr value, Type to)
    {
        if (to.getSort() == Type.VOID)
        {
            throw new IllegalArgumentException("Cannot cast to void");
        }

        Type from = value.type();
        if (to.equals(from))
        {
            return value;
        }

        return new SimpleExpr(to, ib -> {
            value.emit(ib);

            if (AdvInsnSupport.isReference(to))
            {
                if (isBoxablePrimitive(from))
                {
                    emitBox(ib, from);
                }
                if (!to.equals(Type.getType(Object.class)))
                {
                    ib.checkCast(to.getInternalName());
                }
                return;
            }

            if (AdvInsnSupport.isReference(from))
            {
                TypeUtils.unbox(ib, to);
                return;
            }

            AdvInsnSupport.convert(from, to, ib);
        }, "((" + AdvInsnSupport.simpleName(to) + ") " + value.source() + ")");
    }

    /**
     * Unboxes or casts an Object expression into the requested type.
     */
    public static Expr unbox(Expr value, Type to)
    {
        if (to.getSort() == Type.VOID)
        {
            throw new IllegalArgumentException("Cannot unbox to void");
        }
        if (!AdvInsnSupport.isReference(value.type()))
        {
            return cast(value, to);
        }
        return new SimpleExpr(to, ib -> {
            value.emit(ib);
            TypeUtils.unbox(ib, to);
        }, "((" + AdvInsnSupport.simpleName(to) + ") " + value.source() + ")");
    }

    /**
     * Unboxes or casts an Object expression into the requested type string.
     */
    public static Expr unbox(Expr value, String to)
    {
        return unbox(value, type(to));
    }

    /**
     * instanceof expression. The result is boolean/int-like.
     */
    public static Expr instanceOf(Expr value, Class<?> type)
    {
        return instanceOf(value, AdvInsnSupport.type(type));
    }

    /**
     * instanceof expression using a type string.
     */
    public static Expr instanceOf(Expr value, String type)
    {
        return instanceOf(value, type(type));
    }

    /**
     * instanceof expression using an ASM Type.
     */
    public static Expr instanceOf(Expr value, Type type)
    {
        AdvInsnSupport.requireReference(value.type(), "instanceof value");
        AdvInsnSupport.requireReference(type, "instanceof type");
        return new SimpleExpr(Type.BOOLEAN_TYPE, ib -> {
            value.emit(ib);
            ib.instanceOf(type.getInternalName());
        }, value.source() + " instanceof " + AdvInsnSupport.simpleName(type));
    }

    /**
     * Creates an instance-field read/write target.
     */
    public static FieldAccess field(Expr owner, Class<?> ownerType, String name, Class<?> type)
    {
        return field(owner, AdvInsnSupport.type(ownerType), name, AdvInsnSupport.type(type));
    }

    /**
     * Creates an instance-field read/write target using string owner/type descriptors.
     */
    public static FieldAccess field(Expr owner, String ownerType, String name, String type)
    {
        return field(owner, object(ownerType), name, type(type));
    }

    /**
     * Creates an instance-field read/write target using ASM Types.
     */
    public static FieldAccess field(Expr owner, Type ownerType, String name, Type type)
    {
        return new FieldAccess(owner, ownerType, name, type);
    }

    /**
     * Creates an instance-field read/write target from a reusable FieldRef.
     */
    public static FieldAccess field(Expr owner, FieldRef field)
    {
        return field(owner, object(field.owner()), field.name(), type(field.descriptor()));
    }

    /**
     * Creates a static-field read/write target.
     */
    public static StaticFieldAccess staticField(Class<?> owner, String name, Class<?> type)
    {
        return staticField(AdvInsnSupport.type(owner), name, AdvInsnSupport.type(type));
    }

    /**
     * Creates a static-field read/write target using string owner/type descriptors.
     */
    public static StaticFieldAccess staticField(String owner, String name, String type)
    {
        return staticField(object(owner), name, type(type));
    }

    /**
     * Creates a static-field read/write target using ASM Types.
     */
    public static StaticFieldAccess staticField(Type owner, String name, Type type)
    {
        return new StaticFieldAccess(owner, name, type);
    }

    /**
     * Creates a static-field read/write target from a reusable FieldRef.
     */
    public static StaticFieldAccess staticField(FieldRef field)
    {
        return staticField(object(field.owner()), field.name(), type(field.descriptor()));
    }

    /**
     * Emits a normal virtual method-call expression.
     */
    public static Expr callVirtual(
            Expr owner,
            Class<?> ownerType,
            String name,
            Class<?> returnType,
            Expr... args)
    {
        return callVirtual(owner, AdvInsnSupport.type(ownerType), name, AdvInsnSupport.type(returnType), args);
    }

    /**
     * Emits a normal virtual method-call expression using type strings.
     */
    public static Expr callVirtual(
            Expr owner,
            String ownerType,
            String name,
            String returnType,
            Expr... args)
    {
        return callVirtual(owner, object(ownerType), name, type(returnType), args);
    }

    /**
     * Emits a normal virtual method-call expression using ASM Types.
     */
    public static Expr callVirtual(
            Expr owner,
            Type ownerType,
            String name,
            Type returnType,
            Expr... args)
    {
        return invoke(ownerType, name, returnType, InvokeKind.VIRTUAL, owner, args);
    }

    /**
     * Emits an interface method-call expression.
     */
    public static Expr callInterface(
            Expr owner,
            Class<?> ownerType,
            String name,
            Class<?> returnType,
            Expr... args)
    {
        return callInterface(owner, AdvInsnSupport.type(ownerType), name, AdvInsnSupport.type(returnType), args);
    }

    /**
     * Emits an interface method-call expression using type strings.
     */
    public static Expr callInterface(
            Expr owner,
            String ownerType,
            String name,
            String returnType,
            Expr... args)
    {
        return callInterface(owner, object(ownerType), name, type(returnType), args);
    }

    /**
     * Emits an interface method-call expression using ASM Types.
     */
    public static Expr callInterface(
            Expr owner,
            Type ownerType,
            String name,
            Type returnType,
            Expr... args)
    {
        return invoke(ownerType, name, returnType, InvokeKind.INTERFACE, owner, args);
    }

    /**
     * Emits a special method-call expression, usually for constructors, super calls, or private methods.
     */
    public static Expr callSpecial(
            Expr owner,
            Class<?> ownerType,
            String name,
            Class<?> returnType,
            Expr... args)
    {
        return callSpecial(owner, AdvInsnSupport.type(ownerType), name, AdvInsnSupport.type(returnType), args);
    }

    /**
     * Emits a special method-call expression using type strings.
     */
    public static Expr callSpecial(
            Expr owner,
            String ownerType,
            String name,
            String returnType,
            Expr... args)
    {
        return callSpecial(owner, object(ownerType), name, type(returnType), args);
    }

    /**
     * Emits a special method-call expression using ASM Types.
     */
    public static Expr callSpecial(
            Expr owner,
            Type ownerType,
            String name,
            Type returnType,
            Expr... args)
    {
        return invoke(ownerType, name, returnType, InvokeKind.SPECIAL, owner, args);
    }

    /**
     * Emits a static method-call expression.
     */
    public static Expr callStatic(Class<?> owner, String name, Class<?> returnType, Expr... args)
    {
        return callStatic(AdvInsnSupport.type(owner), name, AdvInsnSupport.type(returnType), args);
    }

    /**
     * Emits a static method-call expression using type strings.
     */
    public static Expr callStatic(String owner, String name, String returnType, Expr... args)
    {
        return callStatic(object(owner), name, type(returnType), args);
    }

    /**
     * Emits a static method-call expression using ASM Types.
     */
    public static Expr callStatic(Type owner, String name, Type returnType, Expr... args)
    {
        return invoke(owner, name, returnType, InvokeKind.STATIC, null, args);
    }

    /**
     * Emits a new-object expression, automatically writing NEW/DUP/INVOKESPECIAL <init>.
     */
    public static Expr newObject(Class<?> owner, Expr... args)
    {
        return newObject(AdvInsnSupport.type(owner), args);
    }

    /**
     * Emits a new-object expression with owner as an internal name or dotted class name.
     */
    public static Expr newObject(String owner, Expr... args)
    {
        return newObject(object(owner), args);
    }

    /**
     * Emits a new-object expression using an ASM Type owner.
     */
    public static Expr newObject(Type owner, Expr... args)
    {
        AdvInsnSupport.requireReference(owner, "object type");
        return new SimpleExpr(owner, ib -> {
            ib.new_(owner.getInternalName());
            ib.dup();
            AdvInsnSupport.emitArgs(ib, args);
            ib.invokeSpecial(owner.getInternalName(), "<init>", AdvInsnSupport.methodDescriptor(Type.VOID_TYPE, args));
        }, "new " + AdvInsnSupport.simpleName(owner) + "(" + AdvInsnSupport.joinArgs(args) + ")");
    }

    /**
     * Emits a one-dimensional array creation expression.
     */
    public static Expr newArray(Class<?> elementType, Expr length)
    {
        return newArray(AdvInsnSupport.type(elementType), length);
    }

    /**
     * Emits a one-dimensional array creation expression using a string element type.
     */
    public static Expr newArray(String elementType, Expr length)
    {
        return newArray(type(elementType), length);
    }

    /**
     * Emits a one-dimensional array creation expression using an ASM Type element type.
     */
    public static Expr newArray(Type elementType, Expr length)
    {
        Type arrayType = Type.getType("[" + elementType.getDescriptor());
        return new SimpleExpr(arrayType, ib -> {
            length.emit(ib);
            if (AdvInsnSupport.isReference(elementType))
            {
                ib.aneArray(elementType.getInternalName());
            }
            else
            {
                ib.newArray(AdvInsnSupport.primitiveArrayCode(elementType));
            }
        }, "new " + AdvInsnSupport.simpleName(elementType) + "[" + length.source() + "]");
    }

    /**
     * Emits a multi-dimensional array creation expression using MULTIANEWARRAY.
     */
    public static Expr newMultiArray(Type arrayType, int dimensions, Expr... lengths)
    {
        if (arrayType.getSort() != Type.ARRAY)
        {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        if (dimensions <= 0 || dimensions != lengths.length)
        {
            throw new IllegalArgumentException("Invalid dimensions: " + dimensions);
        }
        return new SimpleExpr(arrayType, ib -> {
            AdvInsnSupport.emitArgs(ib, lengths);
            ib.multiANewArray(arrayType.getDescriptor(), dimensions);
        }, "new " + arrayType.getElementType().getClassName() + "[" + AdvInsnSupport.joinArgs(lengths) + "]");
    }

    /**
     * Emits a multi-dimensional array creation expression using an array descriptor, for example {@code "[[I"}.
     */
    public static Expr newMultiArray(String arrayType, int dimensions, Expr... lengths)
    {
        return newMultiArray(type(arrayType), dimensions, lengths);
    }

    /**
     * Reads an array element, for example {@code arr[index]}.
     */
    public static Expr arrayAt(Expr array, Expr index)
    {
        Type arrayType = array.type();
        if (arrayType.getSort() != Type.ARRAY)
        {
            throw new IllegalArgumentException("Not an array: " + arrayType);
        }
        return new SimpleExpr(arrayType.getElementType(), ib -> {
            array.emit(ib);
            index.emit(ib);
            emitArrayLoad(ib, arrayType.getElementType());
        }, array.source() + "[" + index.source() + "]");
    }

    /**
     * Reads an array length, for example {@code arr.length}.
     */
    public static Expr arrayLength(Expr array)
    {
        if (array.type().getSort() != Type.ARRAY)
        {
            throw new IllegalArgumentException("Not an array: " + array.type());
        }
        return new SimpleExpr(Type.INT_TYPE, ib -> {
            array.emit(ib);
            ib.arrayLength();
        }, array.source() + ".length");
    }

    /**
     * Treats an int-like/boolean expression as a condition; non-zero means true.
     */
    public static Condition isTrue(Expr value)
    {
        return new BooleanCondition(autoUnboxIfWrapper(value), false);
    }

    /**
     * Treats an int-like/boolean expression as an inverted condition; zero means true.
     */
    public static Condition isFalse(Expr value)
    {
        return new BooleanCondition(autoUnboxIfWrapper(value), true);
    }

    /**
     * Checks whether a reference is null.
     */
    public static Condition isNull(Expr value)
    {
        AdvInsnSupport.requireReference(value.type(), "null check");
        return new SimpleCondition(value.source() + " == null", (ib, falseLabel) -> {
            value.emit(ib);
            ib.ifNonNull(falseLabel);
        });
    }

    /**
     * Checks whether a reference is not null.
     */
    public static Condition notNull(Expr value)
    {
        AdvInsnSupport.requireReference(value.type(), "null check");
        return new SimpleCondition(value.source() + " != null", (ib, falseLabel) -> {
            value.emit(ib);
            ib.ifNull(falseLabel);
        });
    }

    /**
     * instanceof condition.
     */
    public static Condition isInstanceOf(Expr value, Class<?> type)
    {
        return isTrue(instanceOf(value, type));
    }

    /**
     * instanceof condition using a type string.
     */
    public static Condition isInstanceOf(Expr value, String type)
    {
        return isTrue(instanceOf(value, type));
    }

    /**
     * Equality condition.
     */
    public static Condition equal(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.EQUAL);
    }

    /**
     * Inequality condition.
     */
    public static Condition notEqual(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.NOT_EQUAL);
    }

    /**
     * Less-than condition.
     */
    public static Condition lessThan(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.LESS_THAN);
    }

    /**
     * Less-than-or-equal condition.
     */
    public static Condition lessOrEqual(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.LESS_OR_EQUAL);
    }

    /**
     * Greater-than condition.
     */
    public static Condition greaterThan(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.GREATER_THAN);
    }

    /**
     * Greater-than-or-equal condition.
     */
    public static Condition greaterOrEqual(Expr left, Expr right)
    {
        return compare(left, right, CompareOp.GREATER_OR_EQUAL);
    }

    /**
     * Logical NOT condition.
     */
    public static Condition not(Condition condition)
    {
        return new SimpleCondition("!(" + condition.source() + ")", condition::jumpIfTrue);
    }

    /**
     * Short-circuit logical AND condition.
     */
    public static Condition and(Condition left, Condition right)
    {
        return new SimpleCondition("(" + left.source() + " && " + right.source() + ")", (ib, falseLabel) -> {
            left.jumpIfFalse(ib, falseLabel);
            right.jumpIfFalse(ib, falseLabel);
        });
    }

    /**
     * Short-circuit logical OR condition.
     */
    public static Condition or(Condition left, Condition right)
    {
        return new SimpleCondition("(" + left.source() + " || " + right.source() + ")", (ib, falseLabel) -> {
            LabelNode trueLabel = new LabelNode();
            left.jumpIfTrue(ib, trueLabel);
            right.jumpIfFalse(ib, falseLabel);
            ib.label(trueLabel);
        });
    }

    /**
     * Converts a Class to an ASM Type.
     */
    public static Type type(Class<?> type)
    {
        return AdvInsnSupport.type(type);
    }

    /**
     * Converts a string to an ASM Type.
     * Supports primitive names/descriptors, object internal names, object descriptors, and array descriptors.
     */
    public static Type type(String type)
    {
        return switch (type)
        {
            case "void", "V" -> Type.VOID_TYPE;
            case "boolean", "Z" -> Type.BOOLEAN_TYPE;
            case "byte", "B" -> Type.BYTE_TYPE;
            case "char", "C" -> Type.CHAR_TYPE;
            case "short", "S" -> Type.SHORT_TYPE;
            case "int", "I" -> Type.INT_TYPE;
            case "long", "J" -> Type.LONG_TYPE;
            case "float", "F" -> Type.FLOAT_TYPE;
            case "double", "D" -> Type.DOUBLE_TYPE;
            default -> {
                if (type.startsWith("[") || type.startsWith("L"))
                {
                    yield Type.getType(type);
                }
                yield object(type);
            }
        };
    }

    /**
     * Converts an object internal name or dotted class name to an ASM object Type.
     */
    public static Type object(String internalName)
    {
        return Type.getObjectType(internalName.replace('.', '/'));
    }

    /**
     * Shared low-level implementation for all method-call expressions.
     */
    private static Expr invoke(
            Type owner,
            String name,
            Type returnType,
            InvokeKind kind,
            Expr receiver,
            Expr... args)
    {
        return new SimpleExpr(returnType, ib -> {
            if (kind != InvokeKind.STATIC)
            {
                receiver.emit(ib);
            }
            AdvInsnSupport.emitArgs(ib, args);
            String desc = AdvInsnSupport.methodDescriptor(returnType, args);
            switch (kind)
            {
                case STATIC -> ib.invokeStatic(owner.getInternalName(), name, desc);
                case SPECIAL -> ib.invokeSpecial(owner.getInternalName(), name, desc);
                case INTERFACE -> ib.invokeInterface(owner.getInternalName(), name, desc);
                case VIRTUAL -> ib.invokeVirtual(owner.getInternalName(), name, desc);
            }
        }, (kind == InvokeKind.STATIC ? AdvInsnSupport.simpleName(owner) : receiver.source()) +
           "." + name + "(" + AdvInsnSupport.joinArgs(args) + ")");
    }

    /**
     * Shared low-level implementation for numeric arithmetic expressions.
     */
    private static Expr math(Expr left, Expr right, MathOp op)
    {
        Type type = AdvInsnSupport.commonNumeric(left.type(), right.type());
        return new SimpleExpr(type, ib -> {
            AdvInsnSupport.emitConverted(ib, left, type);
            AdvInsnSupport.emitConverted(ib, right, type);
            op.emit(ib, type);
        }, "(" + left.source() + " " + op.symbol + " " + right.source() + ")");
    }

    /**
     * Shared low-level implementation for bitwise expressions.
     */
    private static Expr bitwise(Expr left, Expr right, MathOp op)
    {
        Type type = AdvInsnSupport.commonBitwise(left.type(), right.type());
        return new SimpleExpr(type, ib -> {
            AdvInsnSupport.emitConverted(ib, left, type);
            AdvInsnSupport.emitConverted(ib, right, type);
            op.emit(ib, type);
        }, "(" + left.source() + " " + op.symbol + " " + right.source() + ")");
    }

    /**
     * Shared low-level implementation for shift expressions.
     */
    private static Expr shift(Expr left, Expr right, ShiftOp op)
    {
        Type leftType = AdvInsnSupport.numeric(left.type());
        if (leftType.getSort() != Type.INT && leftType.getSort() != Type.LONG)
        {
            throw new IllegalArgumentException("Shift left side must be int-like or long: " + left.type());
        }
        return new SimpleExpr(leftType, ib -> {
            AdvInsnSupport.emitConverted(ib, left, leftType);
            AdvInsnSupport.emitConverted(ib, right, Type.INT_TYPE);
            op.emit(ib, leftType);
        }, "(" + left.source() + " " + op.symbol + " " + right.source() + ")");
    }

    /**
     * Shared low-level implementation for numeric conversion expressions.
     */
    private static Expr convert(Expr value, Type type, String display)
    {
        return new SimpleExpr(type, ib -> AdvInsnSupport.emitConverted(ib, value, type),
                              "((" + display + ") " + value.source() + ")");
    }

    /**
     * Automatically adapts expression values for assignment-like contexts.
     * <p>
     * Supports:
     * <ul>
     *     <li>primitive numeric conversion, for example int -> long or int -> float</li>
     *     <li>primitive boxing, for example int -> Integer/Object/Number</li>
     * </ul>
     */
    private static Expr autoCastIfNeeded(Type targetType, Expr value)
    {
        Type valueType = value.type();

        if (targetType.equals(valueType))
        {
            return value;
        }

        if (targetType.getSort() == Type.VOID)
        {
            return value;
        }

        if (!AdvInsnSupport.isReference(targetType))
        {
            if (AdvInsnSupport.isReference(valueType))
            {
                return new SimpleExpr(targetType, ib -> {
                    value.emit(ib);
                    TypeUtils.unbox(ib, targetType);
                }, "((" + AdvInsnSupport.simpleName(targetType) + ") " + value.source() + ")");
            }

            if (shouldAutoPrimitiveCast(targetType, valueType))
            {
                return new SimpleExpr(targetType, ib -> {
                    value.emit(ib);
                    AdvInsnSupport.convert(valueType, targetType, ib);
                }, "((" + AdvInsnSupport.simpleName(targetType) + ") " + value.source() + ")");
            }

            return value;
        }

        if (shouldAutoBox(targetType, valueType))
        {
            Type boxedType = wrapperType(valueType);
            Type exposedType = canReceiveBoxedPrimitive(targetType, valueType) ? targetType : boxedType;
            return new SimpleExpr(exposedType, ib -> {
                value.emit(ib);
                emitBox(ib, valueType);
                if (!targetType.equals(Type.getType(Object.class))
                    && !targetType.equals(boxedType)
                    && !canReceiveBoxedPrimitive(targetType, valueType))
                {
                    ib.checkCast(targetType.getInternalName());
                }
            }, wrapperSimpleName(valueType) + ".valueOf(" + value.source() + ")");
        }

        if (AdvInsnSupport.isReference(valueType))
        {
            if (canReceiveReference(targetType, valueType))
            {
                return new SimpleExpr(targetType, value::emit, value.source());
            }
            return new SimpleExpr(targetType, ib -> {
                value.emit(ib);
                ib.checkCast(targetType.getInternalName());
            }, "((" + AdvInsnSupport.simpleName(targetType) + ") " + value.source() + ")");
        }

        return value;
    }

    /**
     * Returns true when a primitive value can be converted to another primitive value.
     * This intentionally excludes boolean because boolean is not numeric in JVM conversions.
     */
    private static boolean shouldAutoPrimitiveCast(Type targetType, Type valueType)
    {
        return isNumericPrimitive(targetType)
               && isNumericPrimitive(valueType)
               && !targetType.equals(valueType);
    }

    /**
     * Returns true for primitive numeric values, excluding boolean and void.
     */
    private static boolean isNumericPrimitive(Type type)
    {
        return switch (type.getSort())
        {
            case Type.BYTE, Type.CHAR, Type.SHORT, Type.INT,
                 Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            default -> false;
        };
    }

    /**
     * Returns true when a primitive value should become its wrapper for a reference target.
     */
    private static boolean shouldAutoBox(Type targetType, Type valueType)
    {
        return AdvInsnSupport.isReference(targetType)
               && isBoxablePrimitive(valueType)
               && canReceiveBoxedPrimitive(targetType, valueType);
    }

    /**
     * Emits Xxx.valueOf(...) for a primitive already on the operand stack.
     */
    private static void emitBox(InsnBuilder ib, Type primitiveType)
    {
        switch (primitiveType.getSort())
        {
            case Type.BOOLEAN -> ib.invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            case Type.BYTE -> ib.invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
            case Type.CHAR -> ib.invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
            case Type.SHORT -> ib.invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
            case Type.INT -> ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            case Type.LONG -> ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            case Type.FLOAT -> ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
            case Type.DOUBLE -> ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
            default -> throw new IllegalArgumentException("Not a boxable primitive: " + primitiveType);
        }
    }

    /**
     * Maps a primitive type to its wrapper object type.
     */
    private static Type wrapperType(Type primitiveType)
    {
        return switch (primitiveType.getSort())
        {
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.INT -> Type.getType(Integer.class);
            case Type.LONG -> Type.getType(Long.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            default -> throw new IllegalArgumentException("Not a boxable primitive: " + primitiveType);
        };
    }

    /**
     * Returns true for primitive values that Java can autobox.
     */
    private static boolean isBoxablePrimitive(Type type)
    {
        return switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT,
                 Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            default -> false;
        };
    }

    /**
     * Lightweight assignability check for primitive wrappers.
     * This avoids classpath hierarchy analysis while covering the common cases:
     * Object, Number, Comparable, Serializable, and the exact wrapper type.
     */
    private static boolean canReceiveBoxedPrimitive(Type targetType, Type primitiveType)
    {
        if (!AdvInsnSupport.isReference(targetType))
        {
            return false;
        }

        if (targetType.equals(Type.getType(Object.class)))
        {
            return true;
        }

        if (targetType.equals(Type.getType(java.io.Serializable.class))
            || targetType.equals(Type.getType(Comparable.class)))
        {
            return true;
        }

        Type wrapper = wrapperType(primitiveType);
        if (targetType.equals(wrapper))
        {
            return true;
        }

        return targetType.equals(Type.getType(Number.class))
               && primitiveType.getSort() != Type.BOOLEAN
               && primitiveType.getSort() != Type.CHAR;
    }

    private static String wrapperSimpleName(Type primitiveType)
    {
        return switch (primitiveType.getSort())
        {
            case Type.BOOLEAN -> "Boolean";
            case Type.BYTE -> "Byte";
            case Type.CHAR -> "Character";
            case Type.SHORT -> "Short";
            case Type.INT -> "Integer";
            case Type.LONG -> "Long";
            case Type.FLOAT -> "Float";
            case Type.DOUBLE -> "Double";
            default -> throw new IllegalArgumentException("Not a boxable primitive: " + primitiveType);
        };
    }

    private static boolean canReceiveReference(Type targetType, Type valueType)
    {
        if (!AdvInsnSupport.isReference(targetType) || !AdvInsnSupport.isReference(valueType))
        {
            return false;
        }
        if (targetType.equals(valueType) || targetType.equals(Type.getType(Object.class)))
        {
            return true;
        }
        Type primitive = primitiveOfWrapper(valueType);
        if (primitive != null)
        {
            return canReceiveBoxedPrimitive(targetType, primitive);
        }
        return false;
    }

    private static Type primitiveOfWrapper(Type wrapperType)
    {
        if (wrapperType.equals(Type.getType(Boolean.class))) return Type.BOOLEAN_TYPE;
        if (wrapperType.equals(Type.getType(Byte.class))) return Type.BYTE_TYPE;
        if (wrapperType.equals(Type.getType(Character.class))) return Type.CHAR_TYPE;
        if (wrapperType.equals(Type.getType(Short.class))) return Type.SHORT_TYPE;
        if (wrapperType.equals(Type.getType(Integer.class))) return Type.INT_TYPE;
        if (wrapperType.equals(Type.getType(Long.class))) return Type.LONG_TYPE;
        if (wrapperType.equals(Type.getType(Float.class))) return Type.FLOAT_TYPE;
        if (wrapperType.equals(Type.getType(Double.class))) return Type.DOUBLE_TYPE;
        return null;
    }

    private static boolean isWrapper(Type type)
    {
        return primitiveOfWrapper(type) != null;
    }

    private static Expr autoUnboxIfWrapper(Expr value)
    {
        Type primitive = primitiveOfWrapper(value.type());
        return primitive == null ? value : unbox(value, primitive);
    }

    private static Expr adaptForCompare(Expr value, Type otherType, CompareOp op)
    {
        if (AdvInsnSupport.isReference(value.type()) && !AdvInsnSupport.isReference(otherType))
        {
            return autoCastIfNeeded(otherType, value);
        }

        if (isWrapper(value.type()) && isWrapper(otherType) && op != CompareOp.EQUAL && op != CompareOp.NOT_EQUAL)
        {
            return autoUnboxIfWrapper(value);
        }

        return value;
    }

    /**
     * Shared low-level implementation for comparison conditions.
     */
    private static Condition compare(Expr left, Expr right, CompareOp op)
    {
        left = adaptForCompare(left, right.type(), op);
        right = adaptForCompare(right, left.type(), op);

        boolean leftReference = AdvInsnSupport.isReference(left.type());
        boolean rightReference = AdvInsnSupport.isReference(right.type());

        if (leftReference || rightReference)
        {
            if (leftReference && rightReference)
            {
                if (op != CompareOp.EQUAL && op != CompareOp.NOT_EQUAL)
                {
                    left = autoUnboxIfWrapper(left);
                    right = autoUnboxIfWrapper(right);
                    if (!AdvInsnSupport.isReference(left.type()) && !AdvInsnSupport.isReference(right.type()))
                    {
                        return compare(left, right, op);
                    }
                    throw new IllegalArgumentException("Reference values only support equal/notEqual");
                }

                Expr finalLeft = left;
                Expr finalRight = right;
                return new SimpleCondition(finalLeft.source() + " " + op.symbol + " " + finalRight.source(), (ib, falseLabel) -> {
                    finalLeft.emit(ib);
                    finalRight.emit(ib);
                    if (op == CompareOp.EQUAL)
                    {
                        ib.ifAcmpNe(falseLabel);
                    }
                    else
                    {
                        ib.ifAcmpEq(falseLabel);
                    }
                });
            }

            throw new IllegalArgumentException("Cannot compare primitive with reference: " + left.type() + " and " + right.type());
        }

        if (AdvInsnSupport.isIntLike(left.type()) && AdvInsnSupport.isIntLike(right.type()))
        {
            Expr finalLeft = left;
            Expr finalRight = right;
            return new SimpleCondition(finalLeft.source() + " " + op.symbol + " " + finalRight.source(), (ib, falseLabel) -> {
                finalLeft.emit(ib);
                finalRight.emit(ib);
                op.emitIntFalseJump(ib, falseLabel);
            });
        }

        Type type = AdvInsnSupport.commonNumeric(left.type(), right.type());
        Expr finalLeft = left;
        Expr finalRight = right;
        return new SimpleCondition(finalLeft.source() + " " + op.symbol + " " + finalRight.source(), (ib, falseLabel) -> {
            AdvInsnSupport.emitConverted(ib, finalLeft, type);
            AdvInsnSupport.emitConverted(ib, finalRight, type);

            switch (type.getSort())
            {
                case Type.LONG -> ib.lcmp();
                case Type.FLOAT -> ib.fcmpl();
                case Type.DOUBLE -> ib.dcmpl();
                default -> {
                    op.emitIntFalseJump(ib, falseLabel);
                    return;
                }
            }

            op.emitZeroFalseJump(ib, falseLabel);
        });
    }

    /**
     * Ensures this builder is attached to a MethodNode for features stored outside the instruction list.
     */
    private void requireMethod(String feature)
    {
        if (method == null)
        {
            throw new IllegalStateException(feature + " requires AdvInsnBuilder.into(MethodNode)");
        }
    }

    /**
     * Converts a catch type string to the internal name ASM expects in TryCatchBlockNode.
     */
    private static String catchType(String exceptionType)
    {
        return exceptionType == null ? null : type(exceptionType).getInternalName();
    }

    /**
     * Stores or drops the exception object that the JVM places on the handler stack.
     */
    private void storeCaughtException(CatchBlock catchBlock)
    {
        String localName = catchBlock.localName();
        Type exceptionType = catchBlock.exceptionType() == null
                ? object("java/lang/Throwable")
                : type(catchBlock.exceptionType());

        if (localName == null || localName.isBlank())
        {
            builder.pop();
            return;
        }

        Local local = var(localName, exceptionType);
        AdvInsnSupport.store(builder, local.type(), local.index());
    }

    /**
     * Returns the nearest scope that can handle break.
     */
    private FlowScope requireBreakScope()
    {
        for (FlowScope scope : flowScopes)
        {
            if (scope.breakLabel() != null)
            {
                return scope;
            }
        }
        throw new IllegalStateException("break is not inside a loop or switch");
    }

    /**
     * Returns the nearest loop scope that can handle continue.
     */
    private FlowScope requireContinueScope()
    {
        for (FlowScope scope : flowScopes)
        {
            if (scope.continueLabel() != null)
            {
                return scope;
            }
        }
        throw new IllegalStateException("continue is not inside a loop");
    }

    /**
     * Chooses the correct xASTORE instruction for an array element type.
     */
    private void emitArrayStore(Type elementType)
    {
        switch (elementType.getSort())
        {
            case Type.LONG -> builder.lastore();
            case Type.FLOAT -> builder.fastore();
            case Type.DOUBLE -> builder.dastore();
            case Type.OBJECT, Type.ARRAY -> builder.aastore();
            case Type.BYTE, Type.BOOLEAN -> builder.bastore();
            case Type.CHAR -> builder.castore();
            case Type.SHORT -> builder.sastore();
            default -> builder.iastore();
        }
    }

    /**
     * Chooses the correct xALOAD instruction for an array element type.
     */
    private static void emitArrayLoad(InsnBuilder ib, Type elementType)
    {
        switch (elementType.getSort())
        {
            case Type.LONG -> ib.laload();
            case Type.FLOAT -> ib.faload();
            case Type.DOUBLE -> ib.daload();
            case Type.OBJECT, Type.ARRAY -> ib.aaload();
            case Type.BYTE, Type.BOOLEAN -> ib.baload();
            case Type.CHAR -> ib.caload();
            case Type.SHORT -> ib.saload();
            default -> ib.iaload();
        }
    }

    /**
     * Appends one line to sourceView using the current block indentation.
     */
    private void appendView(String line)
    {
        sourceView.append("    ".repeat(indent)).append(line).append(System.lineSeparator());
    }

    /**
     * Maintains conservative maxLocals/maxStack values when a MethodNode is bound.
     */
    private void updateMaxLocals(int locals)
    {
        if (method != null)
        {
            method.maxLocals = Math.max(method.maxLocals, locals);
            method.maxStack = Math.max(method.maxStack, maxStackHint);
        }
    }

    /**
     * Computes the first automatically allocated local slot from static-ness and argument types.
     */
    private static int initialLocal(MethodNode method)
    {
        int local = MethodUtils.isStatic(method) ? 0 : 1;
        for (Type arg : Type.getArgumentTypes(method.desc))
        {
            local += AdvInsnSupport.slotSize(arg);
        }
        return local;
    }

    /**
     * Method-call kind, matching the four JVM invoke instructions.
     */
    private enum InvokeKind
    {
        VIRTUAL,
        STATIC,
        SPECIAL,
        INTERFACE
    }
}
