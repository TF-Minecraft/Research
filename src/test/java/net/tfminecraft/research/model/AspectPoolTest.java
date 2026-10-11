package net.tfminecraft.research.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AspectPoolTest {
    private static final List<String> POOL = List.of("earth", "nature", "fire", "ice", "water", "air");

    private static OutputDef output(int pick) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("aspects.wealth.required_points", 3);
        config.set("aspect_pool.pick", pick);
        for (int i = 0; i < POOL.size(); i++) {
            config.set("aspect_pool.aspects." + POOL.get(i) + ".required_points", i + 1);
        }
        return new OutputDef("output", config);
    }

    @Test
    void outputsWithoutAPoolDrawNothing() {
        OutputDef plain = new OutputDef("plain", new YamlConfiguration());
        assertEquals(0, plain.getAspectPoolPick());
        assertTrue(plain.getAspectPool().isEmpty());
        assertEquals(List.of(), plain.rollPoolAspects());
        assertEquals(List.of(), plain.reconcilePoolAspects(List.of("fire")));
        assertThrows(UnsupportedOperationException.class, () -> plain.getAspectPool().clear());
    }

    @Test
    void rollsDrawDistinctPoolAspectsAndEventuallyCoverThePool() {
        OutputDef output = output(3);
        Set<String> seen = new HashSet<>();
        for (int roll = 0; roll < 200; roll++) {
            List<String> drawn = output.rollPoolAspects();
            assertEquals(3, drawn.size());
            assertEquals(3, new HashSet<>(drawn).size());
            assertTrue(POOL.containsAll(drawn));
            seen.addAll(drawn);
        }
        assertEquals(new HashSet<>(POOL), seen);
        assertEquals(6, output(9).rollPoolAspects().size());
        assertEquals(List.of(), output(-1).rollPoolAspects());
    }

    @Test
    void recipeIsTheFixedAspectsPlusTheProjectsDraws() {
        OutputDef output = output(2);
        ActiveProject project = new ActiveProject("input", "output", "v.paper");
        assertEquals(List.of("wealth"), List.copyOf(output.getRecipeAspects(null).keySet()));
        project.setPoolAspects(List.of("ice", "removed"));
        assertEquals(List.of("wealth", "ice"), List.copyOf(output.getRecipeAspects(project).keySet()));
        assertEquals(4, output.getRecipeAspects(project).get("ice").getRequiredPoints());
    }

    @Test
    void reconcileKeepsValidDrawsAndTopsUpTheRest() {
        OutputDef output = output(3);
        assertEquals(List.of("ice", "air", "fire"), output.reconcilePoolAspects(List.of("ice", "air", "fire")));
        assertEquals(List.of("ice", "air", "fire"),
                output.reconcilePoolAspects(List.of("ice", "air", "fire", "earth")));
        List<String> toppedUp = output.reconcilePoolAspects(Arrays.asList("ice", "ice", null, "gone"));
        assertEquals(3, toppedUp.size());
        assertEquals("ice", toppedUp.getFirst());
        assertEquals(3, new HashSet<>(toppedUp).size());
        assertTrue(POOL.containsAll(toppedUp));
        assertEquals(3, output.reconcilePoolAspects(null).size());
    }

    @Test
    void projectsKeepDistinctNonBlankDraws() {
        ActiveProject project = new ActiveProject("input", "output", "v.paper");
        assertTrue(project.getPoolAspects().isEmpty());
        project.setPoolAspects(Arrays.asList("fire", null, " ", "fire", "ice"));
        assertEquals(List.of("fire", "ice"), project.getPoolAspects());
        assertThrows(UnsupportedOperationException.class, () -> project.getPoolAspects().clear());
        project.setPoolAspects(null);
        assertTrue(project.getPoolAspects().isEmpty());
    }
}
