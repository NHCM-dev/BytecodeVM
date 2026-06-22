package nhcm.bytecodevm.Test;

public class TestClass
{
    public static void main(String[] args)
    {
        System.out.println("Hello, World!");
    }

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
