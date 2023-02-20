package com.mrzak34.thunderhack.modules.movement;

import com.mrzak34.thunderhack.events.*;
import com.mrzak34.thunderhack.manager.EventManager;
import com.mrzak34.thunderhack.modules.Module;
import com.mrzak34.thunderhack.setting.Setting;
import com.mrzak34.thunderhack.util.InventoryUtil;
import com.mrzak34.thunderhack.util.MovementUtil;
import com.mrzak34.thunderhack.util.math.MatrixStrafeMovement;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static com.mrzak34.thunderhack.modules.combat.Aura.isInLiquid;


public class Strafe extends Module {
    public Strafe() {
        super("Strafe", "matrix only!!!", Category.MOVEMENT);
    }


    private Setting<Mode> mode = this.register (new Setting<>("Mode", Mode.Matrix));
    private enum Mode {
        Matrix, SunriseElytra
    }
    private  Setting<Float> speed = this.register(new Setting<Float>("Speed", 0.5f, 0.0f, 2f,v -> mode.getValue() == Mode.SunriseElytra));
    private  Setting<Float> reduction = this.register(new Setting<Float>("Reduction", 0.9f, 0.0f, 1f,v -> mode.getValue() == Mode.SunriseElytra));
    public Setting<Boolean> onlyDown = register(new Setting<>("OnlyDown", false));

    boolean skip = false;

    @Override
    public void onEnable(){
        skip = true;
    }

    @SubscribeEvent
    public void onUpdate(PlayerUpdateEvent event) {
        if(mode.getValue() != Mode.SunriseElytra)return;
        if (mc.player.ticksExisted % 6 == 0) {
            int elytra = InventoryUtil.getElytra();
            if (elytra == -1) {
                this.toggle();
            } else {
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING));
                disabler(elytra);
            }
        }
        if(!skip) {
            if (mc.player.onGround && !mc.gameSettings.keyBindJump.pressed) {
                mc.player.jump();
                MovementUtil.setMotion(getSpeed() * reduction.getValue());
            }
            if (!mc.world.getCollisionBoxes(mc.player, mc.player.getEntityBoundingBox().offset(0.0, -0.84, 0.0f)).isEmpty() && (!onlyDown.getValue() || mc.player.fallDistance > 0.05))
                MovementUtil.setMotion(speed.getValue());
        } else {
            if(mc.player.onGround)
                mc.player.jump();
            if(mc.player.fallDistance > 0.05) {
                skip = false;
            }
        }
    }


    public static float getSpeed() {
        return (float) Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
    }


    public static void disabler(int elytra) {
        if (elytra != -2) {
            mc.playerController.windowClick(0, elytra, 1, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, 6, 1, ClickType.PICKUP, mc.player);
        }
        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
        if (elytra != -2) {
            mc.playerController.windowClick(0, 6, 1, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, elytra, 1, ClickType.PICKUP, mc.player);
        }
    }


    @SubscribeEvent
    public void onPacketReceive(PacketEvent event) {
        if(fullNullCheck()) return;
        if (event.getPacket() instanceof SPacketPlayerPosLook) {
            this.toggle();
        }
    }


    public static boolean needSprintState;
    int waterTicks;



    @SubscribeEvent
    public void onSprint(EventSprint e){
        MatrixStrafeMovement.actionEvent(e);
        if (strafes()) {
            if (EventManager.serversprint != needSprintState) {
                e.setSprintState(!EventManager.serversprint);
            }
        }
    }


    @SubscribeEvent
    public void onMove(MatrixMove move){
        if(mode.getValue() != Mode.Matrix)return;

        if (isInLiquid()) {
            waterTicks = 10;
        } else {
            waterTicks--;
        }
        if (strafes()) {
            double forward = mc.player.movementInput.moveForward;
            double strafe = mc.player.movementInput.moveStrafe;
            float yaw = mc.player.rotationYaw;
            if (forward == 0.0 && strafe == 0.0) {
                MatrixStrafeMovement.oldSpeed = 0;
                move.setMotionX(0);
                move.setMotionZ(0);
            } else {
                if (forward != 0.0) {
                    if (strafe > 0.0) {
                        yaw += ((forward > 0.0) ? -45 : 45);
                    } else if (strafe < 0.0) {
                        yaw += ((forward > 0.0) ? 45 : -45);
                    }
                    strafe = 0.0;
                    if (forward > 0.0) {
                        forward = 1.0;
                    } else if (forward < 0.0) {
                        forward = -1.0;
                    }
                }
                double speed = MatrixStrafeMovement.calculateSpeed(move);
                move.setMotionX(forward * speed * Math.cos(Math.toRadians(yaw + 90.0f)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0f)));
                move.setMotionZ(forward * speed * Math.sin(Math.toRadians(yaw + 90.0f)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0f)));
            }
        } else {
            MatrixStrafeMovement.oldSpeed = 0;
        }
        move.setCanceled(true);
    }

    @SubscribeEvent
    public void onPostMove(EventPostMove move){
        MatrixStrafeMovement.postMove(move.getHorizontalMove());
    }



    public boolean strafes() {
        if (mc.player.isSneaking()) {
            return false;
        }
        if (mc.player.isInLava()) {
            return false;
        }
        if (mc.player.isInWater() || waterTicks > 0) {
            return false;
        }
        if (mc.player.isInWeb) {
            return false;
        }
        return !mc.player.capabilities.isFlying;
    }
}