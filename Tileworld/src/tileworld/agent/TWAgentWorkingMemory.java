package tileworld.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.text.html.HTMLDocument;
import sim.engine.Schedule;
import sim.field.grid.ObjectGrid2D;
import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.environment.*;
import tileworld.Parameters;

/**
 * TWAgentMemory
 * 
 * @author michaellees
 * 
 *         Created: Apr 15, 2010 Copyright michaellees 2010
 * 
 *         Description:
 * 
 *         This class represents the memory of the TileWorld agents. It stores
 *         all objects which is has observed for a given period of time. You may
 *         want to develop an entirely new memory system by extending this one.
 * 
 *         The memory is supposed to have a probabilistic decay, whereby an element is
 *         removed from memory with a probability proportional to the length of
 *         time the element has been in memory. The maximum length of time which
 *         the agent can remember is specified as MAX_TIME. Any memories beyond
 *         this are automatically removed.
 */
public class TWAgentWorkingMemory {

	/**
	 * Access to Scedule (TWEnvironment) so that we can retrieve the current timestep of the simulation.
	 */
	private Schedule schedule;
	private TWAgent me;
	private final static int MAX_TIME = 50;
	private final static float MEM_DECAY = 0.5f;

	private ObjectGrid2D memoryGrid;

	/*
	 * This was originally a queue ordered by the time at which the fact was observed.
	 * However, when updating the memory a queue is very slow.
	 * Here we trade off memory (in that we maintain a complete image of the map)
	 * for speed of update. Updating the memory is a lot more straightforward.
	 */
	private TWAgentPercept[][] objects;
	/**
	 * Number of items recorded in memory, currently doesn't decrease as memory
	 * is not degraded - nothing is ever removed!
	 */
	private int memorySize;

	/**
	 * Stores (for each TWObject type) the closest object within sensor range,
	 * null if no objects are in sensor range
	 */
	private HashMap<Class<?>, TWEntity> closestInSensorRange;
	static private List<Int2D> spiral = new NeighbourSpiral(Parameters.defaultSensorRange * 4).spiral();
	//    private List<TWAgent> neighbouringAgents = new ArrayList<TWAgent>();

	// x, y: the dimension of the grid
	public TWAgentWorkingMemory(TWAgent moi, Schedule schedule, int x, int y) {

		closestInSensorRange = new HashMap<Class<?>, TWEntity>(4);
		this.me = moi;

		this.objects = new TWAgentPercept[x][y];

		this.schedule = schedule;
		this.memoryGrid = new ObjectGrid2D(me.getEnvironment().getxDimension(), me.getEnvironment().getyDimension());
//		for (Int2D offset : spiral) {
//			System.out.println("x: " + offset.x + " y: " + offset.y);
//		}
	}

	/**
	 * Called at each time step, updates the memory map of the agent.
	 * Note that some objects may disappear or be moved, in which case part of
	 * sensed may contain null objects
	 *
	 * Also note that currently the agent has no sense of moving objects, so
	 * an agent may remember the same object at two locations simultaneously.
	 * 
	 * Other agents in the grid are sensed and passed to this function. But it
	 * is currently not used for anything. Do remember that an agent sense itself
	 * too.
	 *
	 * @param sensedObjects bag containing the sensed objects
	 * @param objectXCoords bag containing x coordinates of objects
	 * @param objectYCoords bag containing y coordinates of object
	 * @param sensedAgents bag containing the sensed agents
	 * @param agentXCoords bag containing x coordinates of agents
	 * @param agentYCoords bag containing y coordinates of agents
	 */
	public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
		//reset the closest objects for new iteration of the loop (this is short
		//term observation memory if you like) It only lasts one timestep
		closestInSensorRange = new HashMap<Class<?>, TWEntity>(4);

		//must all be same size.
		assert (sensedObjects.size() == objectXCoords.size() && sensedObjects.size() == objectYCoords.size());

