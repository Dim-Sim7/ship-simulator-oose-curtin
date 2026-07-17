package edu.curtin.app.ship;

import edu.curtin.app.task.Task;
import edu.curtin.app.task.TaskPriority;
import edu.curtin.app.utility.Observable;
import edu.curtin.app.utility.Position;
import edu.curtin.app.utility.Updatable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Represents a ship in the simulation
 *  Concrete subject in the observer pattern 
 *  Notifies observers of state changes, damage and task completion
 *  Extends Observable<ShipObserver> to reuse observer management functions
 *  Delegates update() behaviour to ShipState subclasses (State pattern)
 */
public class Ship extends Observable<ShipObserver> implements Updatable {
    private static final Logger logger = Logger.getLogger(Ship.class.getName());
    
    public static final int DESTROYED_HEALTH = 0;       /* Destroy ship at 0 health */
    public static final int DISABLED_HEALTH = 1;        /* Health threshold for a disabled state */
    public static final int RESCUE_REPAIR_HEALTH = 3;   /* Health recovers to 3 when rescued */
    public static final int PORT_REPAIR_HEALTH = 4;     /* Heatlth recovers to 4 at a port */
    public static final int STARTING_HEALTH = 4;         
    public static final int PORT_SUPPLY_WAIT_TIME = 5;  /* Wait 5 days(ticks) at a port when loading/unloading */

    private ShipState state;    // Destroyed, Disabled, Idle, Moving, Loading, Unloading -- controls update() behaviour
    private int id;
    private Position position;
    private int health;         // Sea Worthiness
    private Task curTask;       // Current active task (RepairAtPort, Rescue, Supply)
    private Task suspendedTask; // A ship can be rerouted for a rescue task and must resume the previous task after
    private boolean dockedAtPort = false;

    public Ship(int id, Position position) {
        if (position == null) {
            throw new ShipException("Ship requires a non-null position");
        }
        this.id = id;
        this.position = position;
        this.health = STARTING_HEALTH;
        this.state = new IdleShip();
    }

    @Override
    public void update() {
        state.update(this);
    }

    /** 
     * ---- NOTIFICATIONS ----
     * Each method passes a lambda to notifyObservers, which calls accept() on each
     * o -> o.onX(this) is shorthand for: for each observer o, call o.onX(this)
     * This calls onSomeTrigger() on each registered observer
     * https://stackoverflow.com/questions/67494505/what-is-a-consumer-in-java - Marco Tizzano
     */
    protected void notifyShipSunk() {
        notifyObservers(o -> o.onShipSunk(this));
    }

    protected void notifyShipDisabled() {
        notifyObservers(o -> o.onShipDisabled(this));
    }

    protected void notifyNeedPortRepairs() {
        notifyObservers(o -> o.onNeedPortRepairs(this));
    }

    protected void notifyTaskComplete() {
        notifyObservers(o -> o.onTaskComplete(this, curTask));
    }

    protected void notifyAbandonedRescue(Ship ship) {
        notifyObservers(o -> o.onRescueAbandoned(this, curTask));
    }

    public void notifyEnterPort(String task) {
        notifyObservers(o -> o.onEnterPort(this, task));
    }

    public void notifyExitPort(int prevX, int prevY) {
        notifyObservers(o -> o.onExitPort(this, prevX, prevY));
    }

    // ---- HEALTH ----

    public boolean needsRescue() {
        return health == DISABLED_HEALTH;
    }

    public boolean canRescue() {
        // A ship can rescue if its state allows it and it is not already on a rescue mission
        return  state.canRescue() && 
            (curTask == null || curTask.getTaskType() != TaskPriority.RESCUE);
    }

    public boolean needsPortRepair() {
        return health > DISABLED_HEALTH && health < PORT_REPAIR_HEALTH;
    }

    public void setDockedAtPort(boolean isDocked) {
        dockedAtPort = isDocked;
    }

    public boolean isDockedAtPort() {
        return dockedAtPort;
    }

    public void takeDamage() {
        if (health > DESTROYED_HEALTH) {
            health--;
        }
        checkHealthState();
    }

    public boolean isDestroyed() {
        return state.isDestroyed();
    }

