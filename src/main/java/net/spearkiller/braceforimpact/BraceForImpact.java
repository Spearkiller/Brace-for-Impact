package net.spearkiller.braceforimpact;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import java.util.HashMap;
import java.util.UUID;

@Mod(BraceforImpact.MODID)
public class BraceforImpact {
    public static final String MODID = "braceforimpact";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final float MAX_DAMAGE_REDUCTION = 3.0f; //TODO make it so damage reduced is greater the closer the diff is to 0?
    public static final int GRACE_PERIOD_IN_TICKS = 5; //10 ticks, so, half a second. TODO: make this configurable

    private static HashMap<UUID, Long> crouchTicks = new HashMap<>();

    public BraceforImpact(IEventBus modEventBus, ModContainer modContainer) {
        //I don't think I need anything here until I set up config?
    }

    @EventBusSubscriber(modid = BraceforImpact.MODID)
    public static class ServerModEvents {

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().isClientSide()) return; //Probably not needed???

            if (sp.isCrouching()) {
                if (!crouchTicks.containsKey(sp.getUUID())) {
                    crouchTicks.put(sp.getUUID(), sp.level().getGameTime());
                    BraceforImpact.LOGGER.debug("Logging " + sp.getUUID() + " crouching");
                }
            } else {
                if (crouchTicks.remove(sp.getUUID()) != null)
                    BraceforImpact.LOGGER.debug("Removing " + sp.getUUID() + " crouching");
            }

        }


        @SubscribeEvent
        public static void onLivingFall(LivingDamageEvent.Pre event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().isClientSide()) return; //Probably not needed???
            if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;

            if (crouchTicks.containsKey(sp.getUUID())) {
                long firstCrouch = crouchTicks.get(sp.getUUID());

                long diff = sp.level().getGameTime() - firstCrouch;
                BraceforImpact.LOGGER.debug("Diff of " + sp.getUUID() + " crouching is " + diff);
                if (diff <= GRACE_PERIOD_IN_TICKS) {
                    float dmg = Math.max(0, event.getOriginalDamage() - MAX_DAMAGE_REDUCTION);
                    BraceforImpact.LOGGER.debug("Old damage of " + sp.getUUID() + " is " + event.getOriginalDamage());
                    event.setNewDamage(dmg);
                    BraceforImpact.LOGGER.debug("New damage of " + sp.getUUID() + " is " + event.getNewDamage());
                }
            }
        }

        //Dirty hack to fix some UI/UX things. I do wonder if I could do it all here and don't know why I don't rn.
        //TODO: change that?
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event){
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().isClientSide()) return; //Probably not needed???
            if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;

            long firstCrouch = crouchTicks.getOrDefault(sp.getUUID(), -1L);
            if (firstCrouch == -1) return;
            long diff = sp.level().getGameTime() - firstCrouch;

            BraceforImpact.LOGGER.debug("Incoming damage is " + event.getAmount()); //This doesn't get the right value
            BraceforImpact.LOGGER.debug("Diff is " + diff);
            BraceforImpact.LOGGER.debug("Crouch timer contains player: " + crouchTicks.containsKey(sp.getUUID()));
            //Probably all the relevant checks
            if (crouchTicks.containsKey(sp.getUUID()) && event.getAmount() <= 0 && diff <= GRACE_PERIOD_IN_TICKS) {
                BraceforImpact.LOGGER.debug("Cancelling damage");
                event.setCanceled(true);
            }
        }

        //Just a bit of cleanup to prevent memory leaks or something i know what I mean
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            crouchTicks.remove(event.getEntity().getUUID());
        }
    }
}
