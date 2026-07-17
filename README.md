<<<<<<< HEAD
Criteria (e) - Observer Pattern

I used the observer pattern with Ships as the subject and Fleet Coordinator as the observer, using a generic Observable<T> interface
to remove observer specific variables and methods out of Ship such as the observer list, add / remove and notifyObservers while also
allowing extensibility for potential future Observer relationships. Polymorphism allows ships to notify Ship Observers without needing to know their concrete implementations. Observers simply listen for events sent by ships they are subscribed to.

My initial design also had Storm as being Observable with StormManager observing them, but since storms only did one specific thing
and has a limited specific lifecycle, that implementation did not make sense and it is easier to just handle all Storm functionality
within Storm Manager's update() method.

The notifyObservers(Consumer<? super T> action) in Observable exposes a functional interface 'Consumer' that allows me to perform
an action over each element of an Iterable container by just passing a lambda expression to the exposed forEach method. This allows me
to decouple the observable object from the concrete observer implementation while avoiding repeating iterative logic (for (...))
whenever I need to broadcast notifications to observers. The ship itself only needs to extend Observable<ShipObserver>
rather than holding a list of observers, that is handled in Observable<T>. Without it, the Ship itself holds and manages the list of observers.

Ship observers are subscribed to ships in Ship Spawner, allowing me to subscribe one or a list of observers for extensibility. Currently there is only one concrete observer which is Fleet Coordinator. Journal is not a seperate observer as the journal logging statements depend on outcomes from the ShipObserver methods. Such as onShipDisabled(), if a disabled ship doesnt find a rescuer, the journal logs the ignored rescue. The journal cannot know this unless it is associated with Fleet Coordinator. Fleet Coordinator also assigns supply tasks to Ships, which the journal needs log whenever a supply run is accepted or ignored. I made this as a trade-off of tighter coupling between Fleet Coordinator and Journal for more specific journal logging that a journal simply observing ships cannot do.

Ships notify Ship Observers to different events. This in turn changes the simulation. For example, if a ship gets destroyed (health == 0) then it sends out a notification to FleetCoordinator, which then removes the ship from Fleet's List<Ship> (removes it from the simulation). If the ship is idle and becomes damaged, the ship notifies Fleet Coordinator which assigns that ship to move to the closest Port for repairs. Ships notify on port exit and enters for logging purposes, they also notify when they complete a task so that it can also be logged. The ship itself handles its internal task state because a ship knows if it has any suspended tasks to continue with or to go idle if it completes a task and has nothing to continue with.


Criteria (f) - State Pattern

I used the state pattern to control ship behaviour. Each ship holds a reference to a ShipState, which can be in 1 of 6 different states, each having their own update() behaviour which gets called each tick. The Ship does not need to know about the concrete state types, instead it delegates update() behaviour using polymorphism to the current State object. Ship transitions also occur in different areas of the code base with different triggers.

IdleShip ----

This is the initial state of all ships when they are created. An idle ship can accept all tasks (supply, rescue and repair at port), however, only an idle ship can be assigned a repair at port task since the idle update() method is the only place where a ship notifies that it needs port repairs if its health reaches a threshold.

A ship returns to idle once it completes all current and suspended tasks or if those tasks become invalid, such as when a ship gets destroyed while attempting to rescue it. The ship itself manages it own internal task logic as seen with completeCurrentTask(), resumeCurrent() and resumeSuspended().

DisabledShip ---

Whenever a ship takes damage, it checks if its health is at 1. If it is then a notification is sent out that it needs rescue and the ship transitions into a Disabled State. In this state the ship cannot continue on any current task and cannot move. This is marked with a red background on the lake display.

When a rescue ship arrives at the disabled ship's location, on the next update(), the disabled ship checks its health and calls its resumeCurrent() method. This method will either set its state to Moving if it can continue with its current or suspended task, or it will head to a port for with a repair at port task because of the transition to Idle. A ship that is rescued cannot continue with  its own rescue task, as it is assumed that the Ship's crew has been airlifted to safety. The method canResume() in task allows me to determine which tasks can be resumed without changing Ship behaviour.

DestroyedShip ---

A ship is destroyed when it's health reaches 0. This is handled in Ship.checkHealthState(). Here, the transition to Destroyed occurs and notifyShipSunk() is called. In Fleet Coordinator, a few things happen. If the ship was on a rescue mission, the ship-to-be-rescued is re-queued into the awaitingRescue list in Fleet Coordinator for another ship to be sent to go rescue it. After, a replacement ship is spawned in at a port closest to the destroyed ship via Ship Spawner. Lastly, the ship is removed from the fleet which effectively removes it from the simulation.

MovingShip ---

A Ship is transition into the Moving State whenever it needs to travel to any destination. A reason for travelling is based on the task it is currently on. A ship can travel to a port for loading or unloading if it is on a Supply Task or simply for repairs if it is on a Repair At Port Task. A ship can also travel to a disabled ship if it is on a Rescue Task.

There are multiple transition out cases for this State. If the ship arrives for loading / unloading it transitions to it respective States, if the ship becomes disabled in transit, then it transitions to a Disabled State, if the Ship completes it current task or the current task becomes invalid during transit and there is no valid suspended task, then the Ship transitions to Idle.

A ship can self-transition to Moving again if the current task is completed or becomes invalid and also it has a valid suspended task

LoadingShip ---

This is a Supply task specific state which requires the ship to stay at a Port, defined by the task, to load cargo. This loading state lasts for either 5 simulation ticks or if the Ship is assigned a Rescue Task. Either way, the Ship will transition back to Moving.

When a Moving Ship, that is on a Supply Task, arrives at the loading port location, the Moving Ship will transition to Loading. The internal state of the Supply Task will change to allow it to transition to Unloading at the next destination port.

UnloadingShip ---

This is the last phase for a Supply Task. When a Ship enters the Drop Off Port, it transitions to Unloading from Moving, as seen in the performArrivalLogic(Ship) method in Supply Task. The flag hasPickedUp() at this point returns true and thus enters the branch where the Ship's state transitions into UnloadShip()

In this state the ship waits 5 ticks to complete its task, which at this point the task will mark itself complete and call the ship's completeCurrentTask method. This method will check for any valid suspended tasks, if there is one, it will transition to Moving to complete the task, if there are none, the ship will transition to Idle and wait for further instructions in the Port.

The ship may be called upon for a Rescue Task while it is unloading, at this point the ship will transition to Moving, perform the rescue, and come back to restart the Unloading process.



=======
# Ship Simulator – Curtin OOSE (2026 S1)

This repository contains my submission for the **2026 Semester 1 Curtin University Object-Oriented Software Engineering (OOSE)** assignment.

The application is a real-time simulation of a lake where ships travel between ports, complete transport tasks, and respond dynamically to storms that can damage or destroy them.

## Features

- Real-time ship simulation
- Dynamic weather and storm events
- Port-to-port task management
- Object-oriented architecture using:
  - Command Pattern
  - Strategy Pattern
  - Observer Pattern
  - State Pattern
- Factory-based object creation
- Java Generics
- Built with Gradle

## Technologies

- Java
- Gradle

## Running the Project

Clone the repository and run:

```bash
./gradlew run
```

On Windows:

```bash
gradlew.bat run
```
>>>>>>> a5c3d2a35fe76e847e4586fccf0fec78752e1c98
