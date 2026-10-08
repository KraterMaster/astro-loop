package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.PowerUp
import com.astroloop.game.entity.PowerUpType
import com.astroloop.game.entity.Ship
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepairKitTest {

    private lateinit var state: GameState
    private lateinit var ship: Ship

    @Before
    fun setup() {
        state = GameState()
        state.reset()
        state.survivalTime = 100f
        ship = Ship()
        ship.maxHealth = 50f
        ship.health = 25f
    }

    // ─── Drop rule ───────────────────────────────────────────────────

    @Test
    fun `no drop at full hull even on a perfect roll`() {
        ship.health = ship.maxHealth
        assertFalse(shouldDropRepairKit(state, ship, 0f))
    }

    @Test
    fun `drops when damaged and the roll is under the chance`() {
        assertTrue(shouldDropRepairKit(state, ship, 0f))
    }

    @Test
    fun `no drop when the roll is above the chance`() {
        assertFalse(shouldDropRepairKit(state, ship, 0.99f))
    }

    @Test
    fun `lower hull makes a drop likelier`() {
        // A roll between the two chances: drops at near-death, not at a scratch.
        val scratch = GameConfig.REPAIR_KIT_DROP_CHANCE +
            GameConfig.REPAIR_KIT_MISSING_HP_BONUS * 0.02f
        val roll = scratch + 0.001f

        ship.health = ship.maxHealth * 0.98f
        assertFalse(shouldDropRepairKit(state, ship, roll))

        ship.health = 1f
        assertTrue(shouldDropRepairKit(state, ship, roll))
    }

    @Test
    fun `cooldown blocks a second drop`() {
        state.lastRepairKitDropTime = state.survivalTime - (GameConfig.REPAIR_KIT_COOLDOWN - 1f)
        assertFalse(shouldDropRepairKit(state, ship, 0f))

        state.lastRepairKitDropTime = state.survivalTime - GameConfig.REPAIR_KIT_COOLDOWN
        assertTrue(shouldDropRepairKit(state, ship, 0f))
    }

    @Test
    fun `no drop in corruption runs`() {
        state.isCorruptionRun = true
        assertFalse(shouldDropRepairKit(state, ship, 0f))
    }

    @Test
    fun `state reset clears the cooldown clock`() {
        state.lastRepairKitDropTime = 99f
        state.reset()
        assertEquals(-1000f, state.lastRepairKitDropTime, 0f)
    }

    // ─── Healing ─────────────────────────────────────────────────────

    @Test
    fun `kit heals the configured fraction of max HP`() {
        val healed = applyRepairKit(ship)

        val expected = ship.maxHealth * GameConfig.REPAIR_KIT_HEAL_FRACTION
        assertEquals(expected, healed, 0.001f)
        assertEquals(25f + expected, ship.health, 0.001f)
    }

    @Test
    fun `kit never heals above max HP`() {
        ship.health = ship.maxHealth - 1f
        val healed = applyRepairKit(ship)

        assertEquals(1f, healed, 0.001f)
        assertEquals(ship.maxHealth, ship.health, 0f)
    }

    @Test
    fun `kit does not touch shields`() {
        ship.currentShield = 3f
        applyRepairKit(ship)
        assertEquals(3f, ship.currentShield, 0f)
    }

    @Test
    fun `kit does not revive a destroyed ship`() {
        ship.health = 0f
        ship.isActive = false

        assertEquals(0f, applyRepairKit(ship), 0f)
        assertEquals(0f, ship.health, 0f)
    }

    // ─── Entity ──────────────────────────────────────────────────────

    @Test
    fun `initializeAsRepairKit sets the type and clears stale state`() {
        val kit = PowerUp()
        kit.initialize(1f, 1f, PowerUpType.WEAPON, "pulse_cannon")
        kit.startFadeOut()

        kit.initializeAsRepairKit(5f, 6f)

        assertEquals(PowerUpType.REPAIR_KIT, kit.type)
        assertEquals("", kit.itemId)
        assertTrue(kit.isActive)
        assertEquals(1f, kit.getFadeAlpha(), 0f)
        assertEquals(5f, kit.position.x, 0f)
        assertEquals(6f, kit.position.y, 0f)
    }
}
