# BytecodeVM

(Credit to GPT-5.5 and GPT 5.6)

BytecodeVM is a Java bytecode virtualizing obfuscator.
It rewrites selected Java methods into a compact virtual bytecode program, injects a generated VM, and executes the protected logic through that VM at runtime.
All process does not require compilation of native codes or dynamic library, the VM is written in bytecodes, so it is cross-platform.

This obfuscator is intended for demonstration purposes only and is not suitable for production use.
It can make your program hundreds of times slower, while the quality of its protection is not guaranteed.
This obfuscator may not even provide protection comparable to existing virtualization tools such as V\*P or The\*ida, or even basic bytecode-to-native obfuscation tools such as JN\*C.
Its purpose is to demonstrate the concept of bytecode virtualization.

Friendly warning: any kind of virtualization can make protected code hundreds of times slower.
Code virtualization **should not** be applied to **time-critical** methods.
It should be used **only** for **very important methods** or code where runtime performance is not sensitive.

A video is available here: https://youtu.be/hnDbwdsGjBU

## Build

This project uses JDK 21

```powershell
.\gradlew.bat build
```

The runnable fat jar is generated at:

```text
build/libs/BytecodeVM-X.X.X.jar
```

The BytecodeVM tool requires JDK 21, but generated VM classes target Java 8
(class-file version 52) and protected Java 8 applications can run on a Java 8
runtime. Optional APIs introduced by newer JDKs are detected without linking
them into Java 8 execution paths.

## Usage

Every invocation prints the BytecodeVM banner and version information. Show the command list
or the version generated from Gradle's `project.version` with:

```powershell
java -jar BytecodeVM.jar --help
java -jar BytecodeVM.jar --version
java -jar BytecodeVM.jar --help protect
```

Create and validate a documented YAML configuration:

```powershell
java -jar BytecodeVM.jar init config.yml
java -jar BytecodeVM.jar preset
java -jar BytecodeVM.jar preset BALANCED balanced.yml
java -jar BytecodeVM.jar validate config.yml
```

Inspect exactly what will be protected without generating an output JAR:

```powershell
java -jar BytecodeVM.jar inspect config.yml
java -jar BytecodeVM.jar inspect config.yml --report inspection.json
java -jar BytecodeVM.jar watermark protected.jar
```

`inspect` applies the configured `includes` and `excludes` through the same selection and VM
allocation path as `protect`. The terminal only shows the matched method count and concise VM
allocation. The optional JSON report contains the complete selected-method plan and diagnostics.

Protect a JAR and optionally verify every emitted class with ASM:

```powershell
java -jar BytecodeVM.jar protect app.jar
java -jar BytecodeVM.jar protect config.yml
```

`protect`, `validate`, and `inspect` accept either a positional YAML config or an input JAR using
default settings. Direct JAR protection always parses the configuration returned by
`BytecodeVM.defaultConfig()` and only replaces its input and output paths. The command always comes first,
followed by its arguments and options.
`--config` remains available for scripts. The commands also support `--input`, `--output`,
and `--report <report.json>`.
`protect` also accepts repeatable `--watermark key=value` options for CI and release scripts.
Existing output, report, and initialized config files are overwritten automatically.

Lifecycle logs are printed to the terminal with colors. `--verbose` adds detailed planning and
generation records. `--log-file <file>` additionally writes the same logs to a file, while
`--quiet` suppresses normal logger output and command summaries. The banner and errors remain visible.

The JSON report records the effective configuration, seed, input/output SHA-256, method
selection and skip reasons, pre-encrypted string/number and fixed-constant counts, per-method
VM assignments, VM structure counts, generated class counts and elapsed time. This makes `inspect` suitable for CI
checks before a release build.

Legacy `--config`, `--defaultconfig`, and `--defaultrun` invocations remain accepted and are
mapped to `protect` or `init` with overwrite behavior matching older releases.

`init` only writes the canonical documented default configuration. Presets have their own command:
`preset` lists the gallery, while `preset BALANCED balanced.yml` writes a complete tuned profile.
Every generated default or preset file keeps the same field order, spacing, and explanatory comments
as `BytecodeVM.defaultConfig()`. Available presets are `DISABLED`, `SIMPLE`, `CODE_POOL_ONLY`, `FAST`, `LIGHT`,
`BALANCED`, `INTEGRITY_FOCUSED`, `STRONG`, `EXTREME`, and `RANDOMIZED`.

Any concrete VM structure can be used directly as a preset. For example,
`preset GRAPH graph.yml` creates a balanced configuration using only one `GRAPH` VM.
`--vm-structure` and `--vm-count` can override a gallery preset, such as
`preset STRONG custom.yml --vm-structure FSM --vm-count 1`.

| Preset | Intended use |
|---|---|
| `DISABLED` | Turns off every optional transform and protection layer while retaining basic virtualization. |
| `SIMPLE` | Uses one `SIMPLE_DISPATCH` VM with normal encoding protections. |
| `CODE_POOL_ONLY` | Focuses on CodePool, constant, opcode, and operand protection. |
| `FAST` | Favors lower runtime overhead and LOW structures. |
| `LIGHT` | Uses LOW and MEDIUM structures with restrained code expansion. |
| `BALANCED` | General-purpose default with MEDIUM structures. |
| `INTEGRITY_FOCUSED` | Enables full integrity coverage without EXTREME expansion settings. |
| `STRONG` | Uses MEDIUM and HIGH structures, integrity sampling, and stronger branch decoys. |
| `EXTREME` | Enables the strongest gallery settings and all HIGH structures. |
| `RANDOMIZED` | Automatically selects from every concrete VM structure. |

