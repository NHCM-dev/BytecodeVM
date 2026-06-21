package nhcm.bytecodevm.Test;

public class TestClass
{
    public int add(int a, int b)
    {
        int c = a + b;
        return c;
    }

    public String hello(String name)
    {
        String str = "Hello, " + name;
        System.out.println(str);
        return str;
    }
}
