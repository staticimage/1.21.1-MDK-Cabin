package net.austin.cabinmod.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austin.cabinmod.CabinMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CraftingBlockScreen extends AbstractContainerScreen<CraftingBlockMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/crafting_block/crafting_block_gui.png");
    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/arrow_progress.png");

    public CraftingBlockScreen(CraftingBlockMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f,1f,1f,1f);
        RenderSystem.setShaderTexture(0,GUI_TEXTURE);
        int x = (width-imageWidth)/2;
        int y = (height-imageHeight)/2;

        guiGraphics.blit(GUI_TEXTURE,x,y,0,0,imageWidth,imageHeight);

        renderProgressArrow(guiGraphics, x,y);
    }

    private void renderProgressArrow(GuiGraphics guiGraphics, int x, int y){
        if (menu.isCrafting()){
            guiGraphics.blit(ARROW_TEXTURE, x+73,y+35,0,0,menu.getScaledArrowProgress(),16,24,16);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX,mouseY);
    }
}