| Exit code | Meaning |
| ---: | --- |
| `0` | Success |
| `1` | Unexpected failure |
| `2` | Invalid command-line usage |
| `3` | Invalid configuration or inspection failure |
| `4` | Missing or unreadable input JAR |
| `5` | Generation, output, or report write failure |
| `6` | Generated output verification failure |

## Config

BytecodeVM configuration uses YAML (`.yml` or `.yaml`). YAML supports `#` comments;
quote matcher expressions containing `*` because that character has special meaning in YAML.

```yaml
# Relative paths use the current working directory.
input: ./input.jar
output: ./output.jar

# VM allocation and generated runtime shape.
createMode: ONE_FOR_ALL # ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
location: ONE_PACKAGE # SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
renameMode: DISABLE
interpretMode: SAVE_ONLY_REQUIRED_INSTRUCTION
# Automatic tiers, ranked by the current implementation:
# These tiers describe analysis resistance, not runtime speed.
# LOW: SIMPLE_DISPATCH, DISTRIBUTED_DISPATCH, MULTIPLE_DISPATCH,
#      THREADED_DIRECT, THREADED_INDIRECT
# MEDIUM: CALL_THREADED, RECURSIVE, CONTINUATION_PASSING, OBJECT,
#         SELF_MODIFYING, EVENT, COROUTINE
# HIGH: DATA_FLOW, POLYMORPHIC, GRAPH, FSM, REGISTER_BASED
# MEDIUM_LOW: all LOW and MEDIUM structures
# MEDIUM_HIGH: all MEDIUM and HIGH structures
# ANY: all concrete VM structures
# Concrete: SIMPLE_DISPATCH, DISTRIBUTED_DISPATCH, MULTIPLE_DISPATCH,
# THREADED_DIRECT, THREADED_INDIRECT, CALL_THREADED, RECURSIVE,
# CONTINUATION_PASSING, OBJECT, POLYMORPHIC, SELF_MODIFYING,
# REGISTER_BASED, DATA_FLOW, GRAPH, FSM, EVENT, COROUTINE.
vmStructure: HIGH
vmCount: 5

# CodePool and virtual control-flow protection.
protectCodePool: true
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

# Input transforms and call graph handling.
constantFix: true
preEncryptStrings: true
preEncryptNumbers: true

# UNSAFE: Member inlining can break reflection, serialization, frameworks, and external callers.
inlineFields: true
inlineCalledProtectedMethods: true
inlineStaticFinals: true
virtualizeConstructors: true
annotationOnly: false
privateFieldOnly: false
ignorePublicCalls: false
includeReferencedMethods: true

removeAnnotations: true # Remove BytecodeVM SDK annotations from output classes.
watermark:               # Optional custom fields; an embedded default is used when empty.
  owner: NHCM
  channel: release
includeMethodsCalledWithin: false
excludeMethodsCalledWithin: false
virtualizeInvocationBridges: true

# Integrity and low-frequency runtime sampling.
vmIntegrityCheck: true
vmIntegrityCheckRatio: 1.0
vmIntegrityRecheckInterval: 65536

# SuperInstruction fusion.
superInstruction: true
superInstructionCombineRange: [2, 5]
superInstructionMode: HYBRID # RANDOM, PATTERN, HYBRID
superInstructionMaxHandlers: 128
superInstructionMinFrequency: 2

# Interpret-branch decoys and dynamic branch selection.
obfuscateInterpretBranch: true
interpretBranchCases: 3

includes:
  all: ["class:*", "field:*", "method:*(*)*"]
  protectCodePool: ["class:@Sensitive com.example.secure.*;method:*(*)*"]
  dynamicConstantDecrypt: ["class:@Sensitive com.example.secure.*;method:*(*)*"]
  encryptOperands: ["class:com.example.secure.*;method:*(*)*"]
  obfuscateDispatch: ["method:*(*)*"]
  obfuscateInterpretBranch: ["method:@Sensitive *(*)*"]
  constantFix: ["class:com.example.secure.*;field:*"]
  preEncryptStrings: ["class:com.example.secure.*;method:*(*)*"]
  preEncryptNumbers: ["class:com.example.secure.*;method:*(*)*"]
  inlineFields: ["class:com.example.secure.*;field:secret*"]
  inlineStaticFinals: ["class:com.example.secure.*;field:java.lang.String *"]
  inlineCalledProtectedMethods: ["class:com.example.secure.*;method:boolean verify(java.lang.String)"]
  superInstruction: ["class:com.example.hot.*;method:*(*)*"]
excludes:
  all: []
  dynamicStateKey: ["method:* fastPath(*)*"]
```

### Options

Every field is optional. Omitted fields inherit from the canonical `BytecodeVM.defaultConfig()` configuration shown above. Supplying any `includes` or `excludes` block replaces the default matching chain. Every generated VM set uses an independent random 32-bit opcode mapping.

