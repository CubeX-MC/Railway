package org.cubexmc.railway.service;

public interface DispatchStrategy {
    void tick(LineService service, long currentTick);
}


