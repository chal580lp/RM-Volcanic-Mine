package com.runemate.volcanic

import com.runemate.game.api.client.ClientUI
import com.runemate.game.api.hybrid.Environment
import com.runemate.game.api.hybrid.RuneScape
import com.runemate.game.api.hybrid.local.Skill
import com.runemate.game.api.hybrid.local.hud.interfaces.Bank
import com.runemate.game.api.hybrid.local.hud.interfaces.ChatDialog
import com.runemate.game.api.hybrid.local.hud.interfaces.Equipment
import com.runemate.game.api.hybrid.local.hud.interfaces.Inventory
import com.runemate.game.api.hybrid.region.GameObjects
import com.runemate.game.api.hybrid.region.Players
import com.runemate.game.api.hybrid.util.Regex
import com.runemate.game.api.script.Execution
import com.runemate.game.api.script.framework.LoopingBot
import com.runemate.game.api.script.framework.listeners.SettingsListener
import com.runemate.game.api.script.framework.listeners.events.SettingChangedEvent
import com.runemate.ui.DefaultUI
import com.runemate.ui.setting.annotation.open.SettingsProvider
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import java.util.regex.Pattern


class Bot : LoopingBot(), SettingsListener {
	private val log = getLogger("Bot")
	private val volcanicMine = VolcanicMine(this)

	@SettingsProvider(updatable = true)
	lateinit var settings: VolcanicSettings


	private var started = false
	private var volcanicMineState = VolcanicMineState.RESUPPLY

	private var paidForEntry = true

	private var foodName: String = ""
	private val staminaPotion = Regex.getPatternForStartsWith("Stamina potion")
	private val prayerPotion = Regex.getPatternForStartsWith("Prayer potion")
	private val payNumulites = "Pay 30 Numulites for one visit."

	private fun validateSettings(): Boolean {
		val food = Inventory.newQuery().actions("Eat").results().firstOrNull()
		food?.definition?.let {
			foodName = it.name
			log.info("Food set to: $foodName")
		} ?: run {
			pauseAndWarn("No food found in inventory")
			return false
		}

		if (Skill.MINING.baseLevel < 50) {
			stopAndWarn("Mining level is too low")
			return false
		}
		if (Skill.PRAYER.baseLevel < 40) {
			stopAndWarn("Prayer level is too low need level 40 for protect from missiles")
			return false
		}
		val pickaxe = Regex.getPatternForContainsString("pickaxe")
		if (!Inventory.contains(pickaxe) && !Equipment.contains(pickaxe)) {
			pauseAndWarn("No pickaxe found")
			return false
		}
		return true
	}

	fun stopAndWarn(reason: String) {
		ClientUI.showError("Stopping Script: $reason")
		stop(reason)
	}

	fun pauseAndWarn(reason: String) {
		ClientUI.showAlert(ClientUI.AlertLevel.WARN, "Pausing Script: $reason")
		pause(reason)
	}


	override fun onStart(vararg arguments: String?) {
		eventDispatcher.addListener(this)
		addInfoLabel()
	}

	override fun onLoop() {
		if (isPaused || !started) return
		if (volcanicMine.inVolcanicMine() && volcanicMineState != VolcanicMineState.VOLCANIC_MINE) {
			log.debug("In Volcanic Mine with state: $volcanicMineState")
			volcanicMineState = VolcanicMineState.VOLCANIC_MINE
		}
		when (volcanicMineState) {
			VolcanicMineState.RESUPPLY -> resupply()
			VolcanicMineState.ENTER -> enterVolcanicMine()
			VolcanicMineState.VOLCANIC_MINE -> {
				if (!volcanicMine.inVolcanicMine()) {
					volcanicMineState = VolcanicMineState.RESUPPLY
					volcanicMine.reset()
					return
				}
				volcanicMine.execute()
			}
		}
	}

	private fun checkStopConditions() {
		val stopMinutes = settings.stopMinutes()
		if (stopMinutes > 0 && Environment.getRuntime(this) >= stopMinutes * 60 * 1000) {
			log.info("Stop minutes: $stopMinutes minutes")
			stop("Script has been running for $stopMinutes minutes")
			ClientUI.showAlert("Script has been running for $stopMinutes minutes, stopping.")
		}
		val stopLevel = settings.stopLevel()
		if (stopLevel > 50 && Skill.MINING.baseLevel >= stopLevel) {
			log.info("Stop level: $stopLevel")
			stop("Mining level has reached $stopLevel")
			ClientUI.showAlert("Mining level has reached $stopLevel, stopping.")
		}

	}

