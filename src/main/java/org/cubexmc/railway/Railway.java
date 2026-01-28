package org.cubexmc.railway;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.railway.command.RailCommand;
import org.cubexmc.railway.listener.PlayerInteractListener;
import org.cubexmc.railway.listener.PlayerMoveListener;
import org.cubexmc.railway.listener.VehicleListener;
import org.cubexmc.railway.estimation.TravelTimeEstimator;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.manager.SelectionManager;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.service.LineServiceManager;
import org.cubexmc.railway.placeholder.RailwayPlaceholders;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.railway.update.ConfigUpdater;
import org.cubexmc.railway.control.TrainControlMode;

public final class Railway extends JavaPlugin {

    private LanguageManager languageManager;
    private LineServiceManager lineServiceManager;
    private LineManager lineManager;
    private StopManager stopManager;
    private SelectionManager selectionManager;
    private TravelTimeEstimator travelTimeEstimator;
    private org.cubexmc.railway.gui.GuiManager guiManager;

    @Override
    public void onEnable() {
        ensureDataFolder();
        saveDefaultConfig();
        ConfigUpdater.applyDefaults(this, "config.yml");
        saveDefaultResource("lines.yml");
        saveDefaultResource("stops.yml");

        this.languageManager = new LanguageManager(this);
        this.lineManager = new LineManager(this);
        this.stopManager = new StopManager(this);
        this.selectionManager = new SelectionManager();
        this.lineServiceManager = new LineServiceManager(this);
        this.travelTimeEstimator = new TravelTimeEstimator(this);
        this.travelTimeEstimator.load();

        // Initialize GUI Manager
        this.guiManager = new org.cubexmc.railway.gui.GuiManager(this);

        // Initialize scoreboard manager
        org.cubexmc.railway.train.ScoreboardManager.initialize(this);
        purgeResidualMinecarts();

        // Commands
        RailCommand railCommand = new RailCommand(this);
        if (getCommand("rail") != null) {
            getCommand("rail").setExecutor(railCommand);
            getCommand("rail").setTabCompleter(railCommand);
        }

        // Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        Bukkit.getPluginManager().registerEvents(new VehicleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new org.cubexmc.railway.gui.GuiListener(this), this);

        // PlaceholderAPI hook (optional)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new RailwayPlaceholders(this).register();
                getLogger().info("Registered Railway placeholders with PlaceholderAPI");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        // Register bstats
        int pluginId = 27819; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info("Railway enabled");
    }

    @Override
    public void onDisable() {
        if (lineServiceManager != null) {
            lineServiceManager.shutdown();
        }
        if (travelTimeEstimator != null) {
            travelTimeEstimator.save();
        }
        getLogger().info("Railway disabled");
    }

    public org.cubexmc.railway.gui.GuiManager getGuiManager() {
        return guiManager;
    }

    public LineServiceManager getLineServiceManager() {
        return lineServiceManager;
    }

    public LineManager getLineManager() {
        return lineManager;
    }

