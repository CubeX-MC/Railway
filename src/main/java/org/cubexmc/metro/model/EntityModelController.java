package org.cubexmc.metro.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;

/**
 * Manages visual entity models that replace minecart appearance.
 * <p>
 * When enabled, each minecart gets an invisible-but-visible-model living entity
 * that is synchronized to the minecart's position every tick. The minecart itself
 * is hidden from players. Players interact with (ride) the visual entity instead
 * of the minecart, enabling multi-passenger support.
 */
public class EntityModelController {

    private final Metro plugin;
    private final Map<UUID, LivingEntity> cartToModel = new ConcurrentHashMap<>();
    private final Map<UUID, Minecart> modelToCart = new ConcurrentHashMap<>();

    private double offsetY;
    private boolean multiPassenger;

    public EntityModelController(Metro plugin) {
        this.plugin = plugin;
    }

    /**
     * Reload settings from config. Should be called after config load.
     */
    public void reload() {
        this.offsetY = plugin.getConfig().getDouble("entity-model.offset-y", 0.0);
        this.multiPassenger = plugin.getConfig().getBoolean("entity-model.multi-passenger", false);
    }

    /**
     * Attach a visual entity model to a minecart using the global entity type.
     * The model entity is spawned at the minecart's location.
     */
    public void attachModel(Minecart cart) {
        attachModel(cart, null);
    }

    /**
     * Attach a visual entity model to a minecart with an optional per-line entity type override.
     * The model entity is spawned at the minecart's location.
     *
     * @param cart              the minecart to attach a model to
     * @param entityTypeOverride if non-null, use this entity type instead of the global config
     */
    public void attachModel(Minecart cart, String entityTypeOverride) {
        if (cart == null || cart.isDead()) return;

        String typeRaw = (entityTypeOverride != null && !entityTypeOverride.isEmpty())
                ? entityTypeOverride : plugin.getEntityTypeOverride();
        EntityType entityType = parseEntityType(typeRaw);

        if (entityType == null) {
            plugin.getLogger().warning("EntityModel: invalid entity type '"
                    + typeRaw + "', skipping model attach");
            return;
        }
        if (cartToModel.containsKey(cart.getUniqueId())) return;

        Location spawnLoc = cart.getLocation().clone().add(0, offsetY, 0);

        @SuppressWarnings("unchecked")
        Class<? extends LivingEntity> entityClass =
                (Class<? extends LivingEntity>) entityType.getEntityClass();

        LivingEntity model = spawnLoc.getWorld().spawn(spawnLoc, entityClass, ent -> {
            ent.setInvisible(false);
            ent.setSilent(true);
            ent.setInvulnerable(true);
            ent.setGravity(false);
            ent.setCollidable(false);
            ent.setCustomName(cart.getCustomName());
            ent.setCustomNameVisible(cart.isCustomNameVisible());
            if (ent instanceof Mob) {
                ((Mob) ent).setAI(false);
            }
        });

        model.setRemoveWhenFarAway(false);

        cartToModel.put(cart.getUniqueId(), model);
        modelToCart.put(model.getUniqueId(), cart);

        makeCartInvisible(cart);
    }

    /**
     * Synchronize the visual entity's position to its minecart.
     * Called every tick for each cart in the consist.
     */
    public void syncPosition(Minecart cart) {
        if (cart == null || cart.isDead()) {
            cleanupModel(cart);
            return;
        }
        LivingEntity model = cartToModel.get(cart.getUniqueId());
        if (model == null || model.isDead()) {
            cartToModel.remove(cart.getUniqueId());
            if (model != null) modelToCart.remove(model.getUniqueId());
            return;
        }

        Location target = cart.getLocation().clone().add(0, offsetY, 0);
        target.setYaw(cart.getLocation().getYaw());
        target.setPitch(cart.getLocation().getPitch());

        SchedulerUtil.teleportEntity(model, target);
    }

    /**
     * Remove the visual entity model for a given minecart.
     */
    public void removeModel(Minecart cart) {
        cleanupModel(cart);
        makeCartVisible(cart);
    }

    /**
     * Get the visual entity associated with a minecart.
     */
    public LivingEntity getModelEntity(Minecart cart) {
        if (cart == null) return null;
        return cartToModel.get(cart.getUniqueId());
    }

    /**
     * Get the minecart associated with a visual entity (by UUID).
     */
    public Minecart getCartByModelEntity(UUID modelEntityId) {
        return modelToCart.get(modelEntityId);
    }

    /**
     * Check if a given entity UUID corresponds to a Railway visual model entity.
     */
    public boolean isModelEntity(UUID entityId) {
        return modelToCart.containsKey(entityId);
    }

    /**
     * Check if multi-passenger mode is enabled.
     */
    public boolean isMultiPassenger() {
        return multiPassenger;
    }

    /**
     * Hide a minecart from players by making it invisible.
     */
    public void makeCartInvisible(Minecart cart) {
        if (cart == null || cart.isDead()) return;
        try {
            cart.getClass().getMethod("setInvisible", boolean.class).invoke(cart, true);
        } catch (Exception ignored) {
            // setInvisible may not be fully effective for minecarts on some versions
        }
    }

    /**
     * Restore minecart visibility (e.g., during cleanup).
     */
    public void makeCartVisible(Minecart cart) {
        if (cart == null || cart.isDead()) return;
        try {
            cart.getClass().getMethod("setInvisible", boolean.class).invoke(cart, false);
        } catch (Exception ignored) {}
    }

    /**
     * Remove all tracked model entities. Called on plugin disable.
     */
    public void shutdown() {
        for (Map.Entry<UUID, LivingEntity> entry : cartToModel.entrySet()) {
            LivingEntity model = entry.getValue();
            if (model != null && !model.isDead()) {
                model.remove();
            }
        }
        cartToModel.clear();
        modelToCart.clear();
    }

    private void cleanupModel(Minecart cart) {
        if (cart == null) return;
        LivingEntity model = cartToModel.remove(cart.getUniqueId());
        if (model != null) {
            modelToCart.remove(model.getUniqueId());
            if (!model.isDead()) {
                // Eject any passengers first
                for (org.bukkit.entity.Entity passenger : new java.util.ArrayList<>(model.getPassengers())) {
                    passenger.leaveVehicle();
                }
                model.remove();
            }
        }
    }

    private static EntityType parseEntityType(String raw) {
        if (raw == null) return null;
        try {
            EntityType t = EntityType.valueOf(raw.trim().toUpperCase());
            if (!t.isAlive()) return null;
            return t;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
