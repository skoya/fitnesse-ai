package fitnesse.slim.instructions;

public class SystemExitSecurityManager {

  public static final String PREVENT_SYSTEM_EXIT = "prevent.system.exit";
  private static final boolean RETIRED = true;

  public static boolean isRetired() {
    return RETIRED;
  }

  /**
   * The {@link SystemExitSecurityManager} overrides the behavior of the wrapped
   * original {@link SecurityManager} to prevent {@link System#exit(int)} calls
   * from being executed.
   *
   * @author Anis Ben Hamidene
   *
   */

  /**
   * Replaces the current {@link SecurityManager} with a
   * {@link SystemExitSecurityManager}.
   */
  public static void activateIfWanted() {
    // Retired: JDK ≥ 18 removes SecurityManager. Keep this a no-op to avoid
    // IllegalStateException while preserving backward compatibility.
  }

  public static void restoreOriginalSecurityManager() {
  }

  private static boolean isPreventSystemExit() {
    String preventSystemExitString = System.getProperty(PREVENT_SYSTEM_EXIT);
    if (preventSystemExitString != null) {
      return Boolean.parseBoolean(preventSystemExitString);
    } else {
      return false;
    }
  }

  private static boolean isAndroid() {
    String vendorUrl = System.getProperty("java.vendor.url", "");
    return vendorUrl.toLowerCase().contains("android");
  }

  public static class SystemExitException extends SecurityException {

    public SystemExitException(String message) {
      super(message);
    }

    private static final long serialVersionUID = 2584644457111168436L;

  }

}