| Field | Values | Default  | Description |
|---|---|----------|---|
| `input` | Path | `./input.jar` | Input jar to transform. |
| `output` | Path | `./output.jar` | Output jar path. |
| `createMode` | `ONE_FOR_ALL`, `PER_METHOD`, `PER_CLASS`, `PER_PACKAGE` | `ONE_FOR_ALL` | Controls how VM classes are grouped. |
| `location` | `SAME_PACKAGE_AS_TARGET`, `NEW_PACKAGE`, `ONE_PACKAGE` | `ONE_PACKAGE` | Controls where generated VM classes are placed. |
| `renameMode` | `ENABLE`, `DISABLE` | `DISABLE` | Randomizes generated VM/support class, field, and method names. It does not rename protected application classes. |
| `interpretMode` | `SAVE_ALL_INSTRUCTION`, `SAVE_ONLY_REQUIRED_INSTRUCTION` | `SAVE_ONLY_REQUIRED_INSTRUCTION` | Controls how many interpreter branches are emitted. |
| `vmStructure` | See VM Structures below | `HIGH` | Selects a concrete VM structure or an automatic protection-strength tier for each VM set. |
| `protectCodePool` | `true`, `false` | `true`   | Enables code-pool protection. When disabled, most protection sub-options below have no effect. |
| `dynamicConstantDecrypt` | `true`, `false` | `true` | Encrypts strings, numeric constants, and type descriptors per use site and decrypts them from the current method, frame, virtual PC, block, instruction, and opcode state. Requires `protectCodePool`. |
| `virtualizeInstructionAddresses` | `true`, `false` | `true`   | Encodes virtual instruction addresses instead of using direct layout addresses. |
| `encryptOperands` | `true`, `false` | `true`   | Encrypts virtual instruction operands. |
| `perMethodOpcodeMap` | `true`, `false` | `true`   | Uses method-specific opcode mapping and decoding. |
| `shuffleConstants` | `true`, `false` | `true`   | Shuffles constants stored in generated VM programs. |
| `bindConstantsToOperands` | `true`, `false` | `true`   | Binds constant references to operand data so constant indexes are not stored plainly. |
| `splitCodeStreams` | `true`, `false` | `true`   | Splits VM program data into separate code/layout/operand streams. |
| `shuffleInstructionBlocks` | `true`, `false` | `true`   | Shuffles virtual instruction blocks before writing code-pool data. |
| `obfuscateDispatch` | `true`, `false` | `true`   | Obfuscates interpreter dispatch selection. |
| `dynamicCodePoolBuild` | `true`, `false` | `true`   | Builds code-pool program data dynamically in generated bytecode instead of storing everything plainly. |
| `dynamicStateKey` | `true`, `false` | `true`   | Adds block-entry state capsules and a rolling per-record key chain used by opcode, layout, and operand decoding. |
| `virtualControlFlowGraph` | `true`, `false` | `true`   | Stores methods as shuffled virtual basic blocks and resolves instruction indexes through block-local lookup. |
| `constantFix` | `true`, `false` | `true`  | Moves `ConstantValue` data from static final fields into `<clinit>` assignments, updates initializer stack metadata, and clears the field value attribute. |
| `preEncryptStrings` | `true`, `false` | `true` | Adaptively replaces selected string constants with per-site encrypted integer data and an inline runtime decoder before virtualization. Selection accounts for method size, constant count, text length, and estimated generated growth. |
| `preEncryptNumbers` | `true`, `false` | `true` | Adaptively replaces selected integer, long, float, and double constants with encrypted bit patterns while retaining enough size headroom for virtualization. |
| `inlineFields` | `true`, `false` | `true` | Removes selected fields of any JVM type. Primitive and String values are encrypted directly; object and array references use encrypted randomized handles. Storage and crypto bytecode are expanded at each original field instruction. |
| `inlineCalledProtectedMethods` | `true`, `false` | `true` | Uses the protected method's original owner, name, and descriptor as its direct VM entry. No shared `MethodEntries` bridge class is generated. |
| `inlineStaticFinals` | `true`, `false` | `true` | Applies direct access-site encryption and storage to selected static-final fields of any JVM type. |
| `virtualizeConstructors` | `true`, `false` | `true` | Keeps the required `super()`/`this()` prefix and virtualizes the complete initialized-this constructor continuation. Unsafe constructor shapes are skipped automatically. |
| `annotationOnly` | `true`, `false` | `false` | Restricts unsafe transforms to `@InlineField`, `@InlineFinal`, and method-level `@Virtualize` targets when enabled. The maximum-coverage default lets config match groups select them globally. |
| `privateFieldOnly` | `true`, `false` | `false` | Limits config-selected field inlining to private fields when enabled. An explicit SDK field annotation is treated as an intentional override. |
| `ignorePublicCalls` | `true`, `false` | `false` | Limits unsafe original-slot VM entry selection to private methods when enabled. The default also handles package, protected, and public call sites. |
| `includeReferencedMethods` | `true`, `false` | `true` | Automatically protects eligible methods that access selected fields or call original-slot VM entries. Effective exclude decisions still win. |
| `removeAnnotations` | `true`, `false` | `true` | Removes BytecodeVM SDK annotations from classes and methods after their options have been applied. Other application annotations are untouched. |
| `watermark` | key/value map | `{}` | Adds custom fields to the mandatory bytecode-embedded watermark. Empty maps receive a default label. |
| `includeMethodsCalledWithin` | `true`, `false` | `false`  | Recursively includes target-jar methods called from explicitly included methods. |
| `excludeMethodsCalledWithin` | `true`, `false` | `false`  | Recursively excludes target-jar methods called from explicitly included methods. |
| `virtualizeInvocationBridges` | `true`, `false` | `true` | Virtualizes generated `$vm$invoke$N` bridge methods when their bytecode can be represented by the VM. String concat invokedynamic bridges are lowered to normal `StringBuilder` bytecode first. |
| `vmIntegrityCheck` | `true`, `false` | `true`  | Generates a second-stage integrity VM that checks the generated VM and CodePool class bytes before dispatching protected methods. |
| `vmIntegrityCheckRatio` | `0.0` to `1.0` | `1.0` | Controls how many replaced method stubs call the integrity VM. `1.0` checks every stub. |
| `vmIntegrityRecheckInterval` | `0` to `16777216` | `65536` | Approximate protected-entry interval between low-frequency runtime integrity probes. Each probe rechecks one derivation chunk; `0` disables periodic rechecks. |
| `superInstruction` | `true`, `false` | `true`  | Fuses safe VM instruction sequences into synthetic super instructions with generated handlers. |
| `superInstructionCombineRange` | `[min, max]` | `[2, 5]` | Minimum and maximum VM instruction count to fuse into one super instruction. |
| `superInstructionMode` | `RANDOM`, `PATTERN`, `HYBRID` | `HYBRID` | Chooses random ranges, frequent opcode patterns, or both. |
| `superInstructionMaxHandlers` | `1` to `4096` | `128`    | Caps generated super-instruction recipes per VM set. |
| `superInstructionMinFrequency` | Positive integer | `2`      | Minimum pattern frequency before `PATTERN` or `HYBRID` pre-registers a recipe. |
| `obfuscateInterpretBranch` | `true`, `false` | `true` | Emits randomized sparse decoy branches and decrypts the real branch selector from CodePool data using the current method, state, instruction, virtual-PC, and opcode state. |
| `interpretBranchCases` | `1` to `8` | `3` | Total generated cases per interpreter branch, including the real branch. `1` disables decoy expansion. |
| `vmCount` | `1` to `1024` | `5`      | Expands each non-`PER_METHOD` VM grouping into this many randomized VM sets and distributes matched methods among them. Five covers the complete current `HIGH` candidate bag. |
| `includes` | Array or object of typed match expressions | `class:*`, `field:*`, `method:*(*)*` | Selects classes, fields, and methods explicitly, plus optional per-boolean groups. Blocks can be repeated. |
| `excludes` | Array or object of match expressions | empty | Removes matching targets. Blocks may be repeated and interleaved with `includes`; the last matching block wins. |

