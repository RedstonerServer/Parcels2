package io.dico.parcels2

import io.dico.parcels2.math.Vec2i
import io.dico.parcels2.math.floor
import io.dico.parcels2.storage.Storage
import io.dico.parcels2.util.doAwait
import kotlinx.coroutines.experimental.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.*
import kotlin.coroutines.experimental.buildSequence

class Worlds(private val plugin: ParcelsPlugin) {
    val worlds: Map<String, ParcelWorld> get() = _worlds
    private val _worlds: MutableMap<String, ParcelWorld> = HashMap()

    fun getWorld(name: String): ParcelWorld? = _worlds[name]

    fun getWorld(world: World): ParcelWorld? = getWorld(world.name)

    fun getParcelAt(block: Block): Parcel? = getParcelAt(block.world, block.x, block.z)

    fun getParcelAt(player: Player): Parcel? = getParcelAt(player.location)

    fun getParcelAt(location: Location): Parcel? = getParcelAt(location.world, location.x.floor(), location.z.floor())

    fun getParcelAt(world: World, x: Int, z: Int): Parcel? = getParcelAt(world.name, x, z)

    fun getParcelAt(world: String, x: Int, z: Int): Parcel? {
        with(getWorld(world) ?: return null) {
            return generator.parcelAt(x, z)
        }
    }

    fun loadWorlds(options: Options) {
        for ((worldName, worldOptions) in options.worlds.entries) {
            val world: ParcelWorld
            try {
                val containerFactory: ParcelContainerFactory = { parcelWorld ->
                    DefaultParcelContainer(parcelWorld, plugin.storage)
                }

                world = ParcelWorld(
                        worldName,
                        worldOptions,
                        worldOptions.generator.getGenerator(this, worldName),
                        containerFactory)

            } catch (ex: Exception) {
                ex.printStackTrace()
                continue
            }

            _worlds.put(worldName, world)

            if (Bukkit.getWorld(worldName) == null) {
                plugin.doAwait {
                    cond = {
                        try {
                            // server.getDefaultGameMode() throws an error before any worlds are initialized.
                            // createWorld() below calls that method.
                            // Plugin needs to load on STARTUP for generators to be registered correctly.
                            // Means we need to await the initial worlds getting loaded.

                            plugin.server.defaultGameMode; true
                        } catch (ex: Throwable) {
                            false
                        }
                    }

                    onSuccess = {
                        val bworld = WorldCreator(worldName).generator(world.generator).createWorld()
                        val spawn = world.generator.getFixedSpawnLocation(bworld, null)
                        bworld.setSpawnLocation(spawn.x.floor(), spawn.y.floor(), spawn.z.floor())
                    }
                }
            }

        }

    }
}

interface ParcelProvider {

    fun parcelAt(x: Int, z: Int): Parcel?

    fun parcelAt(vec: Vec2i): Parcel? = parcelAt(vec.x, vec.z)

    fun parcelAt(loc: Location): Parcel? = parcelAt(loc.x.floor(), loc.z.floor())

    fun parcelAt(entity: Entity): Parcel? = parcelAt(entity.location)

    fun parcelAt(block: Block): Parcel? = parcelAt(block.x, block.z)
}

class ParcelWorld constructor(val name: String,
                              val options: WorldOptions,
                              val generator: ParcelGenerator,
                              containerFactory: ParcelContainerFactory) : ParcelProvider by generator {
    val world: World by lazy { Bukkit.getWorld(name) ?: throw NullPointerException("World $name does not appear to be loaded") }
    val container: ParcelContainer = containerFactory(this)

    fun parcelByID(x: Int, z: Int): Parcel? {
        TODO("not implemented")
    }

    fun parcelByID(id: Vec2i): Parcel? = parcelByID(id.x, id.z)

    fun enforceOptionsIfApplicable() {
        val world = world
        val options = options
        if (options.dayTime) {
            world.setGameRuleValue("doDaylightCycle", "false")
            world.setTime(6000)
        }

        if (options.noWeather) {
            world.setStorm(false)
            world.setThundering(false)
            world.weatherDuration = Integer.MAX_VALUE
        }

        world.setGameRuleValue("doTileDrops", "${options.doTileDrops}")
    }

}

abstract class ParcelContainer {

    abstract fun ployByID(x: Int, z: Int): Parcel?

    abstract fun nextEmptyParcel(): Parcel?

}

typealias ParcelContainerFactory = (ParcelWorld) -> ParcelContainer

class DefaultParcelContainer(private val world: ParcelWorld,
                             private val storage: Storage) : ParcelContainer() {
    private var parcels: Array<Array<Parcel>>

    init {
        parcels = initArray(world.options.axisLimit, world)
    }

    fun resizeIfSizeChanged() {
        if (parcels.size / 2 != world.options.axisLimit) {
            resize(world.options.axisLimit)
        }
    }

    fun resize(axisLimit: Int) {
        parcels = initArray(axisLimit, world, this)
    }

    fun initArray(axisLimit: Int, world: ParcelWorld, cur: DefaultParcelContainer? = null): Array<Array<Parcel>> {
        val arraySize = 2 * axisLimit + 1
        return Array(arraySize) {
            val x = it - axisLimit
            Array(arraySize) {
                val z = it - axisLimit
                cur?.ployByID(x, z) ?: Parcel(world, Vec2i(x, z))
            }
        }
    }

    override fun ployByID(x: Int, z: Int): Parcel? {
        return parcels[x][z]
    }

    override fun nextEmptyParcel(): Parcel? {
        TODO()
    }

    fun allParcels(): Sequence<Parcel> = buildSequence {
        for (array in parcels) {
            yieldAll(array.iterator())
        }
    }

    fun loadAllData() {
        val channel = storage.readParcelData(allParcels(), 100)
        launch(storage.asyncDispatcher) {
            for ((parcel, data) in channel) {
                data?.let { parcel.data = it }
            }
        }
    }

}