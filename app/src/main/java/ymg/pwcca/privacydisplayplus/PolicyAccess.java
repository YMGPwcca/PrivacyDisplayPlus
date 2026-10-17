package ymg.pwcca.privacydisplayplus;

import java.util.List;
import java.util.Set;

/** Small seam for verifying routing and recovery without booting a hooked system server. */
interface PolicyAccess {
    default String windowPackage(Object window) throws ReflectiveOperationException { return null; }
    default int userId(Object task) throws ReflectiveOperationException { return 0; }
    Object controller(Object window) throws ReflectiveOperationException;
    Object lock(Object controller) throws ReflectiveOperationException;
    Object task(Object window) throws ReflectiveOperationException;
    int displayId(Object window) throws ReflectiveOperationException;
    int windowingMode(Object task) throws ReflectiveOperationException;
    boolean splitScreen(Object task) throws ReflectiveOperationException;
    boolean leafTask(Object task) throws ReflectiveOperationException;
    Object bufferSurface(Object window) throws ReflectiveOperationException;
    boolean taskPrivacyRequested(Object task) throws ReflectiveOperationException;
    SplitPrivacyRegion privacyRegion(Object window) throws ReflectiveOperationException;
    void queuePrivacyRegion(Object window, Object surface, SplitPrivacyRegion region) throws ReflectiveOperationException;
    Object surface(Object task) throws ReflectiveOperationException;
    boolean validSurface(Object surface) throws ReflectiveOperationException;
    int mode(Object controller) throws ReflectiveOperationException;
    int option(Object controller) throws ReflectiveOperationException;
    int samsungFlags(Object window) throws ReflectiveOperationException;
    int appPolicy(Object controller) throws ReflectiveOperationException;
    void appPolicy(Object controller, int value) throws ReflectiveOperationException;
    String packageName(Object controller) throws ReflectiveOperationException;
    void packageName(Object controller, String value) throws ReflectiveOperationException;
    List<?> requests(Object controller) throws ReflectiveOperationException;
    Set<Object> partialTasks(Object controller) throws ReflectiveOperationException;
}
