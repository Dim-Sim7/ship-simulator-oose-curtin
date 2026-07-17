package edu.curtin.app.ship;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.task.SupplyTask;

/**
 * Currently on a supply task and waits 5 days to load cargo at a port
 * Once finished will start moving towards the destination port
 */
public class LoadingShip extends ShipState {
    private static final Logger logger = Logger.getLogger(LoadingShip.class.getName());
    // need to wait 5 days(ticks) to load ship
    private int days = Ship.PORT_SUPPLY_WAIT_TIME;
    private SupplyTask supplyTask;

    public LoadingShip(SupplyTask task) {
        if (task == null) {
            throw new ShipException("LoadingShip requires a non-null supply task");
        }
        this.supplyTask = task;
    }

    @Override
    public void update(Ship ship) {
        if (days > 0) {
            days--;
        }
        if (days <= 0) {
            logger.log(Level.INFO, () -> "Loading complete, ship departing: id=" + ship.getId());
            // After loading set the ship to moving state to move to unloading destination
            supplyTask.markPickedUp();
            ship.setState(new MovingShip(supplyTask));
        }
    }
    
    @Override
    public boolean canRescue() { 
        return true; 
    }

}
