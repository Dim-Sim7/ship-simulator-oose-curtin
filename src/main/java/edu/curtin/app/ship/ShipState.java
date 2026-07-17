package edu.curtin.app.ship;

/**
 * Base class for ship states
 * Flags are false by default, concrete states override what applies to it
 * States: IdleShip, MovingShip, LoadingShip, UnloadingShip, DisabledShip, DestroyedShip
 */
public abstract class ShipState {
    
    public boolean isDestroyed() { 
        return false; 
    }

    public boolean isIdle() { 
        return false; 
    }

    public boolean canRescue() { 
        return false; 
    }

    public abstract void update(Ship ship); // performs some action depending on implemented state
}
