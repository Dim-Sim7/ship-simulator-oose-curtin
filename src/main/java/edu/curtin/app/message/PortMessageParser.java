package edu.curtin.app.message;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.fleet.Fleet;
import edu.curtin.app.port.Port;

//“port ID x y” (e.g., port AA 0 5)
public class PortMessageParser implements ParseStrategy {
    private static final Logger logger = Logger.getLogger(PortMessageParser.class.getName());

    private final Fleet fleet;

    public PortMessageParser(Fleet fleet) {
        if (fleet == null) {
            throw new MessageException("PortMessageParser requires a non-null fleet");
        }
        this.fleet = fleet;
    }   

    @Override
    public boolean canParse(String[] message) {
        return message != null 
            && message.length == 4 
            && "port".equals(message[0]);
    }

    @Override
    public void parse(String[] message) throws MessageException {
        String id = message[1].toUpperCase();
        int x, y;
        try {
            x = Integer.parseInt(message[2]);
            y = Integer.parseInt(message[3]);
        } catch (NumberFormatException e) {
            throw new MessageException(String.join(" ", message) + " failed to parse coordinates", e);
        }
        // adds the new port to the ports list in fleet
        fleet.addPort(new Port(id, x, y));
        logger.log(Level.INFO, () -> "Port added with id " + id + " at coordinates x = " + x + ", y = " + y);
    }
}