## Annotation SDK

The `sdk` subproject is a dependency-free Java 8 annotation and watermark utility library published on Maven Central. Use `compileOnly` when only annotations are needed; watermark-reading tools must keep the SDK on their runtime classpath.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'io.github.nhcm-dev:bytecodevm-sdk:2.2.0'
}
```

For Maven projects, use `provided` scope:

```xml
<dependency>
    <groupId>io.github.nhcm-dev</groupId>
    <artifactId>bytecodevm-sdk</artifactId>
    <version>2.2.0</version>
    <scope>provided</scope>
</dependency>
```

To test the current source checkout instead of the published release, publish it to Maven Local with:

```powershell
gradlew :sdk:publishToMavenLocal
```

`@ProtectClass` makes a class eligible for SDK-aware and YAML method matching without automatically virtualizing every method. `@Virtualize` on a class selects all eligible methods; on a method it selects only that method. `@DoNotVirtualize` and an effective YAML exclude decision exclude their target.

```java
import nhcm.bytecodevm.sdk.annotation.DoNotVirtualize;
import nhcm.bytecodevm.sdk.annotation.InlineField;
import nhcm.bytecodevm.sdk.annotation.InlineFinal;
import nhcm.bytecodevm.sdk.annotation.ProtectClass;
import nhcm.bytecodevm.sdk.annotation.Virtualize;

@ProtectClass
public final class LicenseService {
    @InlineField
    private int attempts;

    @InlineFinal
    private static final String PRODUCT = "BytecodeVM";

    @Virtualize
    public boolean verify(String key) {
        return key != null;
    }

    @DoNotVirtualize
    public String version() {
        return "1.0";
    }
}
```

Every SDK option using `CONFIG` inherits its value from the enclosing class annotation and then YAML. Explicit class, constructor, or method SDK values override YAML, and member values override class values. `@Virtualize.preEncryptStrings` and `preEncryptNumbers` default to `ENABLED`; set either to `Toggle.DISABLED` for a target that should retain its original constants. A structure override is assigned to a compatible VM set, so it does not silently retain an incompatible global VM structure. `inspect` and `protect` emit a warning when an SDK structure falls outside the configured automatic tier.

`VMOptions` groups the low-level YAML switches into three practical controls. `encrypt` controls virtual addresses, operands, per-method opcode maps, dynamic constant decryption, constant binding, and dynamic state keys. `shuffle` controls constants, split streams, instruction blocks, and virtual-CFG layout. `obfuscate` controls dispatch obfuscation and dynamic CodePool construction. Explicitly enabling any group also enables `protectCodePool`; disabling one group leaves the other groups unchanged. Fine-grained tuning remains available in YAML.

Constant relocation is class metadata, so the SDK exposes `constantFix` only through `@ProtectClass`. `superInstructionMaxHandlers` controls a VM-set-wide handler registry and therefore remains YAML-only; per-target `SuperInstructionOptions` exposes enablement, mode, combine range, and minimum pattern frequency.

```java
import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.SuperInstructionOptions;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.CallPolicy;
import nhcm.bytecodevm.sdk.enums.SuperInstructionMode;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

