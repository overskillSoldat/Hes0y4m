package com.mrzak34.thunderhack.events;

import com.mrzak34.thunderhack.util.phobos.SafeRunnable;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayDeque;
import java.util.Deque;

@Cancelable
public class EventPreMotion extends Event {

    public float getYaw() {
        return yaw;
    }

    float yaw;

    public float getPitch() {
        return pitch;
    }

    float pitch;
    public EventPreMotion(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

}