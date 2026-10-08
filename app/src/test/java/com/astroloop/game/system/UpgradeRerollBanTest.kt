package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.data.PassiveDefinitions
import com.astroloop.game.data.WeaponDefinitions
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.PowerUp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpgradeRerollBanTest {

    private lateinit var upgradeSystem: UpgradeSystem
    private lateinit var state: GameState

    @Before
    fun setup() {
        upgradeSystem = UpgradeSystem(EntityPool({ PowerUp() }, 10))
        state = GameState()
        state.reset()
        upgradeSystem.unlockedWeaponIds = WeaponDefinitions.getBaseWeapons().map { it.id }.toSet()
        upgradeSystem.unlockedPassiveIds = PassiveDefinitions.getAllPassives().map { it.id }.toSet()
        upgradeSystem.resetRunModifiers()
    }

    @Test
    fun `reroll costs a charge and keeps the offer size`() {
        upgradeSystem.generateUpgradeOptions(state)
        assertTrue(upgradeSystem.reroll(state))

        assertEquals(GameConfig.UPGRADE_REROLLS_PER_RUN - 1, upgradeSystem.rerollsLeft)
        assertEquals(GameConfig.UPGRADE_CHOICES, upgradeSystem.getPendingOptions().size)
    }

    @Test
    fun `reroll stops when charges run out`() {
        upgradeSystem.generateUpgradeOptions(state)
        repeat(GameConfig.UPGRADE_REROLLS_PER_RUN) { assertTrue(upgradeSystem.reroll(state)) }

        assertFalse(upgradeSystem.canReroll())
        assertFalse(upgradeSystem.reroll(state))
    }

    @Test
    fun `reroll prefers cards that were not just shown`() {
        val before = upgradeSystem.generateUpgradeOptions(state).map { it.id }.toSet()
        upgradeSystem.reroll(state)
        val after = upgradeSystem.getPendingOptions().map { it.id }.toSet()

        assertTrue("pool is large enough that a reroll should not repeat any card", before.intersect(after).isEmpty())
    }

    @Test
    fun `ban removes the card, swaps in a different one and costs a ban`() {
        val before = upgradeSystem.generateUpgradeOptions(state)
        val target = before[1]
        assertTrue(upgradeSystem.banOption(1, state))

        val after = upgradeSystem.getPendingOptions()
        assertEquals(before.size, after.size)
        assertFalse(after.any { it.id == target.id })
        assertEquals(before[0].id, after[0].id)
        assertEquals(before[2].id, after[2].id)
        assertEquals(GameConfig.UPGRADE_BANS_PER_RUN - 1, upgradeSystem.bansLeft)
        assertTrue(target.id in upgradeSystem.bannedIds)
    }

    @Test
    fun `a banned option never appears in later selections`() {
        val first = upgradeSystem.generateUpgradeOptions(state)
        val bannedId = first[0].id
        upgradeSystem.banOption(0, state)

        repeat(200) {
            val options = upgradeSystem.generateUpgradeOptions(state)
            assertFalse(options.any { it.id == bannedId })
        }
    }

    @Test
    fun `cannot ban with no charges left`() {
        upgradeSystem.generateUpgradeOptions(state)
        repeat(GameConfig.UPGRADE_BANS_PER_RUN) { assertTrue(upgradeSystem.banOption(0, state)) }

        assertFalse(upgradeSystem.canBan())
        assertFalse(upgradeSystem.banOption(0, state))
        assertFalse(upgradeSystem.toggleBanMode())
    }

    @Test
    fun `ban mode toggles and is cleared by picking`() {
        upgradeSystem.generateUpgradeOptions(state)
        assertTrue(upgradeSystem.toggleBanMode())
        assertTrue(upgradeSystem.banMode)

        upgradeSystem.selectOption(0)
        assertFalse(upgradeSystem.banMode)
    }

    @Test
    fun `resetRunModifiers restores charges and clears bans`() {
        upgradeSystem.generateUpgradeOptions(state)
        upgradeSystem.reroll(state)
        upgradeSystem.banOption(0, state)

        upgradeSystem.resetRunModifiers()

        assertEquals(GameConfig.UPGRADE_REROLLS_PER_RUN, upgradeSystem.rerollsLeft)
        assertEquals(GameConfig.UPGRADE_BANS_PER_RUN, upgradeSystem.bansLeft)
        assertTrue(upgradeSystem.bannedIds.isEmpty())
    }

    @Test
    fun `ban is refused rather than leaving the player with no options`() {
        // Only one weapon and no passives available -> a single card; banning it must be refused.
        upgradeSystem.unlockedWeaponIds = setOf("pulse_cannon")
        upgradeSystem.unlockedPassiveIds = emptySet()
        state.reset()
        val options = upgradeSystem.generateUpgradeOptions(state)
        assertEquals(1, options.size)

        assertFalse(upgradeSystem.banOption(0, state))
        assertEquals(1, upgradeSystem.getPendingOptions().size)
        assertEquals(GameConfig.UPGRADE_BANS_PER_RUN, upgradeSystem.bansLeft)
        assertTrue(upgradeSystem.bannedIds.isEmpty())
    }
}
