package com.runemate.volcanic

import com.runemate.game.api.hybrid.entities.GameObject
import com.runemate.game.api.hybrid.input.direct.DirectInput
import com.runemate.game.api.hybrid.input.direct.MenuAction
import com.runemate.game.api.hybrid.input.direct.MenuOpcode
import com.runemate.game.api.hybrid.local.Varbit
import com.runemate.game.api.hybrid.local.Varbits
import com.runemate.game.api.hybrid.local.hud.interfaces.Equipment
import com.runemate.game.api.hybrid.local.hud.interfaces.Health
import com.runemate.game.api.hybrid.local.hud.interfaces.Inventory
import com.runemate.game.api.hybrid.location.Coordinate
import com.runemate.game.api.hybrid.location.navigation.Traversal
import com.runemate.game.api.hybrid.region.GameObjects
import com.runemate.game.api.hybrid.region.Players
import com.runemate.game.api.hybrid.region.Region
import com.runemate.game.api.osrs.local.hud.interfaces.AutoRetaliate
import com.runemate.game.api.osrs.local.hud.interfaces.ControlPanelTab
import com.runemate.game.api.osrs.local.hud.interfaces.Prayer
import com.runemate.game.api.script.Execution
import com.runemate.game.api.script.framework.listeners.EngineListener
import com.runemate.game.api.script.framework.listeners.NpcListener
import com.runemate.game.api.script.framework.listeners.ProjectileListener
import com.runemate.game.api.script.framework.listeners.events.AnimationEvent
import com.runemate.game.api.script.framework.listeners.events.EngineEvent
import com.runemate.ui.DefaultUI

class VolcanicMine(private val bot: Bot) : NpcListener, ProjectileListener, EngineListener {
	private val log = getLogger("VolcanicMine")

	init {
		bot.eventDispatcher.addListener(this)
	}

	/* !!Bad practice, this will be changed soon to using region offsets
	 * We use this coordinate and derive the offset for every single offset coordinate we use in the script
	 * In order to ensure we reference the same coordinates, because they change in each instance of Volcanic Mine
	 */
	var exitCoordinate: Coordinate? = null

	val gameState: Varbit? get() = Varbits.load(5941)
	val timeVarbit: Varbit? get() = Varbits.load(5944)
	val pointsVarbit: Varbit? get() = Varbits.load(5934)
	val stabilityVarbit: Varbit? get() = Varbits.load(5938)

	private val ventBStatus: Varbit? get() = Varbits.load(5940)
	private val ventCStatus: Varbit? get() = Varbits.load(5942)


	// Volcanic Mine Objects that we will be interacting with multiple times
	// get() = is a way to always get the latest object when we call the variable.
	private val volcanicChamber: GameObject?
		get() = GameObjects.newQuery().names("Volcanic chamber").surroundingsReachable().results().first()
	private val blockedVolcanicChamber: GameObject?
		get() = GameObjects.newQuery().names("Blocked volcanic chamber").surroundingsReachable().results().first()
	private val largeRock: GameObject?
		get() = GameObjects.newQuery().names("Large Rock").surroundingsReachable().results().first()
	private val northVolcanicVent: GameObject?
		get() = GameObjects.newQuery()
			.names("Volcanic vent")
			.surroundingsReachable()
			.on(northVolcanicVentCoord.getCoordinate())
			.results()
			.first()
	private val eastVolcanicVent: GameObject?
		get() = GameObjects.newQuery()
			.names("Volcanic vent")
			.surroundingsReachable()
			.on(eastVolcanicVentCoord.getCoordinate())
			.results()
			.first()


	private val eastLobbyStartCoord = OffsetCoordinate(6, -33) { exitCoordinate }
	private val firstWater = OffsetCoordinate(10, -14) { exitCoordinate }
	private val northWater = OffsetCoordinate(10, 0) { exitCoordinate }
	private val eastWater = OffsetCoordinate(15, 0) { exitCoordinate }

	private val northVolcanicVentCoord = OffsetCoordinate(9, 8) { exitCoordinate }
	private val eastVolcanicVentCoord = OffsetCoordinate(21, 0) { exitCoordinate }

