package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RootOperationStateTest {
    @Test public void definiteFailureIsAvailableToRecreatedScreenAndAllowsRetry() {
        RootOperationState state = new RootOperationState();
        assertTrue(state.begin());
        List<RootCommandRunner.Result> first = new ArrayList<>();
        Runnable oldScreen = () -> first.add(state.result());
        state.add(oldScreen);
        state.remove(oldScreen);
        state.finish(RootCommandRunner.Result.DENIED);
        List<RootCommandRunner.Result> recreated = new ArrayList<>();
        state.add(() -> recreated.add(state.result()));
        assertEquals(RootCommandRunner.Result.DENIED, recreated.get(0));
        assertFalse(state.isRunning());
        assertFalse(state.isPending());
        assertTrue(state.begin());
        assertEquals(1, first.size());
    }

    @Test public void recreatedScreenReceivesResultOfOperationStartedByOldScreen() {
        RootOperationState state = new RootOperationState();
        assertTrue(state.begin());
        List<RootCommandRunner.Result> results = new ArrayList<>();
        state.add(() -> results.add(state.result()));
        state.finish(RootCommandRunner.Result.UNSUPPORTED);
        assertEquals(RootCommandRunner.Result.UNSUPPORTED, results.get(results.size() - 1));
        assertFalse(state.isPending());
    }

    @Test public void timeoutSurvivesScreenRecreationAndPreventsDuplicateRequest() {
        RootOperationState state = new RootOperationState();
        assertTrue(state.begin());
        state.finish(RootCommandRunner.Result.TIMED_OUT);
        assertEquals(RootCommandRunner.Result.TIMED_OUT, state.result());
        assertTrue(state.isPending());
        assertFalse(state.begin());
    }

    @Test public void acceptedRequestRemainsPendingUntilProcessRestart() {
        RootOperationState state = new RootOperationState();
        assertTrue(state.begin());
        assertFalse(state.begin());
        state.finish(RootCommandRunner.Result.REQUESTED);
        assertFalse(state.isRunning());
        assertTrue(state.isPending());
        assertFalse(state.begin());
    }
}
