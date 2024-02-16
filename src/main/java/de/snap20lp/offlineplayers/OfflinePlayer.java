package de.snap20lp.offlineplayers;

import lombok.Getter;
import lombok.Setter;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.TargetedDisguise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class OfflinePlayer implements Listener {

    private final org.bukkit.OfflinePlayer offlinePlayer;
    private final double currentHP;
    private final ArrayList<ItemStack> savedInventoryContents;
    private final ArrayList<ItemStack> addedItems = new ArrayList<>();
    private final ArrayList<ItemStack> savedArmorContents;
    private final ItemStack mainHand;
    private final ItemStack offHand;
    private final int playerExp;
    private final String customName;
    private final int despawnTimerSeconds = OfflinePlayers.getInstance().getConfig().getInt("OfflinePlayer.cloneDe-spawnTimer.timer");
    @Setter
    private Location spawnLocation;
    private int despawnTask = 0, updateTask = 0;
    private int currentSeconds = 0;
    @Setter
    private boolean isHidden = false;
    @Setter
    private boolean cloneIsHittable = OfflinePlayers.getInstance().getConfig().getBoolean("OfflinePlayer.cloneIsHittable"), cloneHasAI = OfflinePlayers.getInstance().getConfig().getBoolean("OfflinePlayer.cloneHasAI"), isDead = false, despawnTimerEnabled = OfflinePlayers.getInstance().getConfig().getBoolean("OfflinePlayer.cloneDe-spawnTimer.enabled"),
            isBlockEntity = OfflinePlayers.getInstance().getConfig().getBoolean("OfflinePlayer.useBlockEntity");
    private LivingEntity cloneEntity;
    private TargetedDisguise disguisedEntity;

    public OfflinePlayer(Player player, ArrayList<ItemStack> savedInventoryContents, ArrayList<ItemStack> savedArmorContents, ItemStack mainHand, ItemStack offHand) {
        this.offlinePlayer = player;

        this.spawnLocation = player.getLocation();
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.savedInventoryContents = savedInventoryContents;
        this.savedArmorContents = savedArmorContents;
        this.playerExp = player.getTotalExperience();
        this.currentHP = player.getHealth();
        String customName = OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneName");
        customName = customName.replaceAll("%PLAYER_NAME", player.getName());
        customName = customName.replaceAll("%DESPAWN_TIMER%", String.valueOf(despawnTimerSeconds - currentSeconds));
        this.customName = customName;
        spawnClone();
        despawnClone();
        setHidden(true);
        startTimers();
    }

    public OfflinePlayer(org.bukkit.OfflinePlayer player, int currentSeconds, Location spawnLocation, int playerExp, double currentHP, ArrayList<ItemStack> savedInventoryContents, ArrayList<ItemStack> savedArmorContents, ItemStack mainHand, ItemStack offHand) {
        this.offlinePlayer = player;
        this.mainHand = mainHand;
        this.spawnLocation = spawnLocation;
        this.offHand = offHand;
        this.savedInventoryContents = savedInventoryContents;
        this.savedArmorContents = savedArmorContents;
        this.playerExp = playerExp;
        this.currentHP = currentHP;
        this.currentSeconds = currentSeconds;
        String customName = OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneName");
        customName = customName.replaceAll("%PLAYER_NAME", player.getName());
        customName = customName.replaceAll("%DESPAWN_TIMER%", String.valueOf(despawnTimerSeconds - currentSeconds));
        this.customName = customName;
        spawnClone();
        despawnClone();
        setHidden(true);
        startTimers();
    }

    private void startTimers() {
    updateTask =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                OfflinePlayers.getInstance(),
                () -> {
                  if (isDead()) return;
                  int distance =
                      OfflinePlayers.getInstance()
                          .getConfig()
                          .getInt("OfflinePlayer.cloneSpawnDistance");
                  AtomicBoolean isNearby = new AtomicBoolean(false);
                  cloneEntity
                      .getNearbyEntities(distance, distance, distance)
                      .forEach(
                          entity -> {
                            if (entity.getEntityId() != cloneEntity.getEntityId()
                                && entity.getEntityId()
                                    != disguisedEntity.getEntity().getEntityId()) {

                              if (entity.getType() == EntityType.PLAYER
                                  && !entity.getUniqueId().equals(offlinePlayer.getUniqueId())) {
                                isNearby.set(true);
                              } else if (entity instanceof Mob
                                  && OfflinePlayers.getInstance()
                                      .getConfig()
                                      .getBoolean("OfflinePlayer.cloneCanProvoke")) {
                                if (((Mob) entity).getTarget() == null
                                    && ((Mob) entity).hasLineOfSight(cloneEntity))
                                  ((Mob) entity).setTarget(cloneEntity);
                              }
                            }
                          });

                  if (!isNearby.get() && !isHidden) {

                    OfflinePlayer offlinePlayerClone =
                    new OfflinePlayer(
                        offlinePlayer,
                        currentSeconds,
                        cloneEntity.getLocation(),
                        playerExp,
                        cloneEntity.getHealth(),
                        savedInventoryContents,
                        savedArmorContents,
                        cloneEntity.getEquipment().getItemInMainHand(),
                        cloneEntity.getEquipment().getItemInOffHand());
                    offlinePlayerClone.replaceCloneStats(cloneEntity);

                      DisguiseAPI.undisguiseToAll(cloneEntity);
                      cloneEntity.remove();
                      despawnClone();
                      OfflinePlayers.getInstance().getOfflinePlayerList().remove(offlinePlayer.getUniqueId());
                      OfflinePlayers.getInstance().getEntityOfflinePlayerHashMap().remove(cloneEntity.getEntityId());
                      cancelUpdateTask();
                      cancelDespawnTask();

                      OfflinePlayers.getInstance().getOfflinePlayerList().put(offlinePlayer.getUniqueId(),offlinePlayerClone);
                      OfflinePlayers.getInstance().getEntityOfflinePlayerHashMap().put(offlinePlayerClone.getDisguisedEntity().getEntity().getEntityId(), offlinePlayerClone);
                  }

                  if (isNearby.get() && isHidden) {
                    spawnClone();
                    setHidden(false);
                  }
                },
                10,
                OfflinePlayers.getInstance().getConfig().getInt("OfflinePlayer.cloneUpdateTimer"));

        if (despawnTimerEnabled) {
            despawnTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(OfflinePlayers.getInstance(), new Runnable() {
                @Override
                public void run() {

                    if (currentSeconds >= despawnTimerSeconds - 1) {
                        despawnClone();
                        cancelDespawnTask();
                        return;
                    }
                    currentSeconds++;
                    if (cloneEntity.isValid()) {
                        String customName = OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneName");
                        customName = customName.replaceAll("%PLAYER_NAME", offlinePlayer.getName());
                        customName = customName.replaceAll("%DESPAWN_TIMER%", String.valueOf(despawnTimerSeconds - currentSeconds));
                        cloneEntity.setCustomName(customName);
                        disguisedEntity.getWatcher().setCustomName(customName);
                    }
                }
            }, 20, 20);
        }
    }

    private void cancelDespawnTask() {
        Bukkit.getScheduler().cancelTask(despawnTask);
    }

    private void cancelUpdateTask() {
        Bukkit.getScheduler().cancelTask(updateTask);
    }

    public void replaceCloneStats(LivingEntity entity) {

        cloneEntity.teleport(entity.getLocation());
        cloneEntity.setHealth(entity.getHealth());
        cloneEntity.setFireTicks(entity.getFireTicks());
        cloneEntity.setFallDistance(entity.getFallDistance());
        cloneEntity.setVelocity(entity.getVelocity());
        cloneEntity.setArrowsInBody(entity.getArrowsInBody());
        //cloneEntity.setFreezeTicks(entity.getFreezeTicks());
        //cloneEntity.setVisualFire(entity.isVisualFire());

    }

    public void spawnClone() {
        Entity clone;
        EntityType entityType;
        try {
            clone = offlinePlayer.getPlayer().getWorld().spawnEntity(cloneEntity != null ? cloneEntity.getLocation() : spawnLocation, EntityType.valueOf(OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneRawEntity")));
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§4[OfflinePlayers] Could not spawn clone entity! Please check your config.yml! | Exception: " + e.getMessage());
            return;
        }

        if (clone instanceof LivingEntity) {
            ((LivingEntity) clone).setCanPickupItems(false);
            if (clone instanceof Ageable)
                ((Ageable) clone).setAdult();
            clone.setSilent(true);

            if (cloneHasAI) {
                ((LivingEntity) clone).setAI(true);
                ((LivingEntity) clone).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false, false));
            }
            clone.setGravity(true);
            ((LivingEntity) clone).getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(cloneEntity == null ? currentHP : cloneEntity.getHealth());
            ((LivingEntity) clone).setHealth(cloneEntity == null ? currentHP : cloneEntity.getHealth());
            ((LivingEntity) clone).getEquipment().setArmorContents(cloneEntity == null ? savedArmorContents.toArray(new ItemStack[0]) : cloneEntity.getEquipment().getArmorContents());
            ((LivingEntity) clone).getEquipment().setItemInMainHand(cloneEntity == null ? mainHand: cloneEntity.getEquipment().getItemInMainHand());
            ((LivingEntity) clone).getEquipment().setItemInOffHand(cloneEntity == null ? offHand : cloneEntity.getEquipment().getItemInOffHand());
            offlinePlayer.getPlayer().getActivePotionEffects().forEach(potionEffect -> ((LivingEntity) clone).addPotionEffect(potionEffect));
            clone.setInvulnerable(!cloneIsHittable);
            clone.setCustomName(customName);
            this.cloneEntity = (LivingEntity) clone;

            TargetedDisguise targetedDisguise;

            if(isBlockEntity) {
                targetedDisguise = new MiscDisguise(DisguiseType.getType(EntityType.FALLING_BLOCK), Material.valueOf(OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneEntity")),0);
            } else {
                entityType = EntityType.valueOf(OfflinePlayers.getInstance().getConfig().getString("OfflinePlayer.cloneEntity"));
                if (entityType == EntityType.PLAYER) {
                    targetedDisguise = new me.libraryaddict.disguise.disguisetypes.PlayerDisguise(offlinePlayer.getPlayer().getName());
                } else {
                    targetedDisguise = new MobDisguise(DisguiseType.getType(entityType));
                }
            }

            targetedDisguise.setCustomDisguiseName(true);
            targetedDisguise.setExpires(Long.MAX_VALUE);
            targetedDisguise.getWatcher().setCustomName(customName);
            targetedDisguise.getWatcher().setYawLocked(true);
            targetedDisguise.getWatcher().setPitchLocked(true);
            targetedDisguise.getWatcher().setYawLock(offlinePlayer.getPlayer().getLocation().getYaw());
            targetedDisguise.getWatcher().setPitchLock(offlinePlayer.getPlayer().getLocation().getPitch());
            me.libraryaddict.disguise.DisguiseAPI.disguiseToAll(clone, targetedDisguise);
            disguisedEntity = targetedDisguise;
        }
    }

    public void despawnClone() {
        if (cloneEntity != null) {
            cancelUpdateTask();
            cloneEntity.remove();
        }
    }

}
