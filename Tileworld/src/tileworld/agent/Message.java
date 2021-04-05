package tileworld.agent;

import tileworld.environment.TWObject;

public class Message {
	public static final int MY_X_Y = 0;
	public static final int FOUND_FUEL = 1;
	public static final int MY_SENSE = 2;
	public static final int ERR_MESSAGE = -1;

	private String from; // the sender
	private String to; // the recepient
	private String message; // the message
	private int messageType;
	private TWObject object;
	private int x;
	private int y;
	
	public Message(String from, String to, String message){
		this.from = from;
		this.to = to;
		this.message = message;
		this.messageType = -1;
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

	public int getMessageType() { return this.messageType;}

	public void setMessage(String m) {this.message = m;}

	public void setObject(TWObject o) { this.object = o;}

	public void setX(int x) { this.x = x;}

	public void setY(int y) { this.y = y;}

	public void setMessageType(int mt) { this.messageType = mt;}

}
