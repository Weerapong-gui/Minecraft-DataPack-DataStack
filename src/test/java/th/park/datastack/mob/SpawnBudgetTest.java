package th.park.datastack.mob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpawnBudgetTest {
    @Test void briefCases() {
        assertTrue(SpawnBudget.allows(49, 50, 1));
        assertFalse(SpawnBudget.allows(50, 50, 1));
        assertTrue(SpawnBudget.allows(90, 50, 2));
        assertFalse(SpawnBudget.allows(100, 50, 2));
    }
    @Test void noPlayersCountsAsOne() {
        assertTrue(SpawnBudget.allows(49, 50, 0));
        assertFalse(SpawnBudget.allows(50, 50, 0));
    }
    @Test void despawnKeepsRestOnlyForRandomDespawnNearAPlayer() {
        double hard = 128;
        assertTrue(SpawnBudget.despawnKeepsRest(50 * 50, hard, false));      // random despawn 32..128
        assertTrue(SpawnBudget.despawnKeepsRest(127.9 * 127.9, hard, false));
        assertFalse(SpawnBudget.despawnKeepsRest(129 * 129, hard, false));   // instant despawn: vanilla drops all
        assertFalse(SpawnBudget.despawnKeepsRest(Double.POSITIVE_INFINITY, hard, false)); // no player
        assertFalse(SpawnBudget.despawnKeepsRest(10, hard, true));          // peaceful purge
    }
    @Test void persistentMobsDoNotCountForCap() {
        assertTrue(SpawnBudget.countsForCap(true));
        assertFalse(SpawnBudget.countsForCap(false));
    }
    @Test void hardRangeFromConfig() {
        assertEquals(128.0, SpawnBudget.hardRange("default"));
        assertEquals(128.0, SpawnBudget.hardRange(null));
        assertEquals(96.0, SpawnBudget.hardRange(96));
        assertEquals(64.0, SpawnBudget.hardRange("64"));
        assertEquals(128.0, SpawnBudget.hardRange(-1));
        assertEquals("monster", SpawnBudget.paperCategoryKey("MONSTER"));
        assertEquals("creature", SpawnBudget.paperCategoryKey("ANIMAL"));
        assertEquals("underground_water_creature", SpawnBudget.paperCategoryKey("WATER_UNDERGROUND_CREATURE"));
    }
}
