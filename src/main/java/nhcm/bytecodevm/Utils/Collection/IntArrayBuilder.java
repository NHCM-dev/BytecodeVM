package nhcm.bytecodevm.Utils.Collection;

import java.util.Arrays;

public class IntArrayBuilder
{
    private static final int DEFAULT_CAPACITY = 16;

    private int[] values;
    private int size;

    public IntArrayBuilder()
    {
        this(DEFAULT_CAPACITY);
    }

    public IntArrayBuilder(int initialCapacity)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("initialCapacity cannot be negative");
        }

        values = new int[initialCapacity];
    }

    public IntArrayBuilder add(int value)
    {
        ensureCapacity(size + 1);
        values[size++] = value;
        return this;
    }

    public IntArrayBuilder addAll(int... newValues)
    {
        if (newValues == null)
        {
            throw new IllegalArgumentException("newValues cannot be null");
        }

        ensureCapacity(size + newValues.length);
        System.arraycopy(newValues, 0, values, size, newValues.length);
        size += newValues.length;
        return this;
    }

    public int get(int index)
    {
        checkIndex(index);
        return values[index];
    }

    public void set(int index, int value)
    {
        checkIndex(index);
        values[index] = value;
    }

    public int size()
    {
        return size;
    }

    public boolean isEmpty()
    {
        return size == 0;
    }

    public int[] toArray()
    {
        return Arrays.copyOf(values, size);
    }

    private void ensureCapacity(int requiredCapacity)
    {
        if (requiredCapacity <= values.length)
        {
            return;
        }

        int grownCapacity = values.length == 0
                ? DEFAULT_CAPACITY
                : values.length + (values.length >> 1);

        if (grownCapacity < requiredCapacity)
        {
            grownCapacity = requiredCapacity;
        }

        values = Arrays.copyOf(values, grownCapacity);
    }

    private void checkIndex(int index)
    {
        if (index < 0 || index >= size)
        {
            throw new IndexOutOfBoundsException(
                    "index=" + index + ", size=" + size);
        }
    }
}
