package com.mrzak34.thunderhack.modules.combat;

import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.events.GameZaloopEvent;
import com.mrzak34.thunderhack.events.PacketEvent;
import com.mrzak34.thunderhack.events.PlayerUpdateEvent;
import com.mrzak34.thunderhack.manager.EventManager;
import com.mrzak34.thunderhack.modules.Module;
import com.mrzak34.thunderhack.setting.Setting;
import com.mrzak34.thunderhack.util.CrystalUtils;
import com.mrzak34.thunderhack.util.EntityUtil;
import com.mrzak34.thunderhack.util.Timer;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAir;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;
import java.util.stream.Collectors;

public class AutoTotem extends Module {
    public AutoTotem() {
        super("AutoTotem", "AutoTotem", Category.COMBAT);
    }


    public Setting<ModeEn> mode = this.register(new Setting<>("Mode", ModeEn.SemiStrict));

    private enum ModeEn {
        Strict,
        SemiStrict,
        Matrix
    }


    public Setting<Boolean> totem = this.register(new Setting<>("Totem", true,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> gapple = this.register(new Setting<>("SwordGap", false,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> crystal = this.register(new Setting<>("Crystal", true,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> StopSprint = this.register(new Setting<>("StopSprint", false,v-> mode.getValue() != ModeEn.Matrix));

    public Setting<Float> delay =this.register( new Setting<>("Delay", 0F, 0F, 5F,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> hotbarTotem = this.register(new Setting<>("HotbarTotem", false,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Float> totemHealthThreshold = this.register(new Setting<>("TotemHealth", 5f, 0f, 36f,v-> mode.getValue() != ModeEn.Strict));
    public Setting<Boolean> rightClick = this.register(new Setting<>("RightClickGap", false, v-> gapple.getValue() && mode.getValue() == ModeEn.SemiStrict));
    public Setting<CrystalCheck> crystalCheck = this.register(new Setting<>("CrystalCheck", CrystalCheck.DAMAGE,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Float> crystalRange = this.register(new Setting<>("CrystalRange", 10f, 1f, 15f,v -> (crystalCheck.getValue() != CrystalCheck.NONE) && mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> fallCheck = this.register(new Setting<>("FallCheck", true,v-> mode.getValue() != ModeEn.Strict));
    public Setting<Float> fallDist = this.register(new Setting<>("FallDist", 15f, 0f, 50f,v -> fallCheck.getValue() && mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> totemOnElytra = this.register(new Setting<>("TotemOnElytra", true,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> extraSafe = this.register(new Setting<>("ExtraCheck", false,v -> (crystalCheck.getValue() != CrystalCheck.NONE) && mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> clearAfter = this.register(new Setting<>("SwapBack", true,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> hard = this.register(new Setting<>("AlwaysDefault", false,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Boolean> notFromHotbar = this.register(new Setting<>("NotFromHotbar", false,v-> mode.getValue() == ModeEn.SemiStrict));
    public Setting<Default> defaultItem = this.register(new Setting<>("DefaultItem", Default.TOTEM,v-> mode.getValue() == ModeEn.SemiStrict));



    public Setting<Boolean> absorptionHP = this.register(new Setting<>("absorptionHP", true,v-> mode.getValue() == ModeEn.Matrix));
    public Setting<Boolean> checkTNT = this.register(new Setting<>("CheckTNT", true,v-> mode.getValue() == ModeEn.Matrix));
    public Setting<Boolean> checkObsidian = this.register(new Setting<>("CheckObsidian", true,v-> mode.getValue() == ModeEn.Matrix));


    private final Queue<Integer> clickQueue = new LinkedList<>();

    private Timer Stricttimer = new Timer();

    private enum CrystalCheck {
        NONE,
        DAMAGE,
        RANGE
    }


    Timer timer = new Timer();
    private int swapBack = -1;

    @Override
    public void onUpdate() {
        if(mode.getValue() != ModeEn.Strict) return;
        if (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiInventory)) return;
        if(getItemSlot(Items.TOTEM_OF_UNDYING,false) != -1) {
            if (mc.player.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING) {
                moveToOffhand(getItemSlot(Items.TOTEM_OF_UNDYING,false));
            }
        } else if(getItemSlot(Items.GOLDEN_APPLE,true) != -1) {
            if (mc.player.getHeldItemOffhand().getItem() != Items.GOLDEN_APPLE && mc.player.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING) {
                moveToOffhand(getItemSlot(Items.GOLDEN_APPLE,true));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent e){
        if(mode.getValue() != ModeEn.Matrix) return;
        int totemSlot = getItemSlot(Items.TOTEM_OF_UNDYING,false);
        if (totemSlot < 9 && totemSlot != -1) {
            totemSlot += 36;
        }
        float hp = mc.player.getHealth();
        if (absorptionHP.getValue()) {
            hp = EntityUtil.getHealth(mc.player);
        }
        int prevCurrentItem = mc.player.inventory.currentItem;
        int currentItem = findNearestCurrentItem();
        boolean totemCheck = totemHealthThreshold.getValue() >= hp || crystalCheck() || (fallCheck.getValue() && mc.player.fallDistance >= fallDist.getValue() && !mc.player.isElytraFlying()) || checkTNT() || checkObsidian();
        boolean totemInHand = mc.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING;
        if (totemCheck) {
            if (totemSlot >= 0 && !totemInHand) {
                mc.playerController.windowClick(0, totemSlot, currentItem, ClickType.SWAP, mc.player);
                mc.player.connection.sendPacket(new CPacketHeldItemChange(currentItem));
                mc.player.inventory.currentItem = currentItem;
                ItemStack itemstack = mc.player.getHeldItem(EnumHand.OFF_HAND);
                mc.player.setHeldItem(EnumHand.OFF_HAND, mc.player.getHeldItem(EnumHand.MAIN_HAND));
                mc.player.setHeldItem(EnumHand.MAIN_HAND, itemstack);
                mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.SWAP_HELD_ITEMS, BlockPos.ORIGIN, EnumFacing.DOWN));
                mc.player.connection.sendPacket(new CPacketHeldItemChange(prevCurrentItem));
                mc.player.inventory.currentItem = prevCurrentItem;
                mc.playerController.windowClick(0, totemSlot, currentItem, ClickType.SWAP, mc.player);
                if (swapBack == -1)
                    swapBack = totemSlot;
                return;
            }
            if (totemInHand) {
                return;
            }
        }
        if (swapBack >= 0) {
            mc.player.connection.sendPacket(new CPacketHeldItemChange(currentItem));
            mc.player.inventory.currentItem = currentItem;
            ItemStack itemstack = mc.player.getHeldItem(EnumHand.OFF_HAND);
            mc.player.setHeldItem(EnumHand.OFF_HAND, mc.player.getHeldItem(EnumHand.MAIN_HAND));
            mc.player.setHeldItem(EnumHand.MAIN_HAND, itemstack);
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.SWAP_HELD_ITEMS, BlockPos.ORIGIN, EnumFacing.DOWN));
            mc.playerController.windowClick(0, swapBack, currentItem, ClickType.SWAP, mc.player);
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.SWAP_HELD_ITEMS, BlockPos.ORIGIN, EnumFacing.DOWN));
            itemstack = mc.player.getHeldItem(EnumHand.OFF_HAND);
            mc.player.setHeldItem(EnumHand.OFF_HAND, mc.player.getHeldItem(EnumHand.MAIN_HAND));
            mc.player.setHeldItem(EnumHand.MAIN_HAND, itemstack);
            mc.player.connection.sendPacket(new CPacketHeldItemChange(prevCurrentItem));
            mc.player.inventory.currentItem = prevCurrentItem;
            swapBack = -1;
        }
    }


    private enum Default {
        TOTEM(Items.TOTEM_OF_UNDYING),
        CRYSTAL(Items.END_CRYSTAL),
        GAPPLE(Items.GOLDEN_APPLE),
        AIR(Items.AIR),
        SHIELD(Items.SHIELD);

        public Item item;

        Default(Item item) {
            this.item = item;
        }
    }

    @SubscribeEvent
    public void onLoop(GameZaloopEvent event) {
        if (mc.player == null || mc.world == null) return;
        if(mode.getValue() != ModeEn.SemiStrict) return;

        if (!(mc.currentScreen instanceof GuiContainer)) {
            if (!clickQueue.isEmpty()) {
                if (!timer.passedMs((long) (delay.getValue() * 100))) return;
                int slot = clickQueue.poll();
                try {
                    timer.reset();
                    if(EventManager.serversprint && StopSprint.getValue())
                        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot, 0, ClickType.PICKUP, mc.player);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

                if (!mc.player.inventory.getItemStack().isEmpty()) {
                    for ( int index = 44; index >= 9; index--) {
                        if (mc.player.inventoryContainer.getSlot(index).getStack().isEmpty()) {
                            if(EventManager.serversprint && StopSprint.getValue())
                                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                            mc.playerController.windowClick(0, index, 0, ClickType.PICKUP, mc.player);
                            return;
                        }
                    }
                }

                if (totem.getValue()) {
                    if (mc.player.getHealth() + mc.player.getAbsorptionAmount() <= totemHealthThreshold.getValue()
                            || (totemOnElytra.getValue() && mc.player.isElytraFlying())
                            || (fallCheck.getValue() && mc.player.fallDistance >= fallDist.getValue() && !mc.player.isElytraFlying())) {

                        putItemIntoOffhand(Items.TOTEM_OF_UNDYING);
                        return;
                    } else if (crystalCheck.getValue() == CrystalCheck.RANGE) {
                        EntityEnderCrystal crystal = (EntityEnderCrystal) mc.world.loadedEntityList.stream()
                                .filter(e -> (e instanceof EntityEnderCrystal && mc.player.getDistance(e) <= crystalRange.getValue()))
                                .min(Comparator.comparing(c -> mc.player.getDistance(c)))
                                .orElse(null);

                        if (crystal != null) {
                            putItemIntoOffhand(Items.TOTEM_OF_UNDYING);
                            return;
                        }
                    } else if (crystalCheck.getValue() == CrystalCheck.DAMAGE) {
                        float damage = 0.0f;

                        List<Entity> crystalsInRange = mc.world.loadedEntityList.stream()
                                .filter(e -> e instanceof EntityEnderCrystal)
                                .filter(e -> mc.player.getDistance(e) <= crystalRange.getValue())
                                .collect(Collectors.toList());

                        for (Entity entity : crystalsInRange) {
                            damage += CrystalUtils.calculateDamage((EntityEnderCrystal) entity, mc.player);
                        }

                        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() - damage <= totemHealthThreshold.getValue()) {
                            putItemIntoOffhand(Items.TOTEM_OF_UNDYING);
                            return;
                        }
                    }

                    if (extraSafe.getValue()) {
                        if (crystalCheck()) {
                            putItemIntoOffhand(Items.TOTEM_OF_UNDYING);
                            return;
                        }
                    }
                }

                if (gapple.getValue() && isSword(mc.player.getHeldItemMainhand().getItem())) {
                    if (rightClick.getValue() && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
                        if (clearAfter.getValue()) {
                            putItemIntoOffhand(defaultItem.getValue().item);
                        }
                        return;
                    }
                    putItemIntoOffhand(Items.GOLDEN_APPLE);
                    return;
                }

                if (crystal.getValue()) {
                    if (Thunderhack.moduleManager.getModuleByClass(AutoCrystal.class).isEnabled()) {
                        putItemIntoOffhand(Items.END_CRYSTAL);
                        return;
                    } else if (clearAfter.getValue()) {
                        putItemIntoOffhand(defaultItem.getValue().item);
                        return;
                    }
                }
                if (hard.getValue()) {
                    if((defaultItem.getValue().item == Items.SHIELD && mc.player.cooldownTracker.hasCooldown(Items.SHIELD))  || (defaultItem.getValue().item == Items.SHIELD && findItemSlot(Items.SHIELD) == -1 && mc.player.getHeldItemOffhand().getItem() != Items.SHIELD)){
                        putItemIntoOffhand(Items.GOLDEN_APPLE);
                    } else {
                        putItemIntoOffhand(defaultItem.getValue().item);
                    }
                }
            }
        }
    }








    /*-----------------  Semi-Strict  ----------------*/

    private boolean isSword(Item item) {
        return item == Items.DIAMOND_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private int findItemSlot(Item item) {
        int itemSlot = -1;
        for (int i = notFromHotbar.getValue() ? 9 : 0; i < 36; i++) {

            ItemStack stack = mc.player.inventory.getStackInSlot(i);

            if (stack != null && stack.getItem() == item) {
                itemSlot = i;
                break;
            }

        }
        return itemSlot;
    }

    private void putItemIntoOffhand(Item item) {
        if (mc.player.getHeldItemOffhand().getItem() == item) return;
        int slot = findItemSlot(item);
        if (hotbarTotem.getValue() && item == Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.inventory.mainInventory.get(i);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    if (mc.player.inventory.currentItem != i) {
                        mc.player.inventory.currentItem = i;
                    }
                    return;
                }
            }
        }
        if (slot != -1) {
            if (delay.getValue() > 0F) {
                if (timer.passedMs((long) (delay.getValue() * 100))) {
                    if(EventManager.serversprint && StopSprint.getValue())
                        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
                    timer.reset();
                } else {
                    clickQueue.add(slot < 9 ? slot + 36 : slot);
                }

                clickQueue.add(45);
                clickQueue.add(slot < 9 ? slot + 36 : slot);
            } else {
                timer.reset();
                if(EventManager.serversprint && StopSprint.getValue())
                    mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, 45, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot < 9 ? slot + 36 : slot, 0, ClickType.PICKUP, mc.player);
            }
        }
    }

    private boolean crystalCheck() {
        float cumDmg = 0;
        ArrayList<Float> damageValues = new ArrayList<>();
        damageValues.add(calculateDamageAABB(mc.player.getPosition().add(1, 0, 0)));
        damageValues.add(calculateDamageAABB(mc.player.getPosition().add(-1, 0, 0)));
        damageValues.add(calculateDamageAABB(mc.player.getPosition().add(0, 0, 1)));
        damageValues.add(calculateDamageAABB(mc.player.getPosition().add(0, 0, -1)));
        damageValues.add(calculateDamageAABB(mc.player.getPosition()));
        for (float damage : damageValues) {
            cumDmg += damage;
            if ((((mc.player.getHealth() + mc.player.getAbsorptionAmount())) - damage) <= totemHealthThreshold.getValue()) {
                return true;
            }
        }
        return (((mc.player.getHealth() + mc.player.getAbsorptionAmount())) - cumDmg) <= totemHealthThreshold.getValue();
    }

    private float calculateDamageAABB(BlockPos pos){
        List<Entity> crystalsInAABB =  mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(pos)).stream()
                .filter(e -> e instanceof EntityEnderCrystal)
                .collect(Collectors.toList());
        float totalDamage = 0;
        for (Entity crystal : crystalsInAABB) {
            totalDamage += CrystalUtils.calculateDamage(crystal.posX, crystal.posY, crystal.posZ, mc.player);
        }
        return totalDamage;
    }
    /*---------------------------------------------*/












    /*-----------------  Strict  ----------------*/

    public void moveToOffhand(int from){
        if(!Stricttimer.passedMs(100)) return;
        if(from == -1) return;
        if(EventManager.serversprint && StopSprint.getValue())
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId, from, 0, ClickType.PICKUP, mc.player);
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId, 45, 0, ClickType.PICKUP, mc.player);
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId, from, 0, ClickType.PICKUP, mc.player);
        mc.playerController.updateController();
        Stricttimer.reset();
    }

    public static int getItemSlot(Item item, boolean gappleCheck) {
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStackInSlot = mc.player.inventory.getStackInSlot(i);
            if(!gappleCheck) {
                if (item == itemStackInSlot.getItem()) {
                    if (i < 9) i += 36;
                    return i;
                }
            } else {
                if (item == itemStackInSlot.getItem() && (!item.getRarity(itemStackInSlot).equals(EnumRarity.RARE) || (noGapples()))) {
                    if (i < 9) i += 36;
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean noGapples() {
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStackInSlot = mc.player.inventory.getStackInSlot(i);
            if (Items.GOLDEN_APPLE == itemStackInSlot.getItem() && !Items.GOLDEN_APPLE.getRarity(itemStackInSlot).equals(EnumRarity.RARE)) {
                return false;
            }
        }
        return true;
    }

    /*---------------------------------------------*/



    /*-----------------  Matrix  ----------------*/

    private boolean checkTNT() {
        if (!checkTNT.getValue()) {
            return false;
        }
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity instanceof EntityTNTPrimed && mc.player.getDistanceSq(entity) <= 25) {
                return true;
            }
            if (entity instanceof EntityMinecartTNT && mc.player.getDistanceSq(entity) <= 25) {
                return true;
            }
        }
        return false;
    }

    private boolean IsValidBlockPos(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        return state.getBlock() instanceof BlockObsidian;
    }

    private boolean checkObsidian() {
        if (!checkObsidian.getValue()) {
            return false;
        }
        BlockPos pos = getSphere(new BlockPos(Math.floor(mc.player.posX), Math.floor(mc.player.posY), Math.floor(mc.player.posZ)), 5, 6, false, true, 0).stream()
                .filter(this::IsValidBlockPos)
                .min(Comparator.comparing(blockPos -> getDistanceOfEntityToBlock(mc.player, blockPos))).orElse(null);
        return pos != null;
    }

    public static List<BlockPos> getSphere(final BlockPos blockPos, final float n, final int n2, final boolean b,
                                           final boolean b2, final int n3) {
        final ArrayList<BlockPos> list = new ArrayList<BlockPos>();
        final int x = blockPos.getX();
        final int y = blockPos.getY();
        final int z = blockPos.getZ();
        for (int n4 = x - (int) n; n4 <= x + n; ++n4) {
            for (int n5 = z - (int) n; n5 <= z + n; ++n5) {
                for (int n6 = b2 ? (y - (int) n) : y; n6 < (b2 ? (y + n) : ((float) (y + n2))); ++n6) {
                    final double n7 = (x - n4) * (x - n4) + (z - n5) * (z - n5) + (b2 ? ((y - n6) * (y - n6)) : 0);
                    if (n7 < n * n && (!b || n7 >= (n - 1.0f) * (n - 1.0f))) {
                        list.add(new BlockPos(n4, n6 + n3, n5));
                    }
                }
            }
        }
        return list;
    }

    public static double getDistanceOfEntityToBlock(final Entity entity, final BlockPos blockPos) {
        return getDistance(entity.posX, entity.posY, entity.posZ, blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public static double getDistance(final double n, final double n2, final double n3, final double n4, final double n5, final double n6) {
        final double n7 = n - n4;
        final double n8 = n2 - n5;
        final double n9 = n3 - n6;
        return MathHelper.sqrt(n7 * n7 + n8 * n8 + n9 * n9);
    }

    public static int findNearestCurrentItem() {
        int currentItem = mc.player.inventory.currentItem;
        if (currentItem == 8) {
            return 7;
        }
        if (currentItem == 0) {
            return 1;
        }
        return currentItem - 1;
    }
    /*---------------------------------------------*/

}