		//        me.getEnvironment().getMemoryGrid().clear();  // THis is equivalent to only having sensed area in memory
		//       this.decayMemory();       // You might want to think about when to call the decay function as well.
		for (int i = 0; i < sensedObjects.size(); i++) {
			TWEntity o = (TWEntity) sensedObjects.get(i);

			if (o instanceof TWFuelStation){
				this.me.setFuelStation(o.getX(), o.getY());
				continue;
			}

			if (!(o instanceof TWObject)) {
				continue;
			}
			
			//if nothing in memory currently, then were increasing the number 
			//of items we have in memory by 1
			//if(objects[objectXCoords.get(i)][objectYCoords.get(i)] == null) memorySize++;
			if(objects[o.getX()][o.getY()] == null) memorySize++;
			
			//Add the object to memory
			objects[o.getX()][o.getY()] = new TWAgentPercept(o, this.getSimulationTime());

			memoryGrid.set(o.getX(), o.getY(), o);

			updateClosest(o);

		}

		// clear old memory of the region. ex: previously sensed a tile at location (x,y), but the newest sensing do not sense it, need to remove it from memory
		for (int x = Math.max(me.getX() - Parameters.defaultSensorRange, 0); x <= Math.min(me.getX() + Parameters.defaultSensorRange, objects[0].length - 1); x++) {
			for (int y = Math.max(me.getY() - Parameters.defaultSensorRange, 0); y <= Math.min(me.getY() + Parameters.defaultSensorRange, objects[0].length - 1); y++) {
				if (objects[x][y] == null) continue;
				boolean disappeared = true;
				for (int i = 0; i < sensedObjects.size(); i++) {
					if (objectXCoords.get(i) == x && objectYCoords.get(i) == y) {
						disappeared = false;
						break;
					}
				}
				if (disappeared) {
					clearEntityFromMemory(x, y);
				}
			}
		}
		decayMemory();

