package com.mrzak34.thunderhack.mixin.mixins;

import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.events.*;
import com.mrzak34.thunderhack.modules.combat.HitBoxes;
import com.mrzak34.thunderhack.util.Timer;
import com.mrzak34.thunderhack.util.phobos.EntityType;
import com.mrzak34.thunderhack.util.phobos.IEntity;
import com.mrzak34.thunderhack.util.phobos.IEntityNoInterp;
import net.minecraft.world.*;
import net.minecraft.entity.*;
import net.minecraft.util.math.*;
import net.minecraftforge.common.*;
import net.minecraftforge.fml.common.eventhandler.*;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mrzak34.thunderhack.util.Util.mc;


@Mixin({ Entity.class })
public abstract class MixinEntity implements IEntity
{

    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public double motionX;
    @Shadow
    public double motionY;
    @Shadow
    public double motionZ;
    @Shadow
    public float rotationYaw;
    @Shadow
    public float rotationPitch;
    @Shadow
    public boolean onGround;
    @Shadow
    public World world;

    @Shadow
    public abstract boolean isSneaking();

    @Shadow
    public abstract AxisAlignedBB getEntityBoundingBox();


    @Shadow
    public float stepHeight;


    // Credit: auto
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endSection()V", shift = At.Shift.BEFORE, ordinal = 0))
    public void onMove(MoverType type, double x, double y, double z, CallbackInfo info) {
        if (((Entity) (Object) this).equals(mc.player)) {
            StepEvent event = new StepEvent(getEntityBoundingBox(), stepHeight);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                stepHeight = event.getHeight();
            }
        }
    }


    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    public void onTurn(float yaw, float pitch, CallbackInfo ci) {
        TurnEvent event = new TurnEvent(yaw, pitch);
        MinecraftForge.EVENT_BUS.post((Event)event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Redirect(method = { "applyEntityCollision" },  at = @At(value = "INVOKE",  target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
    public void addVelocityHook(final Entity entity,  final double x,  final double y,  final double z) {
        final PushEvent event = new PushEvent();
        MinecraftForge.EVENT_BUS.post((Event)event);
    }

    @Inject(method = "getCollisionBorderSize", at = @At("HEAD"), cancellable = true)
    private void getCollisionBorderSize(final CallbackInfoReturnable<Float> callbackInfoReturnable) {
        if (Thunderhack.moduleManager.getModuleByClass(HitBoxes.class).isEnabled())
            callbackInfoReturnable.setReturnValue(0.1F + Thunderhack.moduleManager.getModuleByClass(HitBoxes.class).expand.getValue());
    }


    @Shadow
    public double prevPosX;

    @Shadow
    public double prevPosZ;

    @Shadow
    public double lastTickPosX;

    @Shadow
    public double lastTickPosY;

    @Shadow
    public double lastTickPosZ;

    @Shadow
    public boolean isDead;

    @Shadow
    public float width;

    @Shadow
    public float prevRotationYaw;

    @Shadow
    public float height;

    private final Timer pseudoTimer= new Timer();
    private boolean pseudoDead;
    private long stamp;

    @Shadow
    public abstract boolean equals(Object p_equals_1_);

    @Shadow
    public abstract String getName();


    @Override
    public boolean isPseudoDeadT()
    {
        if (pseudoDead && !isDead && pseudoTimer.passedMs(500))
        {
            pseudoDead = false;
        }

        return pseudoDead;
    }

    @Override
    public void setPseudoDeadT(boolean pseudoDead)
    {
        this.pseudoDead = pseudoDead;
        if (pseudoDead)
        {
            pseudoTimer.reset();
        }
    }

    @Override
    public Timer getPseudoTimeT()
    {
        return pseudoTimer;
    }

    @Override
    public long getTimeStampT()
    {
        return stamp;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void ctrHook(CallbackInfo info)
    {
       // this.type = EntityType.getEntityType(Entity.class.cast(mc.player));
        this.stamp = System.currentTimeMillis();
    }

    @Inject(method = "setPositionAndRotation", at = @At("RETURN"))
    public void setPositionAndRotationHook(double x,
                                           double y,
                                           double z,
                                           float yaw,
                                           float pitch,
                                           CallbackInfo ci)
    {
        if (this instanceof IEntityNoInterp)
        {
            ((IEntityNoInterp) mc.player).setNoInterpX(x);
            ((IEntityNoInterp) mc.player).setNoInterpY(y);
            ((IEntityNoInterp) mc.player).setNoInterpZ(z);
        }
    }





}