	// Helper functions
	private fun getPlayerPlane(): Int? = Players.getLocal()?.position?.plane
	private fun getMineProgress(): Int? = gameState?.value
	private fun getPoints(): Int = pointsVarbit?.value ?: 0
	private fun getStability(): Int = stabilityVarbit?.value ?: 100

	//fun inVolcanicMine(): Boolean = gameState != null && gameState?.value != 0
	fun inVolcanicMine(): Boolean = Region.getCurrentRegionId() in setOf(15262, 15263)


	private val northVentChecked: Boolean
		get() = ventBStatus?.value?.let { it in 1..99 } ?: false
	private val eastVentChecked: Boolean
		get() = ventCStatus?.value?.let { it in 1..99 } ?: false


	fun execute() {
		when {
			// Priority 1: Fail safe if stability is low
			getStability() < 20 -> failSafe()
			// Priority 2: Leave volcanic mine if we have more than 400 points
			getPoints() > 400 -> leaveVolcanicMine()
			// Priority 3: Block chamber if both vents are checked
			northVentChecked && eastVentChecked -> blockChamber()
			// Priority 4: Check vents if we are in the mine
			getPlayerPlane() == 1 -> checkVents()
			// Priority 5: Wait in lobby if game has not started
			getPlayerPlane() == 3 -> waitInLobby()

		}

	}

	private fun waitInLobby() {
		if (exitCoordinate == null) {
			exitCoordinate = GameObjects.newQuery().names("Exit").results().first()?.position ?: run {
				log.error("Exit coordinate not found")
				return
			}
		}
		if (Traversal.isRunEnabled()) {
			Traversal.toggleRun()
		}
		if (AutoRetaliate.isActivated()) {
			AutoRetaliate.deactivate()
			ControlPanelTab.INVENTORY.open()
		}
		// Check if the game has started & climb down the stairs, also important to check if we are in the east lobby area
		if (getMineProgress() == 2 && Players.getLocal()?.position == eastLobbyStartCoord.getCoordinate()) {
			log.info("Game has started, climbing down the stairs")
			// Use a let clause ensuring we only execute the code if the object is not null
			val staircase = GameObjects.newQuery().names("Staircase").results().nearest()
			staircase?.let {
				// If we are in the east lobby area, we need to climb down the staircase
				DirectInput.send(MenuAction.forGameObject(it, "Climb-down"))
				// Delay until our plane is 2, this signals we have entered the lower part of volcanic mine.
				Execution.delayUntil({ Players.getLocal()?.position?.plane == 2 }, 2400)
			}
		}
		// If we are not in the east lobby area, we need to move there
		else if (Players.getLocal()?.position != eastLobbyStartCoord.getCoordinate()) {
			DefaultUI.setStatus("Moving to east lobby area")
			// Using DirectInput to move to the east lobby area
			val coord = eastLobbyStartCoord.getCoordinate()
			log.debug("Walking to $coord")
			DirectInput.sendMovement(eastLobbyStartCoord.getCoordinate())
			// Delay until we are in the east lobby area, reset delay if we are moving.
			Execution.delayUntil(
				{ Players.getLocal()?.position == eastLobbyStartCoord.getCoordinate() },
				{ Players.getLocal()?.isMoving },
				1200
			)
		}
		// Otherwise, we are in the east lobby area and the game has not started yet.
		else {
			DefaultUI.setStatus("Waiting for game to start")
			if (bot.settings.equipVessel()) {
				equipHeatProofVessel()
			}
			Execution.delay(1200)
		}
	}

	private fun equipHeatProofVessel() {
		if (!Equipment.contains("Heat-proof vessel")) {
			log.info("Equipping heat-proof vessel")
			val heatProofVessel = Inventory.newQuery().names("Heat-proof vessel").results().first() ?: return
			DirectInput.send(MenuAction.forSpriteItem(heatProofVessel, "Wear"))
		}
	}

