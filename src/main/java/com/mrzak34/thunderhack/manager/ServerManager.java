package com.mrzak34.thunderhack.manager;

import com.mrzak34.thunderhack.events.PacketEvent;
import com.mrzak34.thunderhack.modules.Feature;
import com.mrzak34.thunderhack.util.Timer;
import com.mrzak34.thunderhack.util.Util;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Objects;

public class ServerManager extends Feature {

    private final Timer timeDelay;
    private final ArrayDeque<Float> tpsResult;
    private long time;
    private float tps;

    public void setTPS(float tps) {
        this.tps = tps;
    }

    public Timer getDelayTimer() {
        return timeDelay;
    }

    public ServerManager() {
        this.tpsResult = new ArrayDeque<>(20);
        this.timeDelay = new Timer();
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public float getTPS() {
        return round(this.tps);
    }
    private float round(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public ArrayDeque<Float> getTPSResults() {
        return tpsResult;
    }

    public int getPing() {
        if (Util.mc.world == null || Util.mc.player == null) {
            return 0;
        }
        try {
            return Objects.requireNonNull(Util.mc.getConnection()).getPlayerInfo(Util.mc.getConnection().getGameProfile().getId()).getResponseTime();
        } catch (Exception e) {
            return 0;
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof SPacketChat)) {
            getDelayTimer().reset();
        }
        if (event.getPacket() instanceof SPacketTimeUpdate) {
            if (getTime() != 0L) {
                if (getTPSResults().size() > 20) {
                    getTPSResults().poll();
                }
                getTPSResults().add(20.0f * (1000.0f / (float) (System.currentTimeMillis() - getTime())));
                float f = 0.0f;
                for(Float value : getTPSResults()) {
                    f += Math.max(0.0f, Math.min(20.0f, value));
                }
                setTPS(f /= (float) getTPSResults().size());
            }
            setTime( System.currentTimeMillis());
        }
    }

    public void init(){
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void unload(){
        MinecraftForge.EVENT_BUS.register(this);
    }
}