		//       Agents are currently not added to working memory. Depending on how 
		//       communication is modelled you might want to do this.
		//        neighbouringAgents.clear();
		//        for (int i = 0; i < sensedAgents.size(); i++) {
		//            
		//            
		//            if (!(sensedAgents.get(i) instanceof TWAgent)) {
		//                assert false;
		//            }
		//            TWAgent a = (TWAgent) sensedAgents.get(i);
		//            if(a.equals(me)){
		//                continue;
		//            }
		//            neighbouringAgents.add(a);
		//        }
	}

	//    public TWAgent getNeighbour(){
	//        if(neighbouringAgents.isEmpty()){
	//            return null;
	//        }else{
	//            return neighbouringAgents.get(0);
	//        }
	//    }

	/**
	 * updates memory using 2d array of sensor range - currently not used
	 * see TWAgentWorkingMemory#updateMemory(sim.util.Bag, sim.util.IntBag, sim.util.IntBag)
	 */
	public void updateMemory(TWEntity[][] sensed, int xOffset, int yOffset) {
		for (int x = 0; x < sensed.length; x++) {
			for (int y = 0; y < sensed[x].length; y++) {
				objects[x + xOffset][y + yOffset] = new TWAgentPercept(sensed[x][y], this.getSimulationTime());
			}
		}
	}

	/**
	 * removes all facts earlier than now - max memory time. 
	 * TODO: Other facts are
	 * remove probabilistically (exponential decay of memory)
	 */
	public void decayMemory() {
//		 put some decay on other memory pieces (this will require complete
//		 iteration over memory though, so expensive.
//		This is a simple example of how to do this.
		for (int x = 0; x < this.objects.length; x++) {
			for (int y = 0; y < this.objects[x].length; y++) {
			TWAgentPercept currentMemory =  objects[x][y];
			if(currentMemory!=null && currentMemory.getT() < schedule.getTime()-MAX_TIME){
				memoryGrid.set(x, y, null);
				objects[x][y] = null;
				memorySize--;
			   }
		   }
	   }
	}


	public void removeAgentPercept(int x, int y){
		objects[x][y] = null;
	}


	public void removeObject(TWEntity o){
		removeAgentPercept(o.getX(), o.getY());
	}


	/**
	 * @return
	 */
	private double getSimulationTime() {
		return schedule.getTime();
	}

	/**
	 * Finds a nearby tile we have seen less than threshold timesteps ago
	 *
	 * @see TWAgentWorkingMemory#getNearbyObject(int, int, double, java.lang.Class)
	 */
	public TWTile getNearbyTile(int x, int y, double threshold) {
		return (TWTile) this.getNearbyObject(x, y, threshold, TWTile.class);
	}

	/**
	 * Finds a nearby hole we have seen less than threshold timesteps ago
	 *
	 * @see TWAgentWorkingMemory#getNearbyObject(int, int, double, java.lang.Class)
	 */
	public TWHole getNearbyHole(int x, int y, double threshold) {
		return (TWHole) this.getNearbyObject(x, y, threshold, TWHole.class);
	}


	/**
	 * Returns the number of items currently in memory
	 */
	public int getMemorySize() {
		return memorySize;
	}



	/**
	 * Returns the nearest object that has been remembered recently where recently
	 * is defined by a number of timesteps (threshold)
	 *
	 * If no Object is in memory which has been observed in the last threshold
	 * timesteps it returns the most recently observed object. If there are no objects in
	 * memory the method returns null. Note that specifying a threshold of one
	 * will always return the most recently observed object. Specifying a threshold
	 * of MAX_VALUE will always return the nearest remembered object.
	 *
	 * Also note that it is likely that nearby objects are also the most recently observed
	 *
	 *
	 * param x coordinate from which to check for objects
	 * param y coordinate from which to check for objects
	 * param threshold how recently we want to have seen the object
	 * param type the class of object we're looking for (Must inherit from TWObject, specifically tile or hole)
	 * return
	 */
	private TWObject getNearbyObject(int sx, int sy, double threshold, Class<?> type) {

		//If we cannot find an object which we have seen recently, then we want
		//the one with maxTimestamp
		double maxTimestamp = 0;
		TWObject o = null;
		double time = 0;
		TWObject ret = null;
		int x, y;
		for (Int2D offset : spiral) {
			x = offset.x + sx;
			y = offset.y + sy;

			if (me.getEnvironment().isInBounds(x, y) && objects[x][y] != null) {
				o = (TWObject) objects[x][y].getO();//get mem object
				if (type.isInstance(o)) {//if it's not the type we're looking for do nothing

					time = objects[x][y].getT();//get time of memory

					if (this.getSimulationTime() - time <= threshold) {
						//if we found one satisfying time, then return
						return o;
					} else if (time > maxTimestamp) {
						//otherwise record the timestamp and the item in case
						//it's the most recent one we see
						ret = o;
						maxTimestamp = time;
					}
				}
			}
		}

		//this will either be null or the object of Class type which we have
		//seen most recently but longer ago than now-threshold.
		return ret;
	}

	/**
	 * Used for invalidating the plan, returns the object of a particular type
	 * (Tile or Hole) which is closest to the agent and within it's sensor range
	 *
	 * @param type
	 * @return
	 */
	public TWEntity getClosestObjectInSensorRange(Class<?> type) {
		return closestInSensorRange.get(type);
	}

	private void updateClosest(TWEntity o) {
		assert (o != null);
		if (closestInSensorRange.get(o.getClass()) == null || me.closerTo(o, closestInSensorRange.get(o.getClass()))) {
			closestInSensorRange.put(o.getClass(), o);
		}
	}

	/**
	 * Is the cell blocked according to our memory?
	 * 
	 * @param tx x position of cell
	 * @param ty y position of cell
	 * @return true if the cell is blocked in our memory
	 */
	public boolean isCellBlocked(int tx, int ty) {
		// because we are checking the next step, so need to confirm not out of boundary
		if (tx < 0 || tx > objects[0].length - 1 || ty < 0 || ty > objects.length - 1) return false;
		//no memory at all, so assume not blocked
		if (objects[tx][ty] == null) {
			return false;
		}

		TWEntity e = (TWEntity) objects[tx][ty].getO();
		//is it an obstacle?
		return (e instanceof TWObstacle);
	}

	public ObjectGrid2D getMemoryGrid() {
		return this.memoryGrid;
	}

	// after agent picks up a tile or puts down a tile, should clear the tile/ hole in memory
	public void clearEntityFromMemory(int x, int y) {
		objects[x][y] = null;
		memoryGrid.set(x, y, null);
		memorySize--;
	}
}