@Virtualize(
    preEncryptStrings = Toggle.ENABLED,
    preEncryptNumbers = Toggle.ENABLED,
    vm = @VMOptions(
        structure = VMStructure.DATA_FLOW,
        encrypt = Toggle.ENABLED,
        shuffle = Toggle.ENABLED,
        obfuscate = Toggle.ENABLED
    ),
    superInstructions = @SuperInstructionOptions(
        enabled = Toggle.ENABLED,
        mode = SuperInstructionMode.HYBRID,
        combineMin = 2,
        combineMax = 6
    ),
    integrityCheck = Toggle.ENABLED,
    calls = CallPolicy.INCLUDE
)
public boolean verifyLicense(String key) {
    return check(key);
}
```

`CallPolicy.INCLUDE` recursively virtualizes target-JAR methods reachable from that root. `EXCLUDE` recursively excludes them, `NONE` disables expansion for that root, and `CONFIG` uses the YAML call-expansion settings. Invalid SDK numeric ranges report the annotated class and method. With the default `removeAnnotations: true`, all BytecodeVM SDK declaration annotations are removed after their settings have been applied.

## Member Inlining

`inlineFields` removes selected fields of any valid JVM type from their declaring classes and replaces
ordinary `GETFIELD`, `PUTFIELD`, `GETSTATIC`, and `PUTSTATIC` instructions with in-place storage and
crypto bytecode. Every field receives an independent randomized storage slot and cipher constants, and
every write receives a fresh nonce. The generated storage class exposes no `get`, `set`, `encode`, or
`decode` methods. Primitive values and Strings are encrypted directly. Objects, interfaces, and arrays
retain JVM identity through a shared reference vault keyed by randomized tokens, while field records contain
only encrypted handles; null values and declared-type checks remain at the original access sites. Volatile
fields are supported through synchronized or volatile storage paths.

Instance records are indexed by generated weak identity keys containing the owner's `identityHashCode` and
the field's randomized slot. They never call application `equals` or `hashCode`. A shared `ReferenceQueue`
removes records after their owner becomes unreachable, and the record's separately encrypted cleanup token
also releases its live object or array from the reference vault. Repeated writes release the previous token,
and direct self-references use a reserved encrypted state without creating a strong vault path back to the
owner. All instance fields share one concurrent weak table instead of allocating one Map per field.

The public Java reference API does not provide true ephemeron tables. Direct `field == owner` references are
handled explicitly, but an arbitrary stored object graph that indirectly points back to its owner can still
keep that owner reachable through the vault. Exclude parent-linked graph fields from inlining when exact
cycle collection is required; solving that case requires retaining an owner-local synthetic carrier field.

`inlineStaticFinals` applies the same storage model specifically to static-final values. A `ConstantValue`
is moved into the beginning of `<clinit>` before the field is removed, while an existing initializer is
rewritten in place. `@InlineField` and `@InlineFinal` can select individual fields even when their global
switch is disabled:

```java
@InlineField
private int attempts;

@InlineFinal
private static final String LICENSE_SALT = "example";
```

`virtualizeConstructors` analyzes each selected `<init>` and identifies the call that initializes `this`.
The required prefix remains ordinary JVM bytecode, while the complete continuation after `super()` or
`this()` is moved into a private synthetic method and assigned to the configured VM. Live local values are
passed as compact helper parameters and restored to their original slot layout inside the continuation.
Constructors with multiple initialization branches, a non-empty
split stack, control flow or exception regions crossing the split, unsupported live locals, stack-trace
introspection, record/enum semantics, or SecurityManager inheritance are retained unchanged. A constructor-
level `@Virtualize` annotation can explicitly enable this behavior when global constructor virtualization is
disabled.

`inlineCalledProtectedMethods` does not generate a shared `MethodEntries` class. The existing replacement
stage writes the VM execute stub directly into the protected method's original owner/name/descriptor slot,
so existing calls, recursive calls, method handles, and cross-class callers continue through the same
symbol. With `annotationOnly: true`, this unsafe mode applies only to methods carrying their own
`@Virtualize` annotation; a class-level `@ProtectClass` alone does not opt every method into it.

Member removal changes reflection, serialization, framework injection, and external binary APIs. The
maximum-coverage defaults leave `privateFieldOnly` and `ignorePublicCalls` disabled; enable either limit when
compatibility matters more than coverage. Record and enum storage, fields written before a constructor has
initialized `this`, and fields referenced by
constant method handles are skipped. Fields used through recognizable constant-name reflection, Java
serialization/externalization, or clone-compatible object layouts are also retained automatically.
`includeReferencedMethods` automatically
adds eligible field accessors and callers to the protection plan; effective exclude decisions still prevent field
removal when a required method cannot safely be protected. Constructor continuations normally move all
post-initialization field work into one virtualized body. When a constructor cannot be split safely, individual
inlined field accesses still use virtualized fallback helpers after `this` has been initialized. Pre-super
writes remain untouched and make the field ineligible because the JVM verifier does not allow an uninitialized
`this` reference to be passed into a helper. `<clinit>` keys are hidden only when that initializer is selected
for virtualization.

## Watermarks

Every protected JAR receives a watermark, even when `watermark: {}` is left empty. BytecodeVM always
records the UTC protection time, unique artifact ID, tool version, and input SHA-256. Custom YAML fields
are stored under `user.*`; command-line values merge with YAML and win on duplicate keys:

```powershell
java -jar BytecodeVM.jar protect app.jar --watermark owner=NHCM --watermark channel=release
java -jar BytecodeVM.jar watermark app-bytecodevm.jar
java -jar BytecodeVM.jar watermark app-bytecodevm.jar --json
```

The watermark is not a JAR resource. An AES-GCM capsule is embedded in a generated method's bytecode,
with generated class, method, and field names following `renameMode`. Every VM resolves code IDs through
the authenticated carrier. Removing the carrier or changing its capsule prevents virtualized methods from
running. The JSON obfuscation report contains the same generated tracking fields.

The SDK can verify and read a protected JAR without loading or executing its classes:

```java
import java.nio.file.Paths;
import nhcm.bytecodevm.sdk.watermark.WatermarkInfo;
import nhcm.bytecodevm.sdk.watermark.WatermarkReader;

