package nhcm.bytecodevm.Utils.Collection;

import java.util.HashMap;
import java.util.Map;

public class BiMap<K, V>
{
    private final Map<K, V> forward = new HashMap<>();
    private final Map<V, K> backward = new HashMap<>();

    public void put(K key, V value)
    {
        if(forward.containsKey(key))
        {
            throw new IllegalArgumentException("Duplicate key");
        }

        if(backward.containsKey(value))
        {
            throw new IllegalArgumentException("Duplicate value");
        }

        forward.put(key, value);
        backward.put(value, key);
    }

    public V get(K key)
    {
        return forward.get(key);
    }

    public K getKey(V value)
    {
        return backward.get(value);
    }

    public boolean containsKey(K key)
    {
        return forward.containsKey(key);
    }

    public boolean containsValue(V value)
    {
        return backward.containsKey(value);
    }
}