package net.tfminecraft.research.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActiveProjectTest {
    @Test
    void largePositiveAwardsSaturateAtTheRecipeCapWithoutWrapping() {
        ActiveProject project = new ActiveProject("input", "output", "v.paper");
        project.addAspectPoints("fire", 1, Integer.MAX_VALUE);
        project.addAspectPoints("fire", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, project.getAspectPoints("fire"));
        project.addAspectPoints("fire", 1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, project.getAspectPoints("fire"));
    }

    @Test
    void tracksIdentityDiscoveryAndCompletionIndependently() {
        ActiveProject project = new ActiveProject("input", "output", "v.paper");
        assertEquals("input", project.getInputId());
        assertEquals("output", project.getResolvedOutputId());
        assertEquals("output", project.getProjectId());
        assertEquals("v.paper", project.getResolvedResultRef());
        assertFalse(project.isProductRevealed());
        assertFalse(project.isCompleted());
        project.setProductRevealed(true);
        assertTrue(project.isProductRevealed());
        assertFalse(project.isCompleted());
        project.setCompleted(true);
        assertTrue(project.isCompleted());
        assertTrue(new ActiveProject("i", "o", "r", true).isProductRevealed());
    }

    @Test
    void invalidAndRejectedAwardsLeaveProgressUntouched() {
        ActiveProject project = new ActiveProject("i", "o", "r");
        for (String id : Arrays.asList(null, "", " ")) {
            project.addAspectPoints(id, 10, 20);
            project.incrementAspectTestCount(id);
            project.markTested(id);
            project.setAspectState(id, AspectState.CONFIRMED);
        }
        project.addAspectPoints("fire", 0, 20);
        project.addAspectPoints("fire", -1, 20);
        project.addAspectPoints("fire", 10, 0);
        project.setAspectState("fire", null);
        assertTrue(project.getAspectPoints().isEmpty());
        assertTrue(project.getAspectTestCounts().isEmpty());
        assertTrue(project.getTestedExperimentItems().isEmpty());
        assertTrue(project.getAspectStates().isEmpty());
        project.setAspectState("fire", AspectState.REJECTED);
        project.addAspectPoints("fire", 10, 20);
        assertEquals(0, project.getAspectPoints("fire"));
        project.setAspectState("fire", AspectState.TESTING);
        project.addAspectPoints("fire", 3, 5);
        project.addAspectPoints("fire", 3, 5);
        assertEquals(5, project.getAspectPoints("fire"));
        project.incrementAspectTestCount("fire");
        project.incrementAspectTestCount("fire");
        assertEquals(2, project.getAspectTestCount("fire"));
        assertEquals(0, project.getAspectTestCount("missing"));
        assertFalse(project.hasTestedItem("sample"));
        project.markTested("sample");
        project.markTested("sample");
        assertTrue(project.hasTestedItem("sample"));
        assertEquals(1, project.getTestedExperimentItems().size());
    }

    @Test
    void restoredUnknownStateAndSlotOrderAreHandledSafely() {
        ActiveProject project = new ActiveProject("i", "o", "r");
        assertEquals(AspectState.UNKNOWN, project.getAspectState("missing"));
        project.getAspectStates().put("blank", " ");
        project.getAspectStates().put("invalid", "obsolete");
        assertEquals(AspectState.UNKNOWN, project.getAspectState("blank"));
        assertEquals(AspectState.UNKNOWN, project.getAspectState("invalid"));
        project.setAspectState("valid", AspectState.CONFIRMED);
        assertEquals(AspectState.CONFIRMED, project.getAspectState("valid"));
        assertFalse(project.hasAspectSlotOrder());
        project.setAspectSlotOrder(Arrays.asList("one", null, "three", "four", "five", "six"));
        assertTrue(project.hasAspectSlotOrder());
        assertEquals(List.of("one", "", "three", "four", "five", "six"), project.getAspectSlotOrder());
        project.setAspectSlotOrder(null);
        assertTrue(project.getAspectSlotOrder().isEmpty());
        assertFalse(project.hasAspectSlotOrder());
    }
}
