package io.dico.parcels2

import io.dico.parcels2.util.findWoodKindPrefixedMaterials
import org.bukkit.Material
import java.util.EnumMap

class Interactables
private constructor(val id: Int,
                    val name: String,
                    val interactableByDefault: Boolean,
                    vararg val materials: Material) {

    companion object {
        val classesById: List<Interactables>
        val classesByName: Map<String, Interactables>
        val listedMaterials: Map<Material, Int>

        init {
            val array = getClassesArray()
            classesById = array.asList()
            classesByName = mapOf(*array.map { it.name to it }.toTypedArray())
            listedMaterials = EnumMap(mapOf(*array.flatMap { clazz -> clazz.materials.map { it to clazz.id } }.toTypedArray()))
        }

        private fun getClassesArray() = run {
            var id = 0
            @Suppress("UNUSED_CHANGED_VALUE")
            arrayOf(
                Interactables(id++, "button", true,
                    Material.STONE_BUTTON,
                    *findWoodKindPrefixedMaterials("BUTTON")),

                Interactables(id++, "lever", true,
                    Material.LEVER),

                Interactables(id++, "pressure_plate", true,
                    Material.STONE_PRESSURE_PLATE,
                    *findWoodKindPrefixedMaterials("PRESSURE_PLATE"),
                    Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                    Material.LIGHT_WEIGHTED_PRESSURE_PLATE),

                Interactables(id++, "redstone_components", false,
                    Material.COMPARATOR,
                    Material.REPEATER),

                Interactables(id++, "containers", false,
                    Material.CHEST,
                    Material.TRAPPED_CHEST,
                    Material.DISPENSER,
                    Material.DROPPER,
                    Material.HOPPER,
                    Material.FURNACE)
            )
        }

    }

}

interface InteractableConfiguration {
    val interactableClasses: List<Interactables> get() = Interactables.classesById.filter { isInteractable(it) }
    fun isInteractable(material: Material): Boolean
    fun isInteractable(clazz: Interactables): Boolean
    fun setInteractable(clazz: Interactables, interactable: Boolean): Boolean
    fun clear(): Boolean
    fun copyFrom(other: InteractableConfiguration) {
        Interactables.classesById.forEach { setInteractable(it, other.isInteractable(it)) }
    }
}

class BitmaskInteractableConfiguration : InteractableConfiguration {
    val bitmaskArray = IntArray((Interactables.classesById.size + 31) / 32)

    private fun isBitSet(classId: Int): Boolean {
        val idx = classId.ushr(5)
        return idx < bitmaskArray.size && bitmaskArray[idx].and(0x1.shl(classId.and(0x1F))) != 0
    }

    override fun isInteractable(material: Material): Boolean {
        val classId = Interactables.listedMaterials[material] ?: return false
        return isBitSet(classId) != Interactables.classesById[classId].interactableByDefault
    }

    override fun isInteractable(clazz: Interactables): Boolean {
        return isBitSet(clazz.id) != clazz.interactableByDefault
    }

    override fun setInteractable(clazz: Interactables, interactable: Boolean): Boolean {
        val idx = clazz.id.ushr(5)
        if (idx >= bitmaskArray.size) return false
        val bit = 0x1.shl(clazz.id.and(0x1F))
        val oldBitmask = bitmaskArray[idx]
        bitmaskArray[idx] = if (interactable != clazz.interactableByDefault) oldBitmask.or(bit) else oldBitmask.and(bit.inv())
        return bitmaskArray[idx] != oldBitmask
    }

    override fun clear(): Boolean {
        var change = false
        for (i in bitmaskArray.indices) {
            change = change || bitmaskArray[i] != 0
            bitmaskArray[i] = 0
        }
        return change
    }

}