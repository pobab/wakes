package com.goby56.wakes.config;

import com.goby56.wakes.render.debug.WakeDebugRenderer;
import com.goby56.wakes.utils.WakeNode;
import com.goby56.wakes.utils.WakesUtils;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.gui.screen.Screen;

public class ClothConfigScreen {
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(WakesUtils.translatable("title", "config"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory debug = builder.getOrCreateCategory(WakesUtils.translatable("category", "debug"));

        debug.addEntry(entryBuilder.startFloatField(WakesUtils.translatable("option", "wave_speed"), WakeNode.waveSpeed)
                .setDefaultValue(WakeNode.waveSpeed)
                .setSaveConsumer(WakeNode::setWaveSpeed)
                .build());

        debug.addEntry(entryBuilder.startFloatField(WakesUtils.translatable("option", "wave_initial_strength"), WakeNode.initialStrength)
                .setDefaultValue(WakeNode.initialStrength)
                .setSaveConsumer(WakeNode::setInitialStrength)
                .build());

        debug.addEntry(entryBuilder.startFloatField(WakesUtils.translatable("option", "wave_decay"), WakeNode.waveDecay)
                .setDefaultValue(WakeNode.waveDecay)
                .setSaveConsumer(WakeNode::setWaveDecay)
                .build());

        debug.addEntry(entryBuilder.startIntField(WakesUtils.translatable("option", "flood_fill_distance"), WakeNode.floodFillDistance)
                .setDefaultValue(WakeNode.floodFillDistance)
                .setSaveConsumer(WakeNode::setFloodFillDistance)
                .build());

        debug.addEntry(entryBuilder.startBooleanToggle(WakesUtils.translatable("option", "use_9_point_stencil"), WakeNode.use9PointStencil)
                .setDefaultValue(WakeNode.use9PointStencil)
                .setSaveConsumer(WakeNode::setUse9PointStencil)
                .build());

        debug.addEntry(entryBuilder.startBooleanToggle(WakesUtils.translatable("option", "draw_debug_boxes"), WakeDebugRenderer.drawDebugBoxes)
                .setDefaultValue(WakeDebugRenderer.drawDebugBoxes)
                .setSaveConsumer(b -> WakeDebugRenderer.drawDebugBoxes = b)
                .build());

        return builder.build();
    }
}
