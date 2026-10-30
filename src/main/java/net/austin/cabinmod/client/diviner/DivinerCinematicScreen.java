package net.austin.cabinmod.client.diviner;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Invisible, non-pausing screen held open during the diviner's return so input can't move the player. Esc skips. */
public class DivinerCinematicScreen extends Screen {
    public DivinerCinematicScreen() {
        super(Component.translatable("screen.cabinmod.diviner_cinematic"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!DivinerCinematic.isActive()) onClose();
    }

    @Override
    public void onClose() {
        DivinerCinematic.skip();
        super.onClose();
    }
}
