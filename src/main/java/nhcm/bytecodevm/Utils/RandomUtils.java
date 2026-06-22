package nhcm.bytecodevm.Utils;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomUtils
{
    private static final SecureRandom RANDOM = new SecureRandom();

    public static int randomInt()
    {
        return RANDOM.nextInt();
    }

    public static int randomInt(int bound)
    {
        if (bound <= 0)
        {
            throw new IllegalArgumentException("bound must be positive");
        }

        return RANDOM.nextInt(bound);
    }

    public static int randomInt(int min, int max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("min > max");
        }

        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE)
        {
            return randomInt();
        }

        return RANDOM.nextInt(max - min + 1) + min;
    }

    public static boolean randomBoolean()
    {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static <T> void shuffle(List<T> list)
    {
        Collections.shuffle(list, RANDOM);
    }
}