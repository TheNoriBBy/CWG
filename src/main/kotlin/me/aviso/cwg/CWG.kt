package me.aviso.cwg

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.java.JavaPlugin
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class CWG : JavaPlugin() {
    val regions = mutableMapOf<String, MutableSet<Material>>()
    lateinit var configFile: File
    lateinit var config: YamlConfiguration

    override fun onEnable() {
        configFile = File(dataFolder, "regions.yml")
        if (!configFile.exists()) {
            configFile.createNewFile()
        }
        config = YamlConfiguration.loadConfiguration(configFile)
        loadRegions()

        server.pluginManager.registerEvents(BlockBreakListener(this), this)

        getCommand("cwg")?.setExecutor(CwgCommand(this))
    }

    override fun onDisable() {
        saveRegions()
    }

    private fun loadRegions() {
        regions.clear()
        config.getConfigurationSection("regions")?.getKeys(false)?.forEach { regionName ->
            val blocks = config.getStringList("regions.$regionName.allowed_blocks")
            val materials = blocks.mapNotNull { Material.matchMaterial(it) }.toMutableSet()
            regions[regionName] = materials
        }
    }

    fun saveRegions() {
        config.set("regions", null)
        regions.forEach { (regionName, materials) ->
            config.set("regions.$regionName.allowed_blocks", materials.map { it.name })
        }
        config.save(configFile)
    }

    class BlockBreakListener(private val plugin: CWG) : Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        fun onBlockBreak(event: BlockBreakEvent) {
            if (!event.isCancelled) return

            val bukkitLocation = event.block.location
            val bukkitWorld = bukkitLocation.world ?: return

            val weWorld = BukkitAdapter.adapt(bukkitWorld)
            val regionManager: RegionManager = WorldGuard.getInstance().platform.regionContainer.get(weWorld) ?: return

            val blockVector: BlockVector3 = BlockVector3.at(
                bukkitLocation.blockX,
                bukkitLocation.blockY,
                bukkitLocation.blockZ
            )

            val applicableRegions = regionManager.getApplicableRegions(blockVector).regions

            for (region in applicableRegions) {
                val allowedBlocks = plugin.regions[region.id] ?: continue
                if (allowedBlocks.contains(event.block.type)) {
                    event.isCancelled = false
                    return
                }
            }
        }
    }

    class CwgCommand(private val plugin: CWG) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
            if (sender !is Player) {
                sender.sendMessage("Only players can use this command.")
                return true
            }

            if (!sender.hasPermission("cwg.admin")) {
                sender.sendMessage("§cNo permission.")
                return true
            }

            if (args.isEmpty()) return false

            when (args[0].lowercase()) {
                "addblock" -> {
                    if (args.size < 3) {
                        sender.sendMessage("§cUzycie: /cwg addblock <region> <block>")
                        return true
                    }
                    val regionName = args[1]
                    val blockName = args[2].uppercase()
                    val material = Material.matchMaterial(blockName)

                    if (material == null) {
                        sender.sendMessage("§c$blockName nie jest prawidlowym blokiem.")
                        return true
                    }

                    val regionSet = plugin.regions.getOrPut(regionName) { mutableSetOf() }
                    regionSet.add(material)
                    plugin.saveRegions()
                    sender.sendMessage("§aDodano $blockName do regionu $regionName.")
                    return true
                }
                "removeblock" -> {
                    if (args.size < 3) {
                        sender.sendMessage("§cUzycie: /cwg removeblock <region> <block>")
                        return true
                    }
                    val regionName = args[1]
                    val blockName = args[2].uppercase()
                    val material = Material.matchMaterial(blockName)

                    if (material == null) {
                        sender.sendMessage("§c$blockName nie jest prawidlowym blokiem.")
                        return true
                    }

                    val regionSet = plugin.regions[regionName] ?: run {
                        sender.sendMessage("§cRegion $regionName nie zostal znaleziony.")
                        return true
                    }

                    if (regionSet.remove(material)) {
                        plugin.saveRegions()
                        sender.sendMessage("§aUsunieto $blockName z regionu $regionName.")
                    } else {
                        sender.sendMessage("§cBlok $blockName nie znaleziony w regionie $regionName.")
                    }
                    return true
                }
                "listblocks" -> {
                    if (args.size < 2) {
                        sender.sendMessage("§cUzycie: /cwg listblocks <region>")
                        return true
                    }
                    val regionName = args[1]
                    val blocks = plugin.regions[regionName] ?: run {
                        sender.sendMessage("§cRegion $regionName nie znaleziony.")
                        return true
                    }

                    sender.sendMessage("§aBloki dozwolone w: $regionName:")
                    blocks.forEach { sender.sendMessage("§7- ${it.name}") }
                    return true
                }
                else -> return false
            }
        }
    }
}