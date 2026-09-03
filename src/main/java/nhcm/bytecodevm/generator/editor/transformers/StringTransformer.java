package nhcm.bytecodevm.generator.editor.transformers;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.generator.abstracts.Transformer;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import nhcm.bytecodevm.utils.builder.MethodRef;
import org.objectweb.asm.tree.*;

import java.util.*;

public class StringTransformer extends Transformer
{
    private ConstantEncryptionStats stats = ConstantEncryptionStats.empty();

    public StringTransformer(BytecodeVMConfig config)
    {
        super(config, "preEncryptStrings");
    }

    @Override
    public int transform(Collection<ClassNode> classNodes)
    {
        int changed = 0;
        int candidatesSeen = 0;
        long estimatedGrowth = 0L;
        AdaptiveEncryptionBudget.Global globalBudget =
                AdaptiveEncryptionBudget.global(classNodes, AdaptiveEncryptionBudget.Kind.STRING);

        for(ClassNode classNode : classNodes)
        {
            for(MethodNode method : classNode.methods)
            {
                if(!MethodUtils.hasBody(method))
                {
                    continue;
                }

                SdkAnnotationReader.MethodDirectives directives =
                        SdkAnnotationReader.methodDirectives(classNode, method);
                Boolean sdkOverride = directives.excluded()
                        ? Boolean.FALSE
                        : directives.options().preEncryptStrings();
                if (!config.preEncryptStrings && sdkOverride == null)
                {
                    continue;
                }
                if(!shouldEncrypt(classNode, method, sdkOverride))
                {
                    continue;
                }

                List<LdcInsnNode> ldcs = new ArrayList<>();

                for(AbstractInsnNode insn : method.instructions)
                {
                    if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String)
                    {
                        ldcs.add(ldc);
                    }
                }

                if(ldcs.isEmpty())
                {
                    continue;
                }

                candidatesSeen += ldcs.size();
                long characters = ldcs.stream()
                        .mapToLong(ldc -> ((String) ldc.cst).length())
                        .sum();
                AdaptiveEncryptionBudget budget =
                        AdaptiveEncryptionBudget.strings(method, ldcs.size(), characters);
                RandomUtils.shuffle(ldcs);
                ldcs.sort(Comparator.comparingInt(
                        (LdcInsnNode ldc) -> ((String) ldc.cst).length()).reversed());

                Map<InsnList, LdcInsnNode> replacements = new LinkedHashMap<>();

                /*
                 * 所有我们插入的新 local 都从原方法的 maxLocals 后面开始，
                 * 避免覆盖 this / 方法参数 / 原方法已有 local。
                 */
                int nextLocal = method.maxLocals;
                int requiredMaxLocals = method.maxLocals;

                for(LdcInsnNode ldc : ldcs)
                {
                    String str = (String) ldc.cst;

                    int[] encryptM = getMaterial(RandomUtils.randomInt());
                    int[] encrypted = encrypt(str, encryptM);

                    AdvIBdr ib = new AdvIBdr(nextLocal);

                    /*
                     * int[] data = new int[encrypted.length];
                     */
                    Local dataLocal = ib.createLocal("data", int[].class, AdvIBdr.newArray(int.class, AdvIBdr.constant(encrypted.length))).getLocal("data");

                    /*
                     * data[i] = encrypted[i]
                     */
                    for(int i = 0; i < encrypted.length; i++)
                    {
                        ib.setArray(
                                dataLocal,
                                AdvIBdr.constant(i),
                                AdvIBdr.constant(encrypted[i])
                        );
                    }

                    /*
                     * int[] material = new int[encryptM.length];
                     */
                    Local materialLocal = ib.createLocal("material", int[].class, AdvIBdr.newArray(int.class, AdvIBdr.constant(encryptM.length))).getLocal("material");

                    /*
                     * material[i] = encryptM[i]
                     */
                    for(int i = 0; i < encryptM.length; i++)
                    {
                        ib.setArray(
                                materialLocal,
                                AdvIBdr.constant(i),
                                AdvIBdr.constant(encryptM[i])
                        );
                    }

                    /*
                     * int dataLength = (data.length - 1) * 2;
                     *
                     * 这个是 char[] 的容量，不是 decrypt 循环的上限。
                     */
                    Expr charArrayLength = AdvIBdr.multiply(
                            AdvIBdr.constant(2),
                            AdvIBdr.minus(AdvIBdr.arrayLength(dataLocal), AdvIBdr.constant(1))
                    );

                    /*
                     * char[] c = new char[(data.length - 1) * 2];
                     */
                    Local c = ib.createLocal("c", char[].class, AdvIBdr.newArray(char.class, charArrayLength)).getLocal("c");
                    Local state = ib.createLocal("state", int.class, AdvIBdr.constant(0)).getLocal("state");
                    Local x = ib.createLocal("x", int.class, AdvIBdr.constant(0)).getLocal("x");

                    /*
                     * for (int i = 0; i < material.length; i++)
                     * {
                     *     int x = material[i];
                     *     state ^= x;
                     *     state = Integer.rotateLeft(state, x & 31);
                     *     state += x;
                     * }
                     */
                    ib.forIndexLoop(
                            AdvIBdr.arrayLength(materialLocal),
                            (i, b) -> {
                                b.set(x, AdvIBdr.arrayAt(materialLocal, i));

                                b.set(state, AdvIBdr.bitXor(state, x));
                                b.set(state, AdvIBdr.callStatic(
                                        Integer.class, "rotateLeft", int.class,
                                        state, AdvIBdr.bitAnd(x, AdvIBdr.constant(31))
                                      )
                                );

                                b.set(state, AdvIBdr.plus(state, x));
                            }
                    );

                    Local m = ib.createLocal("m", int.class, AdvIBdr.constant(0)).getLocal("m");
                    Local value = ib.createLocal("value", int.class, AdvIBdr.constant(0)).getLocal("value");
                    Local p = ib.createLocal("p", int.class, AdvIBdr.constant(0)).getLocal("p");

                    /*
                     * for (int i = 1; i < data.length; i++)
                     */
                    ib.forIndexLoop(
                            AdvIBdr.constant(1),
                            AdvIBdr.arrayLength(dataLocal),
                            (i, b) -> {

                                /*
                                 * int m =
                                 *     material[(i - 1) % material.length];
                                 */
                                Expr materialIndex = AdvIBdr.remainder(AdvIBdr.minus(i, AdvIBdr.constant(1)), AdvIBdr.arrayLength(materialLocal));

                                b.set(m, AdvIBdr.arrayAt(materialLocal, materialIndex));

                                /*
                                 * state ^= m;
                                 */
                                b.set(state, AdvIBdr.bitXor(state, m));

                                /*
                                 * state += Integer.rotateLeft(
                                 *     m ^ i,
                                 *     state & 31
                                 * );
                                 */
                                b.set(state, AdvIBdr.plus(state, AdvIBdr.callStatic(
                                        Integer.class, "rotateLeft", int.class,
                                        AdvIBdr.bitXor(m, i), AdvIBdr.bitAnd(state, AdvIBdr.constant(31))))
                                );

                                /*
                                 * state = Integer.rotateLeft(
                                 *     state,
                                 *     m & 31
                                 * );
                                 */
                                b.set(state, AdvIBdr.callStatic(
                                        Integer.class, "rotateLeft", int.class,
                                        state,
                                        AdvIBdr.bitAnd(m, AdvIBdr.constant(31)))
                                );

                                /*
                                 * int value = data[i] ^ state;
                                 */
                                b.set(value, AdvIBdr.bitXor(AdvIBdr.arrayAt(dataLocal, i), state));

                                /*
                                 * int p = (i - 1) * 2;
                                 */
                                b.set(p, AdvIBdr.multiply(
                                        AdvIBdr.minus(i, AdvIBdr.constant(1)),
                                        AdvIBdr.constant(2))
                                );

                                /*
                                 * c[p] = (char) (value >>> 16);
                                 */
                                b.setArray(c, p,
                                        AdvIBdr.cast(
                                                AdvIBdr.unsignedShiftRight(value, AdvIBdr.constant(16)),
                                                char.class
                                        )
                                );

                                /*
                                 * c[p + 1] = (char) value;
                                 */
                                b.setArray(c,
                                        AdvIBdr.plus(p, AdvIBdr.constant(1)),
                                        AdvIBdr.cast(value, char.class)
                                );
                            }
                    );

                    /*
                     * new String(c, 0, data[0])
                     *
                     * 最终 String 会留在 operand stack 上，
                     * 正好替换原来的 LDC String。
                     */
                    InsnBuilder rawBuilder = ib.rawBuilder();

                    rawBuilder.new_("java/lang/String");
                    rawBuilder.dup();

                    rawBuilder.aload(c.index());

                    rawBuilder.iconst0();

                    rawBuilder.aload(dataLocal.index());
                    rawBuilder.iconst0();
                    rawBuilder.iaload();

                    rawBuilder.invokeSpecial(
                            new MethodRef(
                                    "java/lang/String",
                                    "<init>",
                                    "([CII)V"
                            )
                    );

                    InsnList replacement = ib.toInsnList();

                    int generatedBytes = AdaptiveEncryptionBudget.bytecodeSize(replacement);
                    int originalBytes = AdaptiveEncryptionBudget.bytecodeSize(ldc);
                    if(!globalBudget.reserve(generatedBytes, originalBytes))
                    {
                        continue;
                    }
                    if(!budget.reserve(generatedBytes, originalBytes, str.length()))
                    {
                        globalBudget.release(generatedBytes, originalBytes);
                        continue;
                    }

                    replacements.put(replacement, ldc);
                    requiredMaxLocals = Math.max(requiredMaxLocals, ib.nextLocalIndex());
                }

                for(Map.Entry<InsnList, LdcInsnNode> entry : replacements.entrySet())
                {
                    method.instructions.insertBefore(
                            entry.getValue(),
                            entry.getKey()
                    );

                    method.instructions.remove(entry.getValue());

                    changed++;
                }
                method.maxLocals = requiredMaxLocals;
                if(!replacements.isEmpty())
                {
                    method.maxStack += 8;
                }
                estimatedGrowth += budget.estimatedGrowth();
            }
        }