	private fun checkVents() {
		DefaultUI.setStatus("Checking North and East vents")

		when {
			!Inventory.contains("Large rock") -> takeLargeRock()
			firstWater.isAhead(Direction.NORTH) -> {
				log.info("Walking to first water")
				firstWater.getCoordinate()?.let { walkTo(it) }
			}

			firstWater.atCoordinate() -> {
				if (faceDirection(Direction.NORTH)) {
					throwWater(Direction.NORTH)
				}
			}

			!northVentChecked && northVolcanicVent != null -> {
				log.info("Inspecting north vent")
				northVolcanicVent?.let {
					DirectInput.send(MenuAction.forGameObject(it, "Inspect"))
					Execution.delayUntil({ northVentChecked }, { Players.getLocal()?.isMoving }, 1800)
				}
			}

			!northVentChecked && northWater.isAhead(Direction.NORTH) -> {
				log.debug("Walking to north water")
				northWater.getCoordinate()?.let { walkTo(it) }

			}

			!northVentChecked && (northWater.atCoordinate() || northWater.isBehind(
				Direction.NORTH
			)) -> {
				if (faceDirection(Direction.NORTH)) {
					throwWater(Direction.NORTH)
				}
			}

			!eastVentChecked && eastVolcanicVent != null -> {
				log.info("Inspecting east vent")
				eastVolcanicVent?.let {
					DirectInput.send(MenuAction.forGameObject(it, "Inspect"))
					Execution.delayUntil({ eastVentChecked }, { Players.getLocal()?.isMoving }, 1800)
				}
			}

			!eastVentChecked && eastWater.isAhead(Direction.EAST) -> {
				log.debug("Walking to east water")
				eastWater.getCoordinate()?.let { walkTo(it) }
			}

			!eastVentChecked && (eastWater.atCoordinate() || eastWater.isBehind(
				Direction.EAST
			)) -> {
				if (faceDirection(Direction.EAST)) {
					throwWater(Direction.EAST)
				}
			}
		}
	}

	private fun blockChamber() {
		DefaultUI.setStatus("Blocking & Unblocking till we reach 408 points")
		when {
			largeRock != null && Inventory.getQuantity("Large rock") <= 1 -> takeLargeRock()
			blockedVolcanicChamber != null -> {
				log.info("Mining blocked volcanic chamber")
				blockedVolcanicChamber?.let {
					DirectInput.send(MenuAction.forGameObject(it, "Mine"))
					Execution.delayUntil({ !it.isValid }, { Players.getLocal()?.animationId != -1 }, 1200)
				}
			}

			Inventory.contains("Large rock") && volcanicChamber != null -> {
				log.info("Blocking volcanic chamber")
				volcanicChamber?.let {
					DirectInput.send(MenuAction.forGameObject(it, "Block"))
					Execution.delayUntil({ !it.isValid }, { Players.getLocal()?.animationId != -1 }, 1200)
				}
			}
		}
	}

	private fun leaveVolcanicMine() {
		DefaultUI.setStatus("Finished, exiting Volcanic Mine")
		if (Players.getLocal()?.position?.plane == 1) {
			log.info("Using stairs to exit")
			val exit = GameObjects.newQuery().names("Staircase").surroundingsReachable().results().first() ?: return
			DirectInput.send(MenuAction.forGameObject(exit, "Climb-up"))
			Execution.delayUntil(
				{ Players.getLocal()?.position?.plane == 3 },
				{ Players.getLocal()?.isIdle == false },
				1800
			)
		}
		if (Players.getLocal()?.position?.plane == 3) {
			log.info("Leaving volcanic chamber")
			val exit = GameObjects.newQuery().names("Exit").results().first() ?: run {
				log.warn("Exit is null, we are stuck!")
				val coord = exitCoordinate?.derive(0, -8, 0)
				coord?.let {
					DirectInput.sendMovement(coord)
				} ?: {
					log.error("Unable to walk closer to exit")
				}
				return
			}
			DirectInput.send(MenuAction.forGameObject(exit, "Climb-up"))
			Execution.delayUntil(
				{ Players.getLocal()?.position?.plane == 0 },
				{ Players.getLocal()?.isIdle == false },
				1800
			)
		}

	}

