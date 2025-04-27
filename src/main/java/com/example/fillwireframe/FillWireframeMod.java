package com.example.fillwireframe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class FillWireframeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FillWireframeCommand.register(dispatcher);
        });
    }
}
