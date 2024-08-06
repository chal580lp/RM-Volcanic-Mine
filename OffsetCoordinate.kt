package com.runemate.volcanic

import com.runemate.game.api.hybrid.location.Coordinate
import com.runemate.game.api.hybrid.region.Players

class OffsetCoordinate(
	val x: Int,
	val y: Int,
	private val baseCoordinate: () -> Coordinate?
) {
	fun getCoordinate(): Coordinate? {
		val base = baseCoordinate() ?: return null
		val derived = base.derive(x, y)
		val plane = Players.getLocal()?.position?.plane ?: return null
		return Coordinate(derived.x, derived.y, plane)
	}

	fun atCoordinate(): Boolean {
		return Players.getLocal()?.position == getCoordinate()
	}

	fun isAhead(direction: VolcanicMine.Direction): Boolean {
		val position = Players.getLocal()?.position ?: return false
		val coordinate = getCoordinate() ?: return false
		return when (direction) {
			VolcanicMine.Direction.NORTH -> position.y < coordinate.y
			VolcanicMine.Direction.EAST -> position.x < coordinate.x
		}
	}

	fun isBehind(direction: VolcanicMine.Direction): Boolean {
		val position = Players.getLocal()?.position ?: return false
		val coordinate = getCoordinate() ?: return false
		return when (direction) {
			VolcanicMine.Direction.NORTH -> position.y > coordinate.y
			VolcanicMine.Direction.EAST -> position.x > coordinate.x
		}
	}
}