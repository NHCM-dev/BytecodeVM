package nhcm.bytecodevm.Tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import nhcm.bytecodevm.Utils.LogColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public class JarTransformer
{
    private static final Logger logger = LoggerFactory.getLogger(JarTransformer.class);

    public interface Transformer
    {
        void transform(JarContext context);
    }

    public static class JarContext
    {
        public final Map<String, ClassNode> classes = new LinkedHashMap<>();
        public final Map<String, byte[]> resources = new LinkedHashMap<>();

        public ClassNode getClass(String name)
        {
            name = name.replace('.', '/');
            return classes.get(name);
        }

        public boolean hasClass(String name)
        {
            name = name.replace('.', '/');
            return classes.containsKey(name);
        }

        public void addClass(ClassNode cn)
        {
            classes.put(cn.name, cn);
        }

        public void removeClass(String name)
        {
            name = name.replace('.', '/');
            classes.remove(name);
        }

        public void addResource(String name, byte[] bytes)
        {
            resources.put(name, bytes);
        }

        public void removeResource(String name)
        {
            resources.remove(name);
        }
    }

    public static void transformJar(File input, File output, Transformer transformer) throws IOException
    {
        long start = System.nanoTime();
        JarContext context = readJar(input);

        transformer.transform(context);

        writeJar(output, context);
        logger.debug(
                "Transformed {} -> {} in {} ms",
                input.getAbsolutePath(),
                output.getAbsolutePath(),
                (System.nanoTime() - start) / 1_000_000L);
    }

    public static JarContext readJar(File input) throws IOException
    {
        logger.info("{}", LogColors.jarRead("Reading jar: " + LogColors.path(input.getAbsolutePath())));
        JarContext context = new JarContext();

        try (JarFile jarFile = new JarFile(input))
        {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory())
                {
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry))
                {
                    byte[] bytes = readAllBytes(is);

                    if (entry.getName().endsWith(".class"))
                    {
                        ClassReader cr = new ClassReader(bytes);

                        ClassNode cn = new ClassNode();

                        cr.accept(
                                cn,
                                ClassReader.SKIP_FRAMES
                        );

                        context.classes.put(cn.name, cn);
                    }
                    else
                    {
                        context.resources.put(entry.getName(), bytes);
                    }
                }
            }
        }

        logger.info("{}", LogColors.success(
                "Read jar " +
                        LogColors.path(input.getAbsolutePath()) +
                        ": " +
                        LogColors.strong(context.classes.size()) +
                        " class(es), " +
                        LogColors.strong(context.resources.size()) +
                        " resource(s)"));
        return context;
    }

    public static void writeJar(File output, JarContext context) throws IOException
    {
        logger.info("{}", LogColors.jarWrite("Writing jar: " + LogColors.path(output.getAbsolutePath())));
        int classCount = 0;
        int resourceCount = 0;
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output)))
        {
            Set<String> written = new HashSet<>();

            for (ClassNode cn : context.classes.values())
            {
                String entryName = cn.name + ".class";

                if (!written.add(entryName))
                {
                    continue;
                }

                jos.putNextEntry(new JarEntry(entryName));

                ClassWriter cw = new ClassWriter(
                        ClassWriter.COMPUTE_FRAMES |
                        ClassWriter.COMPUTE_MAXS
                );

                cn.accept(cw);

                jos.write(cw.toByteArray());
                jos.closeEntry();
                classCount++;
            }

            for (Map.Entry<String, byte[]> resource : context.resources.entrySet())
            {
                String name = resource.getKey();

                if (!written.add(name))
                {
                    continue;
                }

                jos.putNextEntry(new JarEntry(name));
                jos.write(resource.getValue());
                jos.closeEntry();
                resourceCount++;
            }
        }
        logger.info("{}", LogColors.success(
                "Wrote jar " +
                        LogColors.path(output.getAbsolutePath()) +
                        ": " +
                        LogColors.strong(classCount) +
                        " class(es), " +
                        LogColors.strong(resourceCount) +
                        " resource(s)"));
    }

    private static byte[] readAllBytes(InputStream is) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int len;

        while ((len = is.read(buffer)) != -1)
        {
            baos.write(buffer, 0, len);
        }

        return baos.toByteArray();
    }
}