	private fun throwWater(direction: Direction) {
		if (checkIfWalkable(direction)) return
		val startingCoord = Players.getLocal()?.position ?: return
		if (Equipment.contains("Heat-proof vessel")) {
			log.info("Throwing water from equipment")
			val heatProofVessel = Equipment.newQuery().names("Heat-proof vessel").results().first() ?: run {
				log.warn("Unable to find Heat-proof vessel in equipment!")
				return
			}
			DirectInput.send(MenuAction.forSpriteItem(heatProofVessel, "Throw-water $direction"))
		} else {
			log.info("Throwing water from inventory")
			val heatProofVessel = Inventory.newQuery().names("Heat-proof vessel").results().first() ?: return
			DirectInput.send(MenuAction.forSpriteItem(heatProofVessel, "Throw-water"))
		}
		Execution.delayUntil({ Players.getLocal()?.position != startingCoord }, 600)
	}


	/*
	 * Logic is a bit messy here, needs another look at.
	 */
	private fun checkIfWalkable(direction: Direction): Boolean {
		var destination: Coordinate? = null
		var walkable: Coordinate? = null
		if (direction == Direction.NORTH) {
			destination = Players.getLocal()?.position?.derive(0, 2) ?: return false
			walkable = Players.getLocal()?.position?.derive(0, 1) ?: return false
		} else if (direction == Direction.EAST) {
			destination = Players.getLocal()?.position?.derive(2, 0) ?: return false
			walkable = Players.getLocal()?.position?.derive(1, 0) ?: return false
		}
		destination?.let { destination ->
			if (destination.isReachable) {
				walkable?.let { walkable ->
					log.info("Tile ahead is walkable")
					walkTo(walkable)
					Execution.delayUntil({ Players.getLocal()?.position == walkable }, 1200)
					return true
				}
			}
		}
		return false
	}


	/*
	 * Logic to face the direction we want to throw water
	 * If wearing heat-proof vessel, we don't need to face the direction
	 */
	private fun faceDirection(direction: Direction): Boolean {
		if (Players.getLocal()?.highPrecisionOrientation == direction.orientation) return true
		if (Equipment.contains("Heat-proof vessel")) return true
		val position = Players.getLocal()?.position ?: return false
		val back = if (direction == Direction.NORTH) position.derive(0, -1) else position.derive(-1, 0)
		DirectInput.sendMovement(back)
		Execution.delayUntil({ Players.getLocal()?.position == back }, 1200)
		DirectInput.sendMovement(position)
		return Execution.delayUntil({ Players.getLocal()?.highPrecisionOrientation == direction.orientation }, 1200)
	}

	private fun takeLargeRock() {
		val food = Inventory.newQuery().actions("Eat").results().firstOrNull()
		if (Inventory.isFull() && food != null) {
			food.interact("Eat")
			Execution.delayUntil({ !food.isValid }, 1200)
		}
		log.info("Taking large rock")
		largeRock?.let {
			DirectInput.send(MenuAction.forGameObject(it, "Take"))
			Execution.delayUntil({ !it.isValid }, { Players.getLocal()?.isMoving == true }, 1200)
		}
	}

	private fun walkTo(coord: Coordinate) {
		DirectInput.sendMovement(coord)
		Execution.delay(600)
		Execution.delayUntil({ Players.getLocal()?.isMoving == false }, { Players.getLocal()?.isMoving }, 600, 1200)
	}


	private fun lazyPrayerFlick() {
		val menuAction = MenuAction.forInterfaceComponent(Prayer.PROTECT_FROM_MISSILES.component, 0, MenuOpcode.CC_OP)
		DirectInput.send(menuAction)
		Execution.delay(1800)
		DirectInput.send(menuAction)
	}

	private fun failSafe() {
		log.debug("Failsafe activated")
		val teleportTab = Inventory.newQuery().actions("Break").results().first()
		if (teleportTab != null) {
			DefaultUI.setStatus("Failsafe activated: Teleporting out of mine")
			DirectInput.send(MenuAction.forSpriteItem(teleportTab, "Break"))
			Execution.delayUntil({ !inVolcanicMine() }, 2400)
			if (!inVolcanicMine()) {
				bot.stopAndWarn("Stopping bot as had to use teleport tab because of low stability (This is a fail safe)")
			}
		} else {
			DefaultUI.setStatus("Failsafe activated: Exiting mine")
			leaveVolcanicMine()
		}
	}