    private void checkHealthState() {
        if (health <= DESTROYED_HEALTH) {
            logger.log(Level.INFO, () -> "Ship destroyed: id=" + id);
            setState(new DestroyedShip()); // Cannot recover ship 
            notifyShipSunk();
        } else if (needsRescue()) {
            // Disabled ships cannot accept tasks or move 
            logger.log(Level.INFO, () -> "Ship disabled, needs rescue: id=" + id);
            setState(new DisabledShip());
            notifyShipDisabled();
        }
    }

    public void repairTo(int health) {
        if (this.health < health) {
            this.health = Math.min(4, health);
            logger.log(Level.INFO, () -> "Ship repaired: id=" + id + ", health=" + this.health);
        }
    }

    public int getHealth() {
        return health;
    }

    public void setState(ShipState state) {
        if (state == null) {
            throw new ShipException("Ship state cannot be null: id=" + id);
        }
        this.state = state;
    }

    public ShipState getState() {
        return state;
    }

    public int getId() {
        return id;
    }

    // ---- MOVEMENT ----

    public Position getPosition() {
        return position;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public void moveRight() {
        position.stepX(1);
    }

    public void moveLeft() {
        if (position.getX() > 0) {
            position.stepX(-1);
        }
    }

    public void moveDown() {
        position.stepY(1);
    }

    public void moveUp() {
        if (position.getY() > 0) {
            position.stepY(-1);
        }
    }

    // ---- TASKS ----

    public void assignTask(Task task) {
        if (task == null) {
            throw new ShipException("Cannot assign null task to ship: id=" + id);
        }
        if (curTask != null) {
            if (task.getPriorityLevel() > curTask.getPriorityLevel()) {
                suspendTask();
            }
        }
        curTask = task;
        state = new MovingShip(curTask);
    }

    public void suspendTask() {
        // Saves the current task so it can be resumed after a rescue detour
        logger.log(Level.INFO, () -> "Task suspended for ship: id=" + id);
        suspendedTask = curTask;
        curTask = null;
    }

    /**
     * Resumes the suspended task if it is still valid, otherwise goes idle
     * Called after a task completes and a suspended task exists or a task becomes invalid
     */
    public void resumeSuspended() {
        // log abandoned rescue task
        if (curTask != null && curTask.getTaskType() == TaskPriority.RESCUE) {
            notifyAbandonedRescue(this);
        }
        if (hasSuspendedTask() && suspendedTask.canResume()) {
            logger.log(Level.INFO, () -> "Ship " + id + " resuming suspended task: " + suspendedTask.toString());
            curTask = suspendedTask;
            suspendedTask = null;
            state = new MovingShip(curTask);
        } else {
            // Ship has nothing left to do, clear tasks
            curTask = null;
            suspendedTask = null;
            logger.log(Level.INFO, () -> "No valid suspended task, ship going idle: id=" + id);
            state = new IdleShip();  // nothing to resume, go idle
        }
    }

    /**
     * Resumes the current task if still valid, otherwise falls back to resumeSuspended()
     * Called by DisabledShip when health is restored above the disabled threshold
     */
    public void resumeCurrent() {
        if (hasCurrentTask() && curTask.canResume()) {
            logger.log(Level.INFO, () -> "Ship " + id + " resuming task: " + curTask.toString());
            state = new MovingShip(curTask);
        } else {
            resumeSuspended(); // attempt to resume suspended task
        }
    }

    public void completeCurrentTask() {
        if (curTask != null && curTask.isComplete()) {
            logger.log(Level.INFO, () -> "Task completed: shipId=" + id + ", task=" + curTask.getClass().getSimpleName());
            notifyTaskComplete();
            curTask = null;
            if (hasSuspendedTask()) {
                resumeSuspended();
            } else {
                setState(new IdleShip());
            }
        }
    }

    public Task getCurrentTask() {
        return curTask;
    }

    public boolean hasCurrentTask() {
        return curTask != null && curTask.isStillValid();
    }

    public boolean hasSuspendedTask() {
        return suspendedTask != null && suspendedTask.isStillValid();
    }

    @Override
    public String toString() {
        return "ID: " + this.getId() + " -- at position (" + this.getX() + ", " + this.getY() + ")";
    }

}
