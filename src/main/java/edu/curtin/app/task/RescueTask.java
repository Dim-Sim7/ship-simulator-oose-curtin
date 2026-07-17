package edu.curtin.app.task;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.ship.Ship;

// Tasks that sends a ship to rescue a disabled ship
public class RescueTask extends Task {
    private static final Logger logger = Logger.getLogger(RescueTask.class.getName());

    // Disabled ship to be rescued
    private final Ship targetShip;

    public RescueTask(Ship targetShip) {
        if (targetShip == null) {
            throw new TaskException("RescueTask requires non-null target ship");
        }
        this.targetShip = targetShip;
    }

    public Ship getTargetShip() {
        return targetShip;
    }

    @Override
    public int getTargetX() {
        return targetShip.getX();
    }

    @Override
    public int getTargetY() {
        return targetShip.getY();
    }

    @Override
    protected void performArrivalLogic(Ship ship) {
        logger.log(Level.INFO, () -> "Rescue complete: rescuerId=" + ship.getId() 
            + ", targetId=" + targetShip.getId());
        targetShip.repairTo(Ship.RESCUE_REPAIR_HEALTH);
    }

    // A rescued ship that was on a rescue mission cannot resume the mission, must head to port
    @Override
    public boolean canResume() {
        return false;
    }

    @Override
    public boolean isStillValid() {
        return !targetShip.isDestroyed() && targetShip.needsRescue();
    }

    @Override
    public boolean shouldMarkComplete() {
        return true;
    }

    @Override
    public int getPriorityLevel() {
        return TaskPriority.RESCUE.getValue();
    }

    @Override
    public TaskPriority getTaskType() {
        return TaskPriority.RESCUE;
    }

    @Override
    public String toString() {
        return "RESCUE task for disabled ship " + targetShip.getId();
    }

}