WatermarkInfo watermark = WatermarkReader.read(Paths.get("app-bytecodevm.jar"));
String artifactId = watermark.artifactId();
String owner = watermark.get("user.owner");
```

## Rename Mode

When `renameMode` is `ENABLE`, BytecodeVM renames only generated VM artifacts. Target application classes, fields, and methods selected for virtualization keep their original names and owners.

Rename supports:

| Area | Behavior |
|---|---|
| VM support classes | `MethodFrame`, `VMProgram`, `VMCodePool`, generated VM classes, CodePool classes, integrity carriers, and auxiliary handler classes receive generated names. |
| VM fields | Runtime support fields, structure tables, handler rings, CodePool storage fields, frame fields, and program fields receive generated names. |
| VM methods | VM runtime methods, structure scheduler methods, semantic handler methods, CodePool init helpers, integrity derivation methods, and generated invocation bridges receive generated names. |
| Handler shards | Interface-dispatched handlers are stored in compact generated shards that use randomized token decision trees rather than enumerable JVM opcode switches. |

The original application bytecode is still transformed by virtualization, constant fixing, invocation bridge rewriting, and method replacement as configured, but its class/member names are not globally remapped by `renameMode`.

## VM Structures

`vmStructure` is resolved once per VM set. A concrete value never silently falls back to `SIMPLE_DISPATCH`. `LOW`, `MEDIUM`, and `HIGH` select from shuffled strength-tier bags ranked by the protection actually generated, rather than by architecture names. Separate VM sets can therefore use different architectures while staying within the requested protection level. Automatic selection avoids repeats until the tier's candidate bag is exhausted; the no-repeat window is capped at 12 candidates. Use `vmCount: 5` to cover every current `HIGH` candidate once before that bag is refilled.

The ranking measures static-analysis resistance, not throughput or latency. `HIGH` structures add maps, handler objects, state machines, dependency scheduling, or register lowering and can exceed the timing budget of hot or latency-sensitive code. Pin those methods to a concrete lower-cost structure with the SDK, select them into a separate VM set, or exclude them from virtualization.

| Value | Generated execution shape                                                                                                                                                                                                       | Relative protection |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| `SIMPLE_DISPATCH` | Baseline decode loop with one central opcode dispatcher and split semantic chunks.                                                                                                                                              | Lowest |
| `DISTRIBUTED_DISPATCH` | Routes encoded opcode keys into disjoint dispatch shard methods.                                                                                                                                                                | Low |
| `MULTIPLE_DISPATCH` | Chooses among several equivalent dispatchers with different key transforms and case layouts.                                                                                                                                    | Low |
| `THREADED_DIRECT` | Stores direct handler tokens in the VM opcode stream and maps them straight to generated executable handler objects without an opcode switch.                                                                                   | Low |
| `THREADED_INDIRECT` | Resolves decoded opcodes to dense runtime tokens, then indexes a generated handler array through an interface trampoline.                                                                                                       | Low |
| `CALL_THREADED` | Uses generated callable handler objects, bounded tail segments, and an outer trampoline.                                                                                                                                        | Medium |
| `RECURSIVE` | Executes bounded recursive segments and returns to a loop before JVM stack depth can grow without limit.                                                                                                                        | Medium |
| `CONTINUATION_PASSING` | Encoded continuation actions select generated continuation handler variants through a switch-free trampoline.                                                                                                                   | Medium |
| `OBJECT` | Materializes token-bound instruction objects backed by generated semantic shards and executes them through interface dispatch.                                                                                                  | Medium |
| `POLYMORPHIC` | Generates multiple semantic-equivalent handler classes per opcode and selects a variant from runtime state.                                                                                                                     | High |
| `SELF_MODIFYING` | Copies opcode data per frame, re-encodes executed slots, tracks matching masks, and resolves semantics through a probed handler ring.                                                                                           | Medium |
| `REGISTER_BASED` | Lowers constants, local moves, arithmetic, shifts, conversions, compares, and increments into explicit `destination/sourceA/sourceB` register micro-ops. Complex JVM operations remain behavior-preserving bridge instructions. | High |
| `DATA_FLOW` | Lowers exception-safe basic-block regions into shuffled register nodes with encoded RAW, WAR, and WAW dependency masks, then executes ready nodes instead of following source order.                                            | Highest |
| `GRAPH` | Walks encoded node/edge state and uses that state to select among generated graph-node handler layers.                                                                                                                          | High |
| `FSM` | Maintains encoded states independent of the VM pc and indexes a generated state-by-symbol transition matrix.                                                                                                                    | High |
| `EVENT` | Uses a bounded event-token ring and state-selected generated listener objects; each execution pulse emits the next event.                                                                                                       | Medium |
| `COROUTINE` | Uses a reusable continuation-state array, separate resume phases, and bounded yield pulses without threads or Project Loom.                                                                                                     | Medium |
| `LOW` | Chooses `SIMPLE_DISPATCH`, `DISTRIBUTED_DISPATCH`, `MULTIPLE_DISPATCH`, `THREADED_DIRECT`, or `THREADED_INDIRECT`.                                                                                                              | Low |
| `MEDIUM` | Chooses `CALL_THREADED`, `RECURSIVE`, `CONTINUATION_PASSING`, `OBJECT`, `SELF_MODIFYING`, `EVENT`, or `COROUTINE`.                                                                                                             | Medium |
| `HIGH` | Chooses `DATA_FLOW`, `POLYMORPHIC`, `GRAPH`, `FSM`, or `REGISTER_BASED`.                                                                                                                                                       | High |

`SIMPLE_DISPATCH` alone retains split multi-opcode `interpretChunk` methods and named opcode/operand decoders as the compatibility baseline. Non-simple structures inline opcode, next-pc, original-pc, layout, and operand decoding into their generated kernels and semantic handlers. Their handler and kernel descriptors vary by structure, and each VM set receives separate Frame, Program, and CodePool support types with a structure-specific field order.

Every protection profile randomizes its layout-field permutation, decode expression shape, salts, multipliers, opcode mapping, and handler tokens. State-key records form a block-local rolling chain: a block entry is decoded from its capsule, while each later physical record depends on the previous state. CFG transfers, loop entries, and exception handlers resynchronize through the destination block and slot. Interface-dispatched structures use compact shards with randomized binary decision trees instead of `lookupswitch`/`tableswitch` handler banks. Recursive and call-threaded modes use bounded depth plus a trampoline. Object, event, and coroutine state is allocated once per invocation or cached rather than allocated for every virtual instruction. Large integrity target sets are split into independently virtualized derivation chunks to avoid method and CodePool limits.

VM methods that cannot fit in one CodePool initializer are split into independently serializable segments. Each segment rebuilds a local constant pool, remaps constant operands and catch types, and retains only exception handlers whose protected ranges overlap that segment. Program counters and handler targets remain method-global, so the segmented executor can preserve cross-segment branches and exception transfers without copying the original method's full metadata into every CodePool.

`REGISTER_BASED` uses the frame locals plus an operand-register window as one address space. Stack-relative tokens are resolved against the pre-instruction register window, while local slots use direct register ids. `DATA_FLOW` groups up to eight eligible nodes without crossing a jump target, exception boundary, or control-transfer instruction. It randomizes their physical order, remaps dependency masks, and repeatedly executes nodes whose prerequisites are complete. Invocation, exceptions, monitors, objects, arrays, and other unsafe operations retain the existing interpreter semantics as bridge instructions.

## Super Instructions

When `superInstruction` is enabled, the generator scans VM instructions inside safe basic-block regions and replaces selected instruction sequences with one synthetic `SUPER_INSTRUCTION`. The synthetic instruction stores a generated recipe id followed by the flattened operands of the fused instructions. At runtime the VM dispatches once, reads the recipe id, and expands the original interpreter branch bodies inside a generated super handler.

`superInstructionMode` controls selection:

| Mode | Behavior |
|---|---|
| `RANDOM` | Randomly chooses fusable ranges within `superInstructionCombineRange`. |
| `PATTERN` | Registers frequent opcode sequences and fuses only matching patterns. |
| `HYBRID` | Uses frequent patterns first, then randomly fuses remaining safe ranges. |

## VM Integrity Check

When `vmIntegrityCheck` is enabled, each generated VM set gets a second-stage integrity carrier. After the normal VM and CodePool classes are generated, their final class bytes are hashed. The expected hash data is written into generated derivation methods, and both the derivation chain and one-shot method cold wrappers are virtualized again with a dedicated high-strength VM using `SAVE_ALL_INSTRUCTION`.

Application stubs do not expose the hash gateway, normal VM owner, or plain code id. Each protected method calls a separate randomized carrier entry using the package-agnostic `(Object receiver, Object[] arguments) -> Object` ABI. On the first call for a VM set, that entry invokes its second-stage-virtualized cold wrapper. The wrapper computes and publishes a non-zero per-VM capability before entering the normal VM, so recursive calls immediately use the hot path instead of nesting more integrity interpreters.

Hot entries do not keep a plain capability or a separate ready flag. The one-shot cold path publishes one volatile 64-bit authenticated state envelope: one half contains an invertibly encoded capability and the other half contains a keyed tag plus its publication marker. Every protected entry inlines envelope recovery and folds any tag mismatch into the dynamic key without a visible failure branch. This adds only one volatile read and a short integer-mixing sequence to the hot path; it does not lock or read class resources again. Each entry also stores only a capability-bound code-id encoding with independently randomized rotation, multiplier, addend, and salt. Skipping the integrity path, replaying a partial state, or supplying the old zero key therefore corrupts method resolution and opcode/layout/operand decoding.

When `vmIntegrityRecheckInterval` is non-zero, hot entries also advance a cheap shared ticket. At a randomized cadence around that interval, one derivation chunk is selected in round-robin order and executed through the second-stage IntegrityVM. A probe checks only that chunk's resources, then corrupts the authenticated state envelope if it observes a mismatch. This amortizes ongoing resource verification across many protected calls instead of re-reading every generated class on every invocation.

The integrity VM itself is not included in the hash target set to avoid self-referential hashes.

## Include / Exclude Match Expressions

`includes` and `excludes` are ordered decision blocks. They may appear more than once and may be
interleaved. Rules are evaluated in document order, and the last rule matching a class, field, or
method decides whether that target is selected. This directly supports exclude, re-include, and
re-exclude chains:

```yaml
includes:
  - "class:com.example.*"
  - "class:com.example.*;field:*"
  - "class:com.example.*;method:*(*)*"
