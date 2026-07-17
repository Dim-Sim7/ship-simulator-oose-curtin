package edu.curtin.app.ship;


/**
 * A ship starts idle or when all tasks are completed
 * Idle ships simply wait for a task or if it needs repair will be assigned repairAtPortTask()
 */
public class IdleShip extends ShipState {
    
    @Override
    public void update(Ship ship) {
        // On each update, check that the idle ship needs to head to port for repairs
        // Once the notification is sent, this transitions to MovingShip and moves to a port
        if (ship.needsPortRepair()) {
            ship.notifyNeedPortRepairs();
        }
    }

    @Override
    public boolean canRescue() { 
        return true; 
    }
    
    @Override
    public boolean isIdle() {
        return true;
    }

}