	private fun resupply() {
		checkStopConditions()
		DefaultUI.setStatus("Resupplying")
		val foodCount = Inventory.getQuantity(foodName)
		when {
			foodCount < 5 -> {
				log.debug("Withdrawing food because count: $foodCount < 5")
				val foodAmount = settings.foodAmount()
				withdrawItem(foodName, foodAmount - foodCount)
			}

			settings.useStamina() && !Inventory.contains(staminaPotion) -> {
				log.debug("Withdrawing stamina potion")
				withdrawItem(staminaPotion, 1)
			}

			!Inventory.contains(prayerPotion) -> {
				log.debug("Withdrawing prayer potion")
				withdrawItem(prayerPotion, 1)
			}

			!paidForEntry && Inventory.getQuantity("Numulite") < 30 -> {
				log.debug("Withdrawing numulites")
				withdrawItem("Numulite", 30)
			}

			Bank.isOpen() -> Bank.close()
			else -> {
				volcanicMineState = VolcanicMineState.ENTER
			}
		}
	}

	private fun withdrawItem(itemName: String, amount: Int) {
		withdrawItem(Regex.getPattern(itemName), amount)
	}

	private fun withdrawItem(itemName: Pattern, amount: Int) {
		log.info("Withdrawing $amount of ${itemName}")
		if (Bank.isOpen()) {
			if (!Bank.contains(itemName)) {
				stopAndWarn("Item: $itemName not found in bank")
				return
			}
			Bank.withdraw(itemName, amount)
		} else {
			Bank.open()
			Execution.delayUntil({ Bank.isOpen() }, 2400)
		}
	}

	private fun enterVolcanicMine(): Boolean {
		DefaultUI.setStatus("Entering Volcanic Mine")
		if (ChatDialog.isOpen() && !settings.unlockedFreeEntry()) {
			val numulites = Inventory.getQuantity("Numulite")
			if (numulites < 30) {
				log.debug("Setting paid for entry to false")
				paidForEntry = false
				volcanicMineState = VolcanicMineState.RESUPPLY
				return false
			} else {
				ChatDialog.getOption(payNumulites)?.let {
					it.select()
					Execution.delayUntil({ volcanicMine.inVolcanicMine() }, { Players.getLocal()?.isMoving }, 2400)
					return true
				}
			}
		}
		val entrance = GameObjects.newQuery().names("Volcano Entrance").results().first()

		entrance?.let {
			it.interact("Climb-down")
			Execution.delayUntil(
				{ volcanicMine.inVolcanicMine() || ChatDialog.isOpen() },
				{ Players.getLocal()?.isMoving },
				2400
			)
			Execution.delay(600)
		} ?: stopAndWarn("Volcanic Mine entrance not found")
		return true
	}

	enum class VolcanicMineState {
		RESUPPLY,
		ENTER,
		VOLCANIC_MINE,
	}

	override fun onSettingChanged(p0: SettingChangedEvent?) {
		log.debug("Setting changed: $p0")

	}

	override fun onSettingsConfirmed() {
		if (isPaused) resume()
		platform.invoke {
			if (Execution.delayUntil({ RuneScape.isLoggedIn() }, 30000)) {
				Execution.delay(600)
				validateSettings()
				started = true
			} else {
				stopAndWarn("Failed to login to RuneScape after 30 seconds ")
			}
		}
	}

	private fun addInfoLabel() {
		val vbox = VBox()
		val label = Label().apply {
			text = """
				Start the script outside of the Volcanic Mine entrance.
				Make sure you have a pickaxe and food in your inventory.
				Prayer potions are also required! have in bank or inventory.
				
				Stamina Potions are optional, but recommended.
				
				There is a low chance you will die, so don't bring anything you're not willing to lose.
				You want to set eat percent high, as you can take constant 20s in a row
				Highly advise bringing a teleport tab for the failsafe, anything that has the action "Break" will work.
			""".trimIndent()
			font = Font.font(15.0)

		}
		vbox.children.add(label)
		DefaultUI.addPanel(0, this, "Information", vbox, true)
	}
}