excludes:
  - "class:com.example.internal.*"
  - "class:com.example.internal.*;field:*"
  - "class:com.example.internal.*;method:*(*)*"
includes:
  - "class:com.example.internal.Api;method:publicEntry(*)*"
excludes:
  - "class:com.example.internal.Api;method:debugEntry(*)*"
```

When a group first appears in an `includes` block, unmatched targets begin excluded. When it first
appears in an `excludes` block, unmatched targets begin included. Supplying any matching block in a
configuration replaces the default matching chain.

Each rule contains one `class:`, `field:`, or `method:` clause. In the `all` group, `class:` is a hard
class gate: a class must be included before any of its members can be processed, and excluding a
class rejects every field and method inside it. A standalone `class:` rule does not select members;
`field:` or `method:` must also select the target kind. Combining `class:` with one member clause
using `;` performs both decisions in one rule. Member clauses without `class:` may carry their own
owner pattern, but still require a separate matching class declaration in the `all` group.

```yaml
includes:
  - "class:@XXX.Annotation Expo.*;method:methodA(int,*)java.lang.String"
  - "class:pack1.pack2.*.abc;field:java.lang.String field*"
  - "field:@Anno clazz.* someClazzField"
  - "method:void <clinit>()V"
```

The grouped form applies the same ordered-block behavior to individual boolean options:

```yaml
includes:
  all:
    - "class:com.example.*"
    - "class:com.example.*;field:*"
    - "class:com.example.*;method:*(*)*"
  protectCodePool: ["class:@Sensitive com.example.*;method:*(*)*"]
  dynamicConstantDecrypt: ["method:@Sensitive *(*)*"]
  encryptOperands: ["class:com.example.secure.*;method:*(*)*"]
