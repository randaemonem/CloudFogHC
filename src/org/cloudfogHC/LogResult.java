package org.cloudfogHC;

import java.text.DecimalFormat;

import org.cloudbus.cloudsim.core.CloudSim;

public class LogResult {
	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 * @throws Exception 
	 */
	public static final int ERROR = 1;
	public static final int DEBUG = 0;
	
	public static int LOG_LEVEL = LogResult.DEBUG;
	private static DecimalFormat df = new DecimalFormat("#.00");  

	public static boolean ENABLED = false;;
	
	public static void setLogLevel(int level){
		LogResult.LOG_LEVEL = level;
	}
	
	//public static void debug(String classJava, String message ){
	public static void debug( String message ){
		if(!ENABLED)
			return;
		if(LogResult.LOG_LEVEL <= LogResult.DEBUG)
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
		LogResult.ENABLED = enable;
	}

	public static void debug(double d) {
		if(!ENABLED)
			return;
		if(LogResult.LOG_LEVEL <= LogResult.DEBUG)
			//System.out.println("Clock: "+CloudSim.clock()+" - "+classJava+": "+message);
			System.out.println(d);
		
	}
}
