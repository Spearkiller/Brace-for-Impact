package net.spearkiller.braceforimpact;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
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

    public static final float MIN_DAMAGE_REDUCTION = 0f;
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


        /**
         * Helper method for modifying incoming damage.
         * Doesn't account for time right now, but
         * @param player
         * @param incoming
         * @return
         */
        public static float calcDamageReduction(ServerPlayer player, float incoming){

            //Sooooo what do we need to do here
            //GRACE_PERIOD_IN_TICKS is the maximum amount of time that the player can crouch for before they take full damage
            //MAX_DAMAGE_REDUCTION is the most damage that can be reduced
            //MIN_DAMAGE_REDUCTION is the least (which is 0 by default (suck it up noob))

            //time keeps ticking up... how to calculate that MATH IS HARD
            long lastCrouchTime = crouchTicks.getOrDefault(player.getUUID(), -1L);
            if (lastCrouchTime == -1L) return incoming;

            long diff = player.level().getGameTime() - lastCrouchTime;
            if (diff <= GRACE_PERIOD_IN_TICKS){
                return Math.max(0, incoming-MAX_DAMAGE_REDUCTION);
            }

            return incoming;
        }

        /*@SubscribeEvent
        public static void onLivingFall(LivingDamageEvent.Pre event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().isClientSide()) return; //Probably not needed???
            if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;

            float newDamage = calcDamageReduction(sp, event.getOriginalDamage());
            BraceforImpact.LOGGER.debug("Incoming damage is " + event.getOriginalDamage());
            BraceforImpact.LOGGER.debug("New damage is " + newDamage);

            event.setNewDamage(newDamage);
        }*/

        //I think this is fine to use instead of onLivingFall
        //This assumes the player will already be taking some damage, we're just... tweaking it?
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event){
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().isClientSide()) return; //Probably not needed???
            if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;

            float newDamage = calcDamageReduction(sp, event.getOriginalAmount());

            if (newDamage > 0){
                event.setAmount(newDamage);
            }
            else {
                event.setAmount(0);
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
