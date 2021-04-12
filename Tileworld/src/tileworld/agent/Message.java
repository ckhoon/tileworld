package tileworld.agent;

import tileworld.environment.TWObject;

public class Message {
	public enum MESSAGE_TYPE {
		MY_X_Y, FOUND_FUEL, UPDATE_MY_X_Y, MY_SENSE, ERR_MESSAGE;
	}

	private String from; // the sender
	private String to; // the recepient
	private String message; // the message
	private MESSAGE_TYPE messageType;
	private TWObject object;
	private int x;
	private int y;
	private int fuelStationX;
	private int fuelStationY;
	private long timestamp;

	public Message(String from, String to, String message){
		this.from = from;
		this.to = to;
		this.message = message;
		this.messageType = MESSAGE_TYPE.ERR_MESSAGE;
	}

	public String getFrom() {
		return from;
	}

	public String getTo() {
		return to;
	}

	public String getMessage() {
		return message;
	}

	public TWObject getObject() { return this.object;}

	public int getX() { return this.x;}

	public int getY() { return this.y;}

	public int getFuelStationX() { return this.fuelStationX;}

	public int getFuelStationY() { return this.fuelStationY;}

	public long getTimestamp(){return this.timestamp;}

	public MESSAGE_TYPE getMessageType() { return this.messageType;}

	public void setMessage(String m) {this.message = m;}

	public void setObject(TWObject o) { this.object = o;}

	public void setX(int x) { this.x = x;}

	public void setY(int y) { this.y = y;}

	public void setFuelStationX(int x) { this.fuelStationX = x;}

	public void setFuelStationY(int y) { this.fuelStationY = y;}

	public void setTimestamp(){this.timestamp = System.nanoTime();}

	public void setMessageType(MESSAGE_TYPE mt) { this.messageType = mt;}

}
