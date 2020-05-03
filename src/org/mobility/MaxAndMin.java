package org.mobility;
/**
 * @author Marcio Moraes Lopes
 */
public final class MaxAndMin {
	
	public static final int MAX_DISTANCE = 75; //Max gateway coverage distance in meters
	public static final int MAX_DISTANCE_TO_HANDOFF = 50; //It cannot be less than Max_SPEED 
	public static final int MAX_HANDOFF_TIME = 1200;
	public static final int MIN_HANDOFF_TIME = 700;
	//public static final int MAX_AP_DEVICE = 15;
	public static final int MAX_SM_IN_GATEWAY = 500;
	public static final int MAX_SMART_THING = 2;
	public static final int MAX_SERVER_CLOUDLET = 10;
	public static final int MAX_X = 700;// in meters = (14000 = 14 km )
	public static final int MAX_Y = 700;
	public static final int MAX_SPEED = 5; // in m/s  = 20/ 0.27 = 74 km/h
	public static final int MAX_DIRECTION = 9;
	public static final int MAX_SIMULATION_TIME = (int)30*60*1000;//*60*30;//2 minutes. Note the simulation time is in millisecond //old:5000;  
	public static final int MAX_VM_SIZE = 200; //200MB
	public static final int MIN_VM_SIZE = 100; //100MB
	public static final int MAX_BANDWIDTH = 15 * 1024 * 1024;
	public static final int MIN_BANDWIDTH = 5 * 1024 * 1024;
	// I don't use these
	public static final int DELAY_PROCESS = 500;
	public static final int MIG_POINT = (int) (MAX_DISTANCE_TO_HANDOFF*1.3);//Distance from boundary - it should modify
	public static final int LIVE_MIG_POINT = 200;//(int) (MAX_DISTANCE_TO_HANDOFF*20.0);//It can be based on the Network's Bandwidth 
	public static final int MAX_SERVICES = 3;
	public static final float MAX_VALUE_SERVICE = 1.1f;
	public static final float MAX_VALUE_AGREE = 70f;
	public static final double SIZE_CONTAINER = 0.6;
	public static final double PROCESS_CONTAINER = 1.3;
}
