package edu.curtin.app.ship;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.task.Task;

/**
 * Represents the state of a ship that is currently travelling
 * toward a task destination
 */
public class MovingShip extends ShipState {
    private static final Logger logger = Logger.getLogger(MovingShip.class.getName());

    private final Task task;

    public MovingShip(Task task) {
        if (task == null) {
            throw new ShipException("MovingShip requires a non-null task");
        }
        this.task = task;
    }

    @Override
    public void update(Ship ship) {
        // Continue movement only while the task is still valid
        if (task.isStillValid()) {

            // Store previous coordinates to detect movement
            int prevX = ship.getX();
            int prevY = ship.getY();

            moveTowards(ship, task.getTargetX(), task.getTargetY());
            boolean moved = ship.getX() != prevX || ship.getY() != prevY;

            // If the ship moved away from a port it is docked at, notify exit
            if (moved && ship.isDockedAtPort()) {
                ship.setDockedAtPort(false);
                ship.notifyExitPort(prevX, prevY);
            }

            // If the ship has arrived at the target then call the task arrival logic
            if (isAt(ship, task.getTargetX(), task.getTargetY())) {
                
                logger.log(Level.INFO, () -> "Ship arrived at target: id=" + ship.getId() 
                    + ", x=" + task.getTargetX() + ", y=" + task.getTargetY());

                task.onArrival(ship);
                if (task.isComplete()) {
                    ship.completeCurrentTask(); // only fires for non-supply tasks
                }
            }
        } else { // task no longer valid, resume suspended task if any
            ship.resumeSuspended();
        }
    }
    
    @Override
    public boolean canRescue() { return true; }

    // Moves one step at a time to the target
    private void moveTowards(Ship ship, int targetX, int targetY) {
        if (ship.getX() < targetX) {
            ship.moveRight();
        }
        else if (ship.getX() > targetX) {
            ship.moveLeft();
        }

        if (ship.getY() < targetY) {
            ship.moveDown();
        }
        else if (ship.getY() > targetY) {
            ship.moveUp();
        }
    }

    private boolean isAt(Ship ship, int targetX, int targetY) {
        return ship.getX() == targetX && ship.getY() == targetY;
    }


}
