package com.mrzak34.thunderhack.mixin.mixins;

import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.modules.misc.Shulkerception;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.SlotShulkerBox;
import org.spongepowered.asm.mixin.Mixin;


@Mixin({ SlotShulkerBox.class })
public class MixinSlotShulkerBox {
    @Inject(method = { "isItemValid" }, at = { @At("HEAD") }, cancellable = true)
    public void isItemValid(final ItemStack stack, final CallbackInfoReturnable<Boolean> ci) {
        if (Thunderhack.moduleManager.getModuleByClass(Shulkerception.class).isEnabled() && Block.getBlockFromItem(stack.getItem()) instanceof BlockShulkerBox) {
            ci.setReturnValue(true);
        }
    }
}