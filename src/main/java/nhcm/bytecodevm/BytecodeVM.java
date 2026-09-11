package nhcm.bytecodevm;

import nhcm.bytecodevm.cli.BytecodeVMCLI;
import nhcm.bytecodevm.utils.LogColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

public class BytecodeVM
{
    private static final Logger logger = LoggerFactory.getLogger(BytecodeVM.class);
    private static final AtomicBoolean terminating = new AtomicBoolean(false);

    private static final String defaultConfig = """
            # Relative paths are resolved from the current working directory.
            input: ./input.jar
            output: ./output.jar

            # VM allocation and placement.
            # createMode: ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
            createMode: ONE_FOR_ALL
            # location: SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
            location: ONE_PACKAGE
            # renameMode renames generated VM artifacts only.
            renameMode: DISABLE
            # interpretMode: SAVE_ALL_INSTRUCTION, SAVE_ONLY_REQUIRED_INSTRUCTION
            interpretMode: SAVE_ONLY_REQUIRED_INSTRUCTION
            # Automatic tiers:
            # Tiers rank analysis resistance, not runtime speed. Use LOW or a
            # concrete structure for latency-sensitive and heavily executed methods.
            #   LOW    -> SIMPLE_DISPATCH, DISTRIBUTED_DISPATCH, MULTIPLE_DISPATCH,
            #             THREADED_DIRECT, THREADED_INDIRECT
            #   MEDIUM_LOW -> all LOW and MEDIUM structures
            #   MEDIUM -> CALL_THREADED, RECURSIVE, CONTINUATION_PASSING, OBJECT,
            #             SELF_MODIFYING, EVENT, COROUTINE
            #   MEDIUM_HIGH -> all MEDIUM and HIGH structures
            #   HIGH   -> DATA_FLOW, POLYMORPHIC, GRAPH, FSM, REGISTER_BASED
            #   ANY    -> all concrete VM structures
            # Concrete VM structures:
            #   SIMPLE_DISPATCH, DISTRIBUTED_DISPATCH, MULTIPLE_DISPATCH,
            #   THREADED_DIRECT, THREADED_INDIRECT, CALL_THREADED, RECURSIVE,
            #   CONTINUATION_PASSING, OBJECT, POLYMORPHIC, SELF_MODIFYING,
            #   REGISTER_BASED, DATA_FLOW, GRAPH, FSM, EVENT, COROUTINE
            vmStructure: HIGH
            vmCount: 5

            # CodePool, bytecode encoding, and virtual control flow protection.
            protectCodePool: true
            # Dynamically decrypts constants using the current VM execution state.
            dynamicConstantDecrypt: true
            virtualizeInstructionAddresses: true
            encryptOperands: true
            perMethodOpcodeMap: true
            shuffleConstants: true
            bindConstantsToOperands: true
            splitCodeStreams: true
            shuffleInstructionBlocks: true
            obfuscateDispatch: true
            dynamicCodePoolBuild: true
            dynamicStateKey: true
            virtualControlFlowGraph: true

            # Input transformations and call-graph expansion.
            constantFix: true
            # Rewrites literal strings and primitive numbers into per-site encrypted data
            # that is reconstructed by the code before that code is virtualized.
            preEncryptStrings: true
            preEncryptNumbers: true

            # UNSAFE: Member inlining can break reflection, serialization, frameworks, and external callers.
            # Enable it per match group or SDK field annotation only when every use is under your control.
            # Selected fields of any JVM type are removed; crypto and storage access are expanded at each GET/PUT.
            # Object and array identity is preserved through randomized encrypted handles in a reference vault.
            # Instance owners use weak identity keys and a ReferenceQueue so FieldStore does not retain dead objects.
            # Selected protected methods keep their original signature as a direct VM entry without bridge classes.
            inlineFields: true
            inlineCalledProtectedMethods: true
            inlineStaticFinals: true
            # Keeps the mandatory super()/this() prefix in <init>, then moves the complete
            # initialized-this constructor body into a private method that is always virtualized.
            virtualizeConstructors: true
            # When enabled, unsafe transforms require @InlineField, @InlineFinal,
            # or a method-level @Virtualize annotation. Disabled by default for maximum coverage.
            annotationOnly: false
            # When enabled, config-selected field inlining is limited to private fields.
            privateFieldOnly: false
            # When enabled, only private protected methods receive direct VM entry handling;
            # keep disabled to include package, protected, and public call sites as well.
            ignorePublicCalls: false
            # Automatically protect eligible methods that reference members selected for removal.
            includeReferencedMethods: true

            # Removes BytecodeVM SDK annotations from the output JAR.
            removeAnnotations: true
            # Custom watermark fields. Leave empty to use the built-in default watermark.
            # Required tracking fields such as protection time, artifact ID, version, and
            # input SHA-256 are always added automatically.
            watermark: {}
            includeMethodsCalledWithin: false
            excludeMethodsCalledWithin: false
            virtualizeInvocationBridges: true

            # Integrity protection. Set recheck interval to 0 to disable runtime sampling.
            vmIntegrityCheck: true
            vmIntegrityCheckRatio: 1.0
            vmIntegrityRecheckInterval: 65536

            # SuperInstruction fusion.
            superInstruction: true
            superInstructionCombineRange: [2, 5]
            # superInstructionMode: RANDOM, PATTERN, HYBRID
            superInstructionMode: HYBRID
            superInstructionMaxHandlers: 128
            superInstructionMinFrequency: 2

            # Interpret-branch decoys and dynamic branch selection.
            obfuscateInterpretBranch: true
            interpretBranchCases: 3

            # Rules are evaluated from top to bottom. Repeat includes/excludes to re-include or
            # re-exclude a narrower target; the last matching rule wins.
            # class: is the class gate: excluded or unmatched classes are never processed.
            # It does not select members; field:/method: must also select each target kind.
            # A combined class:...;field:/method:... rule performs both decisions at once.
            # Member forms use normal Java types; '*' matches any text or descriptor fragment.
            # Run `inspect <config.yml>` to preview include matches and VM allocation.
            includes:
              - "class:*"
              - "class:*;field:*"
              - "class:*;method:*(*)*"
            excludes: []
            """;

