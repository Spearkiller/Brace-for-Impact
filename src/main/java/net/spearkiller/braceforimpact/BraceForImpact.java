package net.spearkiller.braceforimpact;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(BraceForImpact.MOD_ID)
public class BraceForImpact
{
    public static final String MOD_ID = "braceforimpact";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HashMap<UUID, Integer> crouchTimer = new HashMap<>();

    public BraceForImpact(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }


    //How many ticks before landing can the player press shift to prevent (some) damage
    public static final  GameRules.Key<GameRules.IntegerValue> BRACE_TICK_AMOUNT =
            GameRules.register("braceTickAmount", GameRules.Category.PLAYER, GameRules.IntegerValue.create(5));

    //How much health (in half-hearts) should be subtracted from the fall damage.
    public static final  GameRules.Key<GameRules.IntegerValue> BRACE_MAX_REDUCTION =
            GameRules.register("braceMaxReduction", GameRules.Category.PLAYER, GameRules.IntegerValue.create(5));


    //How much should the final fall damage be multiplied by when crouch-landing
    public static final  GameRules.Key<GameRules.IntegerValue> BRACE_FALL_MULTIPLIER =
            GameRules.register("braceFallMultiplier", GameRules.Category.PLAYER, GameRules.IntegerValue.create(100));

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ServerModEvents {


        //Logic to determine if the player should be able to fly using beacon ring
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (! (event.player instanceof ServerPlayer sp)) return;

            UUID id = sp.getUUID();
            int timerDuration = sp.level().getGameRules().getRule(BRACE_TICK_AMOUNT).get();

            //Check if the player is falling - I think these are all the checks one should need???????
            if (!sp.onGround() && sp.getDeltaMovement().y < 0 && !sp.getAbilities().flying) {
                //LOGGER.debug("Falling!");

                //if they are falling, and they are crouching
                if (sp.isCrouching()) {
                    //If they just started crouching
                    if (!crouchTimer.containsKey(id)) {
                        //LOGGER.debug("Started crouching");
                        crouchTimer.put(id, 0);
                        //Player is holding down crouch
                    } else {
                        //LOGGER.debug("Counting: " + crouchTimer.getOrDefault(id, -1));
                        //Count down the timer
                        crouchTimer.computeIfPresent(id, (k, v) -> v + 1);
                    }
                }
                //The player is falling but not crouching
                else {
                    crouchTimer.remove(id);
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerFall(LivingHurtEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;


            int maxFallReduction = sp.level().getGameRules().getRule(BRACE_MAX_REDUCTION).get();
            float fm = (sp.level().getGameRules().getRule(BRACE_FALL_MULTIPLIER).get())/100f;
            float fallMult = Math.max( Math.min(fm, 1), 0 ); //Don't make the player take more or negative fall damage. That's silly.
            int timerDuration = sp.level().getGameRules().getRule(BRACE_TICK_AMOUNT).get();

            UUID id = sp.getUUID();

            boolean crouchedBetweenTicks = (sp.isCrouching() && !crouchTimer.containsKey(id));

            Integer timer = crouchTimer.getOrDefault(id, timerDuration+1);

            //LOGGER.debug("Duration: " + timerDuration);
            //LOGGER.debug("Timer: " + timer);

            //The player doesn't crouch on ticks - so we need to check if they have crouched between ticks.
            //If they have, they can't have been crouching for long, so we'll ignore damage on them.
            if (timer <= timerDuration || crouchedBetweenTicks) {
                //LOGGER.debug("Timer: " + timer + " || Between ticks: " + crouchedBetweenTicks);
                float original = event.getAmount();
                float reduced = Math.max((original-maxFallReduction) * fallMult, 0);
                event.setAmount(reduced);

                //If the player ends up taking 0 resultant damage, cancel the damage altogether
                if (reduced <= 0) event.setCanceled(true);
            }
            if (crouchedBetweenTicks) event.setCanceled(true);
            crouchTimer.remove(id);
        }
    }
}
