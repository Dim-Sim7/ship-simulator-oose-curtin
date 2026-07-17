package edu.curtin.app.message;


import edu.curtin.app.fleet.FleetCoordinator;
import edu.curtin.app.ship.ShipSpawner;


//“ship N x y” (e.g., ship 7 16 4)
public class ShipMessageParser implements ParseStrategy {

    private final ShipSpawner shipSpawner;
    private final FleetCoordinator coordinator;

    public ShipMessageParser(ShipSpawner shipSpawner, FleetCoordinator coordinator) {
        if (shipSpawner == null || coordinator == null) {
            throw new MessageException("ShipMessageParser requires non-null dependencies");
        }
        this.shipSpawner = shipSpawner;
        this.coordinator = coordinator;
    }

    @Override
    public boolean canParse(String[] message) {
        return message != null 
            && message.length == 4 
            && "ship".equals(message[0]);
    } 

    @Override
    public void parse(String[] message) throws MessageException {
        int id, x, y;
        try {
            id = Integer.parseInt(message[1]);
            x = Integer.parseInt(message[2]);
            y = Integer.parseInt(message[3]);
        } catch (NumberFormatException e) {
            throw new MessageException(String.join(" ", message) + " failed to parse fields as integers", e);
        }
        shipSpawner.spawn(id, x, y, coordinator);
    }
}
