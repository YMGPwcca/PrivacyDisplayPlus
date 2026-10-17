package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class TemporaryTasksTest {
    @Test public void explicitHandleOffDiffersFromFollowingSamsungDefaults() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        sessions.override(task, 5, TemporaryTasks.OFF);
        assertFalse(sessions.protects(task, 5));
        assertEquals(TemporaryTasks.OFF, sessions.decision(task, 5));
        assertEquals(0, sessions.count());
        assertEquals(1, sessions.overrideCount());
        sessions.set(task, 5, false);
        assertEquals(TemporaryTasks.FOLLOW, sessions.decision(task, 5));
        assertEquals(0, sessions.overrideCount());
    }

    @Test public void handleOffDoesNotLeakToAnotherTaskOrSurviveAWindowModeChange() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object(), other = new Object();
        sessions.override(task, 5, TemporaryTasks.OFF);
        assertEquals(TemporaryTasks.FOLLOW, sessions.decision(other, 5));
        assertEquals(TemporaryTasks.FOLLOW, sessions.decision(task, 6));
        assertEquals(TemporaryTasks.FOLLOW, sessions.decision(task, 5));
    }

    @Test public void closingTaskClearsHandleOffAndReopeningHasNoOverride() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        sessions.override(task, 5, TemporaryTasks.OFF);
        sessions.remove(task);
        assertEquals(0, sessions.overrideCount());
        assertEquals(TemporaryTasks.FOLLOW, sessions.offer(task, 5).override());
    }

    @Test public void onAfterOffReusesOnlyTheSameLiveTaskSession() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        String token = sessions.offer(task, 5).token();
        sessions.override(task, 5, TemporaryTasks.OFF);
        sessions.override(task, 5, TemporaryTasks.ON);
        assertTrue(sessions.protects(task, 5));
        assertEquals(token, sessions.offer(task, 5).token());
        assertEquals(1, sessions.count());
    }

    @Test public void merelyListingAWindowNeverEnablesProtection() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        sessions.offer(task, 5);
        assertFalse(sessions.protects(task, 5));
        assertEquals(0, sessions.count());
    }

    @Test public void selectionBindsToTheSameObjectAndNotAReplacementTask() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object original = new Object(), replacement = new Object();
        String token = sessions.offer(original, 5).token();
        sessions.set(original, 5, true);
        assertSame(original, sessions.resolve(token));
        assertTrue(sessions.protects(original, 5));
        assertFalse(sessions.protects(replacement, 5));
        assertNotEquals(token, sessions.offer(replacement, 5).token());
    }

    @Test public void leavingTheOriginalModeEndsSessionPermanently() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        String old = sessions.offer(task, 6).token();
        sessions.set(task, 6, true);
        assertFalse(sessions.protects(task, 1));
        assertFalse(sessions.protects(task, 6));
        assertNull(sessions.resolve(old));
        assertNotEquals(old, sessions.offer(task, 6).token());
    }

    @Test public void switchingDirectlyBetweenFloatingAndSplitRequiresANewSelection() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        sessions.set(task, 5, true);
        assertFalse(sessions.protects(task, 6));
        assertFalse(sessions.protects(task, 5));
    }

    @Test public void removalInvalidatesOpaqueHandleAndDoesNotProtectAnotherWindow() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object(), other = new Object();
        String old = sessions.offer(task, 5).token();
        sessions.set(task, 5, true);
        sessions.set(other, 6, true);
        sessions.remove(task);
        assertNull(sessions.resolve(old));
        assertFalse(sessions.protects(task, 5));
        assertTrue(sessions.protects(other, 6));
        assertEquals(1, sessions.count());
    }

    @Test public void stoppingAndRelistingKeepsSelectionOff() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        String token = sessions.offer(task, 5).token();
        sessions.set(task, 5, true);
        sessions.set(task, 5, false);
        assertFalse(sessions.offer(task, 5).protectedNow());
        assertSame(task, sessions.resolve(token));
    }

    @Test public void processReloadNeverRestoresTemporaryRequests() {
        Object task = new Object();
        TemporaryTasks before = new TemporaryTasks();
        String token = before.offer(task, 6).token();
        before.set(task, 6, true);
        TemporaryTasks after = new TemporaryTasks();
        assertNull(after.resolve(token));
        assertFalse(after.protects(task, 6));
        assertEquals(0, after.count());
    }

    @Test public void staleTokenCannotSelectSameTaskAfterModeChangeAndRelisting() {
        TemporaryTasks sessions = new TemporaryTasks();
        Object task = new Object();
        String old = sessions.offer(task, 5).token();
        sessions.offer(task, 6);
        assertNull(sessions.resolve(old));
    }
}
