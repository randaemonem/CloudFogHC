package org.cloudfogHC;

import java.text.DecimalFormat;

import org.cloudbus.cloudsim.core.CloudSim;

public class LogPlacement {
	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 * @throws Exception 
	 */
	public static final int ERROR = 1;
	public static final int DEBUG = 0;
	
	public static int LOG_LEVEL = LogPlacement.DEBUG;
	private static DecimalFormat df = new DecimalFormat("#.00");  

	public static boolean ENABLED = false;;
	
	public static void setLogLevel(int level){
		LogPlacement.LOG_LEVEL = level;
	}
	
	//public static void debug(String classJava, String message ){
	public static void debug( String message ){
		if(!ENABLED)
			return;
		if(LogPlacement.LOG_LEVEL <= LogPlacement.DEBUG)
			//System.out.println("Clock: "+CloudSim.clock()+" - "+classJava+": "+message);
			System.out.println(message);
	}
//	public static void error(String name, String message){
//		if(!ENABLED)
//			return;
//		if(LogMobile.LOG_LEVEL <= LogMobile.ERROR)
//			System.out.println(df.format(CloudSim.clock())+" : "+name+" : "+message);
//	}
	public static void setEnabled(boolean enable) {
		LogPlacement.ENABLED = enable;
	}

	public static void debug(double d) {
		if(!ENABLED)
			return;
		if(LogPlacement.LOG_LEVEL <= LogPlacement.DEBUG)
			//System.out.println("Clock: "+CloudSim.clock()+" - "+classJava+": "+message);
			System.out.println(d);
		
	}
}
