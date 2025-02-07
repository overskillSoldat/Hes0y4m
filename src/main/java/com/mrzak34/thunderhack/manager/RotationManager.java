package com.mrzak34.thunderhack.manager;

import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.events.EventPreMotion;
import com.mrzak34.thunderhack.events.PacketEvent;
import com.mrzak34.thunderhack.mixin.mixins.IEntityPlayerSP;
import com.mrzak34.thunderhack.modules.Feature;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


public class RotationManager extends Feature {

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
    }
    public void unload() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private boolean blocking;

    private volatile float last_yaw;
    private volatile float last_pitch;


    @SubscribeEvent
    public void onPacketSend(PacketEvent.SendPost event){
        if(event.getPacket() instanceof CPacketPlayer){
            readCPacket(event.getPacket());
        }
        if(event.getPacket() instanceof CPacketPlayer.Position){
            readCPacket(event.getPacket());
        }
        if(event.getPacket() instanceof CPacketPlayer.Rotation){
            readCPacket(event.getPacket());
        }
        if(event.getPacket() instanceof CPacketPlayer.PositionRotation){
            readCPacket(event.getPacket());
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive e) {
        if(fullNullCheck()) return;

        if (e.getPacket() instanceof SPacketPlayerPosLook) {
            SPacketPlayerPosLook packet = e.getPacket();
            float yaw = packet.getYaw();
            float pitch = packet.getPitch();

            if (packet.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X_ROT))
            {
                yaw += mc.player.rotationYaw;
            }

            if (packet.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y_ROT))
            {
                pitch += mc.player.rotationPitch;
            }

            if (mc.player != null)
            {
                setServerRotations(yaw, pitch);
            }
        }
    }

    public float getServerYaw()
    {
        return last_yaw;
    }

    public float getServerPitch() {return last_pitch;}

    public void setBlocking(boolean blocking)
    {
        this.blocking = blocking;
    }

    public boolean isBlocking()
    {
        return blocking;
    }

    public void setServerRotations(float yaw, float pitch) {
        last_yaw   = yaw;
        last_pitch = pitch;
    }

    public void readCPacket(CPacketPlayer packetIn) {
        ((IEntityPlayerSP) mc.player).setLastReportedYaw(packetIn.getYaw(((IEntityPlayerSP) mc.player).getLastReportedYaw()));
        ((IEntityPlayerSP) mc.player).setLastReportedPitch(packetIn.getPitch(((IEntityPlayerSP) mc.player).getLastReportedPitch()));
        setServerRotations(packetIn.getYaw(last_yaw), packetIn.getPitch(last_pitch));
        Thunderhack.positionManager.setOnGround(packetIn.isOnGround());
    }

}