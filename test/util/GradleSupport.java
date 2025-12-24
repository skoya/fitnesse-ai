package util;

import java.io.File;

public final class GradleSupport {
    public static final String CLASSES_DIR = "build/classes/java/main" + File.pathSeparator
        + "lib" + File.separator + "*";
    public static final String TEST_CLASSES_DIR = "build/classes/java/test";

    public static String javaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isEmpty()) {
            return "java";
        }
        return javaHome + File.separator + "bin" + File.separator + "java";
    }

    private GradleSupport() {
    }
}
