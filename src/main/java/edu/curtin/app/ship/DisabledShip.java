package edu.curtin.app.ship;

/**
 * Ship health is at DISABLED_HEALTH
 * Once rescued, will resume tasks
 */
public class DisabledShip extends ShipState {
    
    @Override
    public void update(Ship ship) {
        // A disabled ship recovers when health is restored above disabled threshold (e.g. by rescue)
        if (ship.getHealth() > Ship.DISABLED_HEALTH) {
            // resume the current task if still valid
            ship.resumeCurrent();
        } 
    }   
}
