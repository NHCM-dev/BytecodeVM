package nhcm.bytecodevm.Tools;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.Collection.BiMap;
import nhcm.bytecodevm.Utils.RandomUtils;

import java.util.*;

public class OpcMutator
{
    private final BiMap<Integer, Opcs> opcodesBindIntegerMap = new BiMap<>();

    public final MutateStrategy strategy;

    public OpcMutator(MutateStrategy strategy)
    {
        this.strategy = strategy == null ? MutateStrategy.RESORT : strategy;

        switch (this.strategy)
        {
            case RANDOM_INT:
                mutateRandomInt();
                break;
            case RESORT:
                mutateResort();
                break;
            default:
                mutateNone();
                break;
        }
    }

    private void mutateRandomInt()
    {
        for (Opcs opcs : Opcs.values())
        {
            int randomInt;

            do
            {
                randomInt = RandomUtils.randomInt();
            }
            while (opcodesBindIntegerMap.containsKey(randomInt));

            opcodesBindIntegerMap.put(randomInt, opcs);
        }
    }

    private void mutateResort()
    {
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < Opcs.values().length; i++)
        {
            ids.add(i);
        }

        RandomUtils.shuffle(ids);

        int index = 0;

        for (Opcs opcs : Opcs.values())
        {
            opcodesBindIntegerMap.put(ids.get(index++), opcs);
        }
    }

    private void mutateNone()
    {
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < Opcs.values().length; i++)
        {
            ids.add(i);
        }

        int index = 0;

        for (Opcs opcs : Opcs.values())
        {
            opcodesBindIntegerMap.put(ids.get(index++), opcs);
        }
    }

    public Opcs fromMutated(int mutated)
    {
        return opcodesBindIntegerMap.get(mutated);
    }

    public int toMutated(Opcs opcs)
    {
        Integer mutated = opcodesBindIntegerMap.getKey(opcs);

        if (mutated == null)
        {
            throw new IllegalArgumentException("Unknown opcode: " + opcs);
        }

        return mutated;
    }

    public boolean contains(int mutated)
    {
        return opcodesBindIntegerMap.containsKey(mutated);
    }

    public static OpcMutator fromStrategy(MutateStrategy strategy)
    {
        return new OpcMutator(strategy);
    }

    public enum MutateStrategy
    {
        NONE,
        RESORT,
        RANDOM_INT;

        public OpcMutator getMutator()
        {
            return new OpcMutator(this);
        }
    }
}
