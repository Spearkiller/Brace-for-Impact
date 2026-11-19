package net.spearkiller.braceforimpact;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(BraceforImpact.MODID)
public class BraceforImpact {
    public static final String MODID = "braceforimpact";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BraceforImpact(IEventBus modEventBus, ModContainer modContainer) {
    }
}
