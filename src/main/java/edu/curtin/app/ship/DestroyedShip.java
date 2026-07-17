package edu.curtin.app.ship;

/**
 * Represents a destroyed ship
 * Health reduced to 0, awaiting removal from simulation
 */
public class DestroyedShip extends ShipState {

        @Override
    public void update(Ship ship) {
        // do nothing
    }

    @Override
    public boolean isDestroyed() {
        return true;
    }

    
}