	override fun onNpcAnimationChanged(event: AnimationEvent?) {
		if (event?.animationId == 7678) {
			log.debug("Volcanic monster attacked us, activating prayer in 2 ticks")
			Execution.delay(1200)
			lazyPrayerFlick()
		}
	}

	override fun onEngineEvent(event: EngineEvent?) {
		if (event?.type == EngineEvent.Type.SERVER_TICK) {
			val eatPercent = bot.settings.eatPercent()
			if (Health.getCurrentPercent() < eatPercent) {
				log.debug("Eating food at ${Health.getCurrent()} HP")
				val food = Inventory.newQuery().actions("Eat").results().first() ?: return
				DirectInput.send(MenuAction.forSpriteItem(food, "Eat"))
			} else if (Traversal.getRunEnergy() <= 10) {
				Traversal.drinkStaminaEnhancer()
			} else if (Prayer.getPoints() <= 10) {
				Inventory.newQuery().filter { it.definition?.name?.contains("Prayer") == true }.actions("Drink")
					.results().first()
					?.let {
						DirectInput.send(MenuAction.forSpriteItem(it, "Drink"))
					}
			}
			if (!Traversal.isRunEnabled() && getPlayerPlane() != 3 && Traversal.getRunEnergy() > 10) {
				Traversal.toggleRun()
			}
		}
	}

	fun reset() {
		exitCoordinate = null
	}

	enum class Direction(val action: String, val orientation: Int) {
		NORTH("north", 1024),
		EAST("east", 1536);

		override fun toString(): String {
			return action
		}
	}


	/*
	 00:18:24[23]  MenuInt | ID: 31029 | Action: Climb-down | Target: Staircase | TargetType: GAME_OBJECT | TargetEntity: Staircase [7845, 303, 3] | Coordinate(7845, 303, 3) | [Climb-down] | Reachable: false | Surrounding: true
	 00:18:27[95]  MenuInt | ID: 31045 | Action: Take | Target: Large Rock | TargetType: GAME_OBJECT | TargetEntity: Large Rock [7847, 314, 1] | Coordinate(7847, 314, 1) | [Take] | Reachable: true | Surrounding: true
	 00:18:49[3]  MenuInt | ID: 2 | Action: Throw-water | Target: Heat-proof vessel | TargetType: INTERFACE | TargetEntity: InterfaceComponent[149, 0, 20] | Opcode: 57    | MouseXY: (2096,1267) | Input: Regular | Params: (20,9764864,2)
	 00:19:08[25]  MenuInt | ID: 31040 | Action: Inspect | Target: Volcanic vent | TargetType: GAME_OBJECT | TargetEntity: Volcanic vent [7848, 346, 1] | Coordinate(7848, 346, 1) | [Inspect] | Reachable: true | Surrounding: true
	 00:19:20[65]  MenuInt | ID: 31040 | Action: Inspect | Target: Volcanic vent | TargetType: GAME_OBJECT | TargetEntity: Volcanic vent [7860, 338, 1] | Coordinate(7860, 338, 1) | [Inspect] | Reachable: true | Surrounding: true
	 00:19:44[85]  MenuInt | ID: 31045 | Action: Take | Target: Large Rock | TargetType: GAME_OBJECT | TargetEntity: Large Rock [7847, 314, 1] | Coordinate(7847, 314, 1) | [Take] | Reachable: true | Surrounding: true
	 00:19:46[45]  MenuInt | ID: 31043 | Action: Block | Target: Volcanic chamber | TargetType: GAME_OBJECT | TargetEntity: Volcanic chamber [7845, 316, 1] | Coordinate(7845, 316, 1) | [Block] | Reachable: true | Surrounding: true
	 00:19:48[13]  MenuInt | ID: 31044 | Action: Mine | Target: Blocked volcanic chamber | TargetType: GAME_OBJECT | TargetEntity: Blocked volcanic chamber [7845, 316, 1] | Coordinate(7845, 316, 1) | [Mine] | Reachable: true | Surrounding: true
	 */
}