excludes:
  all: ["method:* <init>(*)V"]
  dynamicStateKey: ["method:* hotLoop(*)*"]
```

`all` controls which classes, fields, and methods are selected. Its class decision is always checked
before its field or method decision, and each target kind must be matched explicitly. Boolean option
groups only scope an option that is globally enabled; they may use member-only rules after `all` has
admitted the class. A matching block does not turn on a globally disabled option.

Supported boolean group names are:

`protectCodePool`, `dynamicConstantDecrypt`, `virtualizeInstructionAddresses`, `encryptOperands`, `perMethodOpcodeMap`, `shuffleConstants`, `bindConstantsToOperands`, `splitCodeStreams`, `shuffleInstructionBlocks`, `obfuscateDispatch`, `dynamicCodePoolBuild`, `dynamicStateKey`, `virtualControlFlowGraph`, `constantFix`, `preEncryptStrings`, `preEncryptNumbers`, `inlineFields`, `inlineStaticFinals`, `inlineCalledProtectedMethods`, `virtualizeConstructors`, `superInstruction`, and `obfuscateInterpretBranch`.

Wildcards are supported with `*`. Normal Java type names, arrays, primitive descriptors, object
descriptors, and wildcard descriptor fragments are accepted. The old space/comma syntax and
`exclusions` key remain accepted for compatibility, but generated configurations use the typed form.

### Class Rules

| Expression | Effect |
|---|---|
| `class:*` | Admit all classes through the class gate. It does not select any field or method. |
| `class:package.*` | Match classes in `package` and its subpackages. |
| `class:@Virtualized *` | Match classes annotated with `@Virtualized`. |
| `class:@com.example.Virtualized com.example.*` | Match annotated classes in `com.example`. |

### Field Rules

| Expression | Effect |
|---|---|
| `field:*` | Match all fields. |
| `field:com.example.* token` | Match field `token` in `com.example` classes. |
| `field:com.example.* java.lang.String token*` | Match String fields whose names start with `token`. |
| `field:@Sensitive com.example.* secret*` | Match annotated fields. |
| `class:com.example.Account;field:long balance` | Match one typed field within a class context. |

### Method Rules

| Expression | Effect |
|---|---|
| `method:*(*)*` | Match all methods with any signature. |
| `method:void main(java.lang.String[])` | Match any `void main(String[])`. |
| `method:com.example.* boolean verify(java.lang.String,int)` | Match a method with owner and readable types. |
| `method:@Virtualize com.example.* run(*)*` | Match annotated `run` methods. |
| `class:com.example.Api;method:lookup(int,*)java.lang.String` | Match using a class context and suffix return type. |
| `method:void <init>(*)V` | Match constructors. |
| `method:void <clinit>()V` | Match static initializers. |

Annotation matching checks both runtime-visible and runtime-invisible annotations. You may use a simple annotation name, a full class name, or JVM descriptor form:

```yaml
includes:
  - "class:@Virtualized *"
  - "method:@Virtualize *(*)*"
excludes:
  - "method:void <init>(*)V"
  - "method:void <clinit>()V"
  - "method:@DoNotVirtualize *(*)*"
```
