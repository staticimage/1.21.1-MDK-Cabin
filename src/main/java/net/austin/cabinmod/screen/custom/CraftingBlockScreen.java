package net.austin.cabinmod.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.diviner.CabinDialogue;
import net.austin.cabinmod.client.diviner.DivinerCinematic;
import net.austin.cabinmod.client.diviner.DivinerFx;
import net.austin.cabinmod.client.diviner.DivinerPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.austin.cabinmod.sounds.ModSounds;

// The Transient Diviner's menu. Opening it starts the cosmic ascent (client/diviner/DivinerCinematic); the
// panel then forms as glass among the stars. Without the cinematic (shader failure) it falls back to the
// original GUI texture. Up there the Cabin talks with you in a glass box beside the panel (CabinDialogue), and the
// panel answers what you do with chimes: laying in or taking out the input, each star of the progress constellation.
public class CraftingBlockScreen extends AbstractContainerScreen<CraftingBlockMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/crafting_block/crafting_block_gui.png");
    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/arrow_progress.png");
    private static final int INPUT_SLOT = 36;
    private static final int OUTPUT_SLOT = 37;

    private long openedAt;
    private long supernovaAt = -1;
    private int lastOutputCount = -1;
    private ItemStack lastInput = null;
    private int lastStarsLit;

    public CraftingBlockScreen(CraftingBlockMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        DivinerCinematic.begin(menu.blockEntity.getBlockPos());
    }

    @Override
    public void removed() {
        super.removed();
        DivinerCinematic.screenClosed(menu.hasPendingStar() || launchedHere());
    }

    private boolean launchedHere() {
        return DivinerFx.lastLaunchMillis() >= openedAt;
    }

    private boolean cinematic() {
        return DivinerCinematic.isActive();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (cinematic()) {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);   // no blur or darkening: the cosmos shows through
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width-imageWidth)/2;
        int y = (height-imageHeight)/2;
        if (!cinematic()) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f,1f,1f,1f);
            RenderSystem.setShaderTexture(0,GUI_TEXTURE);
            guiGraphics.blit(GUI_TEXTURE,x,y,0,0,imageWidth,imageHeight);
            renderProgressArrow(guiGraphics, x,y);
            return;
        }
        float reveal = DivinerCinematic.panelReveal();
        if (reveal <= 0.001f) return;
        DivinerPanel.drawGlass(guiGraphics, x, y, imageWidth, imageHeight, reveal);
        if (reveal < 0.55f) return;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            DivinerPanel.drawSlot(guiGraphics, x + slot.x - 1, y + slot.y - 1, i >= 36);
        }
        // the inventory's own slot art, showing through the glass
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.72f);
        for (int i = 0; i < 36; i++) {
            Slot slot = menu.slots.get(i);
            guiGraphics.blit(GUI_TEXTURE, x + slot.x, y + slot.y, slot.x, slot.y, 16, 16);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        float progress = menu.isCrafting() ? menu.getScaledArrowProgress() / 24f : 0f;
        DivinerPanel.drawConstellation(guiGraphics, x + 74, y + 35, progress, (System.currentTimeMillis() % 100000) / 1000f);
    }

    private void renderProgressArrow(GuiGraphics guiGraphics, int x, int y){
        if (menu.isCrafting()){
            guiGraphics.blit(ARROW_TEXTURE, x+73,y+35,0,0,menu.getScaledArrowProgress(),16,24,16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!cinematic()) {
            super.renderLabels(guiGraphics, mouseX, mouseY);
            return;
        }
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xD8F0FF, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x9CB8E8, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (cinematic() && DivinerCinematic.panelReveal() < 0.55f) {
            renderBackground(guiGraphics, mouseX, mouseY, partialTick);   // the panel is still forming
            return;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (cinematic()) {
            renderEffects(guiGraphics);
            renderDialogue(guiGraphics, mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX,mouseY);
    }

    private void renderEffects(GuiGraphics guiGraphics) {
        int x = (width-imageWidth)/2;
        int y = (height-imageHeight)/2;
        Slot output = menu.slots.get(OUTPUT_SLOT);
        int count = output.getItem().getCount();
        long now = System.currentTimeMillis();
        if (lastOutputCount >= 0 && count > lastOutputCount) {
            supernovaAt = now;
            play(ModSounds.DIVINER_CRAFT.get(), 1f, 0.6f);
            CabinDialogue.onCrafted(output.getItem());
        }
        lastOutputCount = count;
        listenToPanel();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (supernovaAt > 0) DivinerPanel.drawSupernova(guiGraphics, x + output.x + 8, y + output.y + 8, (now - supernovaAt) / 1000f, output.getItem());
        long launched = DivinerFx.lastLaunchMillis();
        if (launched >= openedAt)
            DivinerPanel.drawLaunch(guiGraphics, menu.blockEntity.getBlockPos(), x + output.x, y + output.y, (now - launched) / 1000f, 0);
        guiGraphics.pose().popPose();
    }

    /** Chimes for the input slot and the constellation, and how much the heavens swell. */
    private void listenToPanel() {
        ItemStack input = menu.slots.get(INPUT_SLOT).getItem();
        if (lastInput != null) {
            int before = lastInput.getCount(), after = input.getCount();
            boolean sameItem = ItemStack.isSameItemSameComponents(lastInput, input);
            if (after > before || (!sameItem && !input.isEmpty())) {
                play(ModSounds.DIVINER_SLOT_PLACE.get(), 0.9f + Math.min(0.4f, input.getCount() / 64f * 0.4f), 0.5f);
            } else if (after < before && !menu.isCrafting()) {
                play(ModSounds.DIVINER_SLOT_TAKE.get(), 1f, 0.45f);
            }
            if (!sameItem && !input.isEmpty()) CabinDialogue.onInputChanged(input);
        }
        lastInput = input.copy();
        int lit = menu.isCrafting() ? Math.min(6, menu.getScaledArrowProgress() * 6 / 24 + 1) : 0;
        if (lit > lastStarsLit) play(ModSounds.DIVINER_STAR_TICK.get(), 0.9f + 0.08f * lit, 0.5f);
        lastStarsLit = lit;
        DivinerCinematic.setSpaceIntensity(menu.isCrafting() ? 1f : input.isEmpty() ? 0f : 0.5f);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void renderDialogue(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!CabinDialogue.isOpen()) return;
        int w = Math.min(170, leftPos - 12);
        int x = leftPos - w - 6, y = topPos;
        if (w < 110) {   // no room beside the panel (big GUI scale): over the top of the screen instead
            w = imageWidth;
            x = leftPos;
            y = 4;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 350);
        CabinDialogue.render(guiGraphics, font, x, y, w, mouseX, mouseY, DivinerCinematic.panelReveal());
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!DivinerCinematic.panelInteractive()) return true;
        if (cinematic() && CabinDialogue.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!DivinerCinematic.panelInteractive()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!DivinerCinematic.panelInteractive()) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
