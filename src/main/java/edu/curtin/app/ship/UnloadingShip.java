package edu.curtin.app.ship;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.task.SupplyTask;

/**
 * Last ship state of a supply task
 * Waits at port for 5 ticks to unload cargo
 * On completion, notifies observers and 
 * transitions to idle if no suspended tasks
 */
public class UnloadingShip extends ShipState {
    private static final Logger logger = Logger.getLogger(UnloadingShip.class.getName());

    private int days = Ship.PORT_SUPPLY_WAIT_TIME;
    private SupplyTask supplyTask;

    public UnloadingShip(SupplyTask task) {
        if (task == null) {
            throw new ShipException("UnloadingShip requires a non-null supply task");
        }
        this.supplyTask = task;
    }

    @Override
    public boolean canRescue() { 
        return true; 
    }
    
    @Override
    public void update(Ship ship) {
        if (days > 0) {
            days--;
        }
        if (days <= 0) {
            // After unloading, supply task is complete
            supplyTask.markDelivered();
            logger.log(Level.INFO, () -> "Unloading complete, ship id=" + ship.getId());
            if (supplyTask.shouldMarkComplete()) {
                supplyTask.markComplete();
            }
            ship.completeCurrentTask();
        }
    }

}
