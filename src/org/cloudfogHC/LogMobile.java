package org.cloudfogHC;

import java.text.DecimalFormat;

import org.cloudbus.cloudsim.core.CloudSim;
import org.mobility.ImplMobility;

public class LogMobile {
	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 * @throws Exception 
	 */
	public static final int ERROR = 1;
	public static final int DEBUG = 0;
	
	public static int LOG_LEVEL = LogMobile.DEBUG;
	private static DecimalFormat df = new DecimalFormat("#.00");  

	public static boolean ENABLED = false;;
	
	public static void setLogLevel(int level){
		LogMobile.LOG_LEVEL = level;
	}
	
	public static void debug(String classJava, String message){
		if(!ENABLED)
			return;
		if(LogMobile.LOG_LEVEL <= LogMobile.DEBUG)
			System.out.println("Clock: "+ImplMobility.getClock()+" - "+classJava+": "+message);
	}
//	public static void error(String name, String message){
//		if(!ENABLED)
//			return;
//		if(LogMobile.LOG_LEVEL <= LogMobile.ERROR)
//			System.out.println(df.format(CloudSim.clock())+" : "+name+" : "+message);
//	}
	public static void setEnabled(boolean enable) {
		LogMobile.ENABLED = enable;
	}
}