    public StopManager getStopManager() {
        return stopManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public TravelTimeEstimator getTravelTimeEstimator() {
        return travelTimeEstimator;
    }

    public double getCartSpeed() {
        return getConfig().getDouble("settings.cart_speed", 0.4);
    }

    public double getTrainSpacing() {
        return getConfig().getDouble("settings.train_spacing", 1.6);
    }

    // Title configuration getters
    public boolean isStopContinuousTitleEnabled() {
        return getConfig().getBoolean("titles.stop_continuous.enabled", true);
    }

    public int getStopContinuousInterval() {
        return getConfig().getInt("titles.stop_continuous.interval", 40);
    }

    public boolean isStopContinuousAlways() {
        return getConfig().getBoolean("titles.stop_continuous.always", true);
    }

    public String getStopContinuousTitle() {
        return getConfig().getString("titles.stop_continuous.title", "&b{stop_name}");
    }

    public String getStopContinuousSubtitle() {
        return getConfig().getString("titles.stop_continuous.subtitle", "");
    }

    public String getStopContinuousActionbar() {
        return getConfig().getString("titles.stop_continuous.actionbar", "");
    }

    public String getStartStopTitle() {
        return getConfig().getString("titles.stop_continuous.start_stop.title", "&b{stop_name}");
    }

    public String getStartStopSubtitle() {
        return getConfig().getString("titles.stop_continuous.start_stop.subtitle", "");
    }

    public String getStartStopActionbar() {
        return getConfig().getString("titles.stop_continuous.start_stop.actionbar", "");
    }

    public String getEndStopTitle() {
        return getConfig().getString("titles.stop_continuous.end_stop.title", "&b{stop_name}");
    }

    public String getEndStopSubtitle() {
        return getConfig().getString("titles.stop_continuous.end_stop.subtitle", "");
    }

    public String getEndStopActionbar() {
        return getConfig().getString("titles.stop_continuous.end_stop.actionbar", "");
    }

    public int getStopContinuousFadeIn() {
        return getConfig().getInt("titles.stop_continuous.fade_in", 10);
    }

    public int getStopContinuousStay() {
        return getConfig().getInt("titles.stop_continuous.stay", 40);
    }

    public int getStopContinuousFadeOut() {
        return getConfig().getInt("titles.stop_continuous.fade_out", 10);
    }

    public boolean isArriveStopTitleEnabled() {
        return getConfig().getBoolean("titles.arrive_stop.enabled", true);
    }

    public String getArriveStopTitle() {
        return getConfig().getString("titles.arrive_stop.title", "&b{stop_name} &fArrived");
    }

    public String getArriveStopSubtitle() {
        return getConfig().getString("titles.arrive_stop.subtitle", "");
    }

    public int getArriveStopFadeIn() {
        return getConfig().getInt("titles.arrive_stop.fade_in", 10);
    }

    public int getArriveStopStay() {
        return getConfig().getInt("titles.arrive_stop.stay", 40);
    }

    public int getArriveStopFadeOut() {
        return getConfig().getInt("titles.arrive_stop.fade_out", 10);
    }

    public boolean isTerminalStopTitleEnabled() {
        return getConfig().getBoolean("titles.terminal_stop.enabled", true);
    }

    public String getTerminalStopTitle() {
        return getConfig().getString("titles.terminal_stop.title", "&b{stop_name}");
    }

    public String getTerminalStopSubtitle() {
        return getConfig().getString("titles.terminal_stop.subtitle", "&cTerminal Station - Please Exit");
    }

    public int getTerminalStopFadeIn() {
        return getConfig().getInt("titles.terminal_stop.fade_in", 10);
    }

    public int getTerminalStopStay() {
        return getConfig().getInt("titles.terminal_stop.stay", 60);
    }

    public int getTerminalStopFadeOut() {
        return getConfig().getInt("titles.terminal_stop.fade_out", 10);
    }

    public boolean isDepartureTitleEnabled() {
        return getConfig().getBoolean("titles.departure.enabled", true);
    }

    public int getDepartureInterval() {
        return getConfig().getInt("titles.departure.interval", 40);
    }

    public String getDepartureTitle() {
        return getConfig().getString("titles.departure.title", "Next Stop &e{next_stop_name}");
    }

    public String getDepartureSubtitle() {
        return getConfig().getString("titles.departure.subtitle", "");
    }

    public String getDepartureActionbar() {
        return getConfig().getString("titles.departure.actionbar", "");
    }

    public int getDepartureFadeIn() {
        return getConfig().getInt("titles.departure.fade_in", 5);
    }

    public int getDepartureStay() {
        return getConfig().getInt("titles.departure.stay", 40);
    }

    public int getDepartureFadeOut() {
        return getConfig().getInt("titles.departure.fade_out", 5);
    }

    public boolean isWaitingTitleEnabled() {
        return getConfig().getBoolean("titles.waiting.enabled", true);
    }

    public int getWaitingInterval() {
        return getConfig().getInt("titles.waiting.interval", 20);
    }

    public String getWaitingTitle() {
        return getConfig().getString("titles.waiting.title", "Train Departing Soon");
    }

    public String getWaitingSubtitle() {
        return getConfig().getString("titles.waiting.subtitle", "");
    }

    public String getWaitingActionbar() {
        return getConfig().getString("titles.waiting.actionbar", "");
    }

    // Sound configuration getters
    public boolean isDepartureSoundEnabled() {
        return getConfig().getBoolean("sounds.departure.enabled", true);
    }

    public List<String> getDepartureNotes() {
        return getConfig().getStringList("sounds.departure.notes");
    }

    public int getDepartureInitialDelay() {
        return getConfig().getInt("sounds.departure.initial_delay", 0);
    }

    public boolean isArrivalSoundEnabled() {
        return getConfig().getBoolean("sounds.arrival.enabled", true);
    }

    public List<String> getArrivalNotes() {
        return getConfig().getStringList("sounds.arrival.notes");
    }

    public int getArrivalInitialDelay() {
        return getConfig().getInt("sounds.arrival.initial_delay", 0);
    }

    public boolean isStationArrivalSoundEnabled() {
        return getConfig().getBoolean("sounds.station_arrival.enabled", true);
    }

    public List<String> getStationArrivalNotes() {
        return getConfig().getStringList("sounds.station_arrival.notes");
    }

    public int getStationArrivalInitialDelay() {
        return getConfig().getInt("sounds.station_arrival.initial_delay", 0);
    }

    public boolean isWaitingSoundEnabled() {
        return getConfig().getBoolean("sounds.waiting.enabled", true);
    }

    public List<String> getWaitingNotes() {
        return getConfig().getStringList("sounds.waiting.notes");
    }

    public int getWaitingInitialDelay() {
        return getConfig().getInt("sounds.waiting.initial_delay", 0);
    }

    public int getWaitingSoundInterval() {
        return getConfig().getInt("sounds.waiting.interval", 20);
    }

    // Scoreboard configuration getters
    public boolean isScoreboardEnabled() {
        return getConfig().getBoolean("scoreboard.enabled", true);
    }

    public String getScoreboardCurrentStopStyle() {
        return getConfig().getString("scoreboard.styles.current_stop", "&f");
    }

    public String getScoreboardNextStopStyle() {
        return getConfig().getString("scoreboard.styles.next_stop", "&a");
    }

    public String getScoreboardOtherStopsStyle() {
        return getConfig().getString("scoreboard.styles.other_stops", "&7");
    }

    public String getScoreboardLineSymbol() {
        return getConfig().getString("scoreboard.line_symbol", "●");
    }

    public Logger logger() {
        return getLogger();
    }

    // Chunk loading (global mode) getters
    public boolean isChunkLoadingEnabled() {
        return getConfig().getBoolean("settings.chunk_loading.enabled", false);
    }

    public int getChunkLoadingRadius() {
        return Math.max(0, getConfig().getInt("settings.chunk_loading.radius", 2));
    }

    public int getForwardPreloadRadius() {
        return Math.max(0, getConfig().getInt("settings.chunk_loading.forward_preload_radius", 1));
    }

    public boolean isChunkLoadingOnlyWhenMoving() {
        return getConfig().getBoolean("settings.chunk_loading.only_when_moving", false);
    }

    public int getChunkLoadingUpdateIntervalTicks() {
        return Math.max(1, getConfig().getInt("settings.chunk_loading.update_interval_ticks", 10));
    }

    // Travel time estimation getters
    public boolean isTravelTimeEnabled() {
        return getConfig().getBoolean("settings.travel_time.enabled", true);
    }

    public double getDefaultSectionSeconds() {
        return getConfig().getDouble("settings.travel_time.default_section_seconds", 20.0);
    }

    public double getPriorStrength() {
        return Math.max(0.0, getConfig().getDouble("settings.travel_time.prior_strength", 4.0));
    }

    public double getOutlierSigma() {
        return Math.max(0.0, getConfig().getDouble("settings.travel_time.outlier_sigma", 3.0));
    }

    public boolean isUseUnboardedSamples() {
        return getConfig().getBoolean("settings.travel_time.use_unboarded_samples", false);
    }

    public double getUnboardedSampleWeight() {
        return Math.max(0.0, getConfig().getDouble("settings.travel_time.unboarded_weight", 0.2));
    }

    public double getDecayPerDay() {
        return Math.max(0.0, Math.min(1.0, getConfig().getDouble("settings.travel_time.decay_per_day", 0.95)));
    }

    // Local mode getters
    public double getLocalActivationRadius() {
        return getConfig().getDouble("settings.local.activation_radius", 96.0);
    }

    public long getLocalSpawnLeadTicks() {
        return Math.max(0L, getConfig().getLong("settings.local.spawn_lead_ticks", 120L));
    }

    public double getLocalSuppressThresholdSeconds() {
        return Math.max(0.0, getConfig().getDouble("settings.local.suppress_threshold_seconds", 10.0));
    }

    public boolean isLocalVirtualizationEnabled() {
        return getConfig().getBoolean("settings.local.virtualization.enabled", true);
    }

    public long getLocalVirtualIdleTicks() {
        return Math.max(0L, getConfig().getLong("settings.local.virtualization.idle_ticks", 200L));
    }

    public int getLocalVirtualLookaheadStops() {
        return Math.max(1, getConfig().getInt("settings.local.virtualization.lookahead_stops", 2));
    }

    // Virtual railway network getters
    public org.cubexmc.railway.service.virtual.SpawnMode getLocalSpawnMode() {
        String mode = getConfig().getString("settings.local.virtual_network.spawn_mode", "current_stop");
        return org.cubexmc.railway.service.virtual.SpawnMode.from(mode,
                org.cubexmc.railway.service.virtual.SpawnMode.CURRENT_STOP);
    }

    public double getLocalMaxMaterializeEtaSeconds() {
        return Math.max(5.0, getConfig().getDouble("settings.local.virtual_network.max_materialize_eta_seconds", 60.0));
    }

    public int getLocalRailSearchRadius() {
        return Math.max(1, getConfig().getInt("settings.local.virtual_network.rail_search_radius", 5));
    }

    // Debug configuration getters

    // Safe speed mode getter
    public boolean isSafeSpeedMode() {
        return getConfig().getBoolean("settings.safe_speed_mode", true);
    }

    // Physics section getters (replacing consist tuning)
    public boolean isPhysicsLeadKinematic() {
        return getConfig().getBoolean("physics.lead_kinematic", true);
    }

    public double getPhysicsSmoothingLerp() {
        double v = getConfig().getDouble("physics.smoothing_lerp", 0.15);
        if (v < 0.0)
            return 0.0;
        if (v > 1.0)
            return 1.0;
        return v;
    }

    public int getPhysicsTrailSeconds() {
        return Math.max(2, getConfig().getInt("physics.trail_seconds", 10));
    }

    public boolean isPhysicsSnapToRail() {
        return getConfig().getBoolean("physics.snap_to_rail", true);
    }

    public boolean isPhysicsStrictWhenPassenger() {
        return getConfig().getBoolean("physics.strict_when_has_passenger", true);
    }

    public int getPhysicsLookaheadBlocks() {
        return Math.max(0, getConfig().getInt("physics.lookahead_blocks", 4));
    }

    public boolean isPhysicsPositionOnlyMode() {
        return getConfig().getBoolean("physics.position_only_mode", false);
    }

    // Control mode getters
    public TrainControlMode getDefaultControlMode() {
        String raw = getConfig().getString("settings.control.default_mode", "kinematic");
        return TrainControlMode.from(raw, TrainControlMode.KINEMATIC);
    }

    public String getLeashMobTypeRaw() {
        return getConfig().getString("settings.control.leash.mob_type", "ALLAY");
    }

    public int getLeashUpdateIntervalTicks() {
        return Math.max(1, getConfig().getInt("settings.control.leash.update_interval_ticks", 1));
    }

    public double getLeashOffsetY() {
        return getConfig().getDouble("settings.control.leash.offset_y", 0.9);
    }

    public Material getSelectionTool() {
        String raw = getConfig().getString("settings.selection.tool", "GOLDEN_HOE");
        if (raw == null || raw.isEmpty()) {
            raw = "GOLDEN_HOE";
        }
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        }
        if (material == null) {
            material = Material.GOLDEN_HOE;
        }
        return material;
    }

    public boolean isPlayerWithinStopRadius(Stop stop, double radiusSquared) {
        if (stop == null || stop.getStopPointLocation() == null) {
            return false;
        }
        Location base = stop.getStopPointLocation();
        if (base.getWorld() == null) {
            return false;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(base.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(base) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void ensureDataFolder() {
        File folder = getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private void saveDefaultResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    @SuppressWarnings("deprecation")
    private void purgeResidualMinecarts() {
        if (lineServiceManager == null) {
            return;
        }
        NamespacedKey trainKey = lineServiceManager.getTrainKey();
        for (World world : Bukkit.getWorlds()) {
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                PersistentDataContainer container = cart.getPersistentDataContainer();
                if (container.has(trainKey, PersistentDataType.STRING) || "RailwayTrain".equals(cart.getCustomName())) {
                    cart.remove();
                }
            }
        }
    }
}