        stats = new ConstantEncryptionStats(
                candidatesSeen,
                changed,
                candidatesSeen - changed,
                estimatedGrowth);
        return changed;
    }

    public ConstantEncryptionStats stats()
    {
        return stats;
    }

    /**
     * Example
     */
    public static String decrypt(int[] data, int[] material)
    {
        char[] c = new char[(data.length - 1) * 2];

        int state = 0;

        for(int i = 0; i < material.length; i++)
        {
            int x = material[i];

            state ^= x;
            state = Integer.rotateLeft(state, x & 31);
            state += x;
        }

        for(int i = 1; i < data.length; i++)
        {
            int m = material[(i - 1) % material.length];

            state ^= m;

            state += Integer.rotateLeft(
                    m ^ i,
                    state & 31
            );

            state = Integer.rotateLeft(
                    state,
                    m & 31
            );

            int value = data[i] ^ state;

            int p = (i - 1) * 2;

            c[p] = (char) (value >>> 16);
            c[p + 1] = (char) value;
        }

        return new String(
                c,
                0,
                data[0]
        );
    }

    private static int[] getMaterial(int key)
    {
        int[] material = new int[4];

        for(int i = 0; i < material.length; i++)
        {
            material[i] = RandomUtils.randomInt();
        }

        material[RandomUtils.randomInt(material.length)] = key;

        return material;
    }

    public static int[] encrypt(String str, int[] material)
    {
        char[] c = str.toCharArray();

        int[] out = new int[
                (c.length + 1) / 2 + 1
                ];

        out[0] = c.length;

        int state = 0;

        for(int x : material)
        {
            state ^= x;
            state = Integer.rotateLeft(state, x & 31);
            state += x;
        }

        for(int i = 1; i < out.length; i++)
        {
            int m = material[
                    (i - 1) % material.length
                    ];

            state ^= m;

            state += Integer.rotateLeft(
                    m ^ i,
                    state & 31
            );

            state = Integer.rotateLeft(
                    state,
                    m & 31
            );

            int p = (i - 1) * 2;

            int value =
                    (c[p] << 16)
                    |
                    (
                            p + 1 < c.length
                                    ? c[p + 1]
                                    : 0
                    );

            out[i] = value ^ state;
        }

        return out;
    }
}