    private static final String asciiArt = """
            ██████╗ ██╗   ██╗████████╗███████╗ ██████╗ ██████╗ ██████╗ ███████╗██╗   ██╗███╗   ███╗
            ██╔══██╗╚██╗ ██╔╝╚══██╔══╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝██║   ██║████╗ ████║
            ██████╔╝ ╚████╔╝    ██║   █████╗  ██║     ██║   ██║██║  ██║█████╗  ██║   ██║██╔████╔██║
            ██╔══██╗  ╚██╔╝     ██║   ██╔══╝  ██║     ██║   ██║██║  ██║██╔══╝  ╚██╗ ██╔╝██║╚██╔╝██║
            ██████╔╝   ██║      ██║   ███████╗╚██████╗╚██████╔╝██████╔╝███████╗ ╚████╔╝ ██║ ╚═╝ ██║
            ╚═════╝    ╚═╝      ╚═╝   ╚══════╝ ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝  ╚═══╝  ╚═╝     ╚═╝
            
            (Credit to GPT 5.5 and GPT 5.6)
            By NHCM, Version %s
            
            MUST READ:
            This obfuscator is intended for demonstration purposes only and is not suitable for production use.
            It can make your program hundreds of times slower, while the quality of its protection is not guaranteed.
            This obfuscator may not even provide protection comparable to existing virtualization tools such as V*P or The*ida, or even basic bytecode-to-native obfuscation tools such as JN*C.
            Its purpose is to demonstrate the concept of bytecode virtualization.
            """.formatted(BuildInfo.VERSION);

    public static void main(String[] args)
    {
        installTerminationHandlers();
        System.out.println(asciiArt);
        if (args.length == 0 || containsArgument(args, "-h", "--help"))
        {
            logger.info("{}", LogColors.lifecycle("Printing usage"));
        }
        else if (containsArgument(args, "-V", "--version"))
        {
            logger.info("{}", LogColors.lifecycle("Printing version"));
        }
        int exitCode = BytecodeVMCLI.execute(args);
        if(exitCode != 0)
        {
            System.exit(exitCode);
        }
    }

    public static String defaultConfig()
    {
        return defaultConfig;
    }

    private static boolean containsArgument(String[] args, String shortName, String longName)
    {
        for (String argument : args)
        {
            if (shortName.equals(argument) || longName.equals(argument))
            {
                return true;
            }
        }
        return false;
    }

    private static void installTerminationHandlers()
    {
        registerSignalHandler("INT", 130, "Ctrl+C");
        registerSignalHandler("TERM", 143, "termination signal");
    }

    private static void registerSignalHandler(String signalName, int exitCode, String reason)
    {
        try
        {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            Object signal = signalClass.getConstructor(String.class).newInstance(signalName);
            InvocationHandler handler = (proxy, method, args) ->
            {
                if ("handle".equals(method.getName()))
                {
                    terminate(exitCode, reason);
                    return null;
                }
                if ("toString".equals(method.getName()))
                {
                    return "BytecodeVM " + signalName + " handler";
                }
                if ("hashCode".equals(method.getName()))
                {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName()))
                {
                    return proxy == args[0];
                }
                return null;
            };
            Object signalHandler = Proxy.newProxyInstance(
                    BytecodeVM.class.getClassLoader(),
                    new Class<?>[]{signalHandlerClass},
                    handler);
            signalClass
                    .getMethod("handle", signalClass, signalHandlerClass)
                    .invoke(null, signal, signalHandler);
        }
        catch (ReflectiveOperationException | LinkageError | SecurityException ignored)
        {
            // If sun.misc.Signal is unavailable, the JVM default signal behavior still exits.
        }
    }

    private static void terminate(int exitCode, String reason)
    {
        if (terminating.compareAndSet(false, true))
        {
            System.err.print("\r\u001B[2K\r");
            System.err.println("BytecodeVM interrupted by " + reason + ", exiting.");
            System.err.flush();
        }
        Runtime.getRuntime().halt(exitCode);
    }
}
