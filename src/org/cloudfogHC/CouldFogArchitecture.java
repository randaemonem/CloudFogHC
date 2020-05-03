package org.cloudfogHC;

import java.util.ArrayList;

import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Integer;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementBAR;
import org.fog.placement.ModulePlacementCloudOnly;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementHEFT;
import org.fog.placement.ModulePlacementHealthEdge;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModulePlacementOnlyCloud;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.mobility.*;
//import org.fog.vmmobile.LogMobile;
//import org.fog.vmmobile.constants.MaxAndMin;


//import com.sun.javafx.collections.MappingChange.Map;
import org.cloudfogHC.SensorsInfo;


/**
 * Simulation setup for Cloud-Fog Architecture for IoT dedicated to healthcare
 */
public class CouldFogArchitecture {
	//-------------------------------------------------------------------------
	// Initialization of all application devices: fogDevices(fog, cloud, sin), sensors and actuators
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static List<FogDevice> sinksMobile = new ArrayList<FogDevice>();

	//--------------------------------------------------------------------------------
	/* Setting Old Environment/Topology
	// Fognode = 15: 1 proxy + 7 gateways (each gateway is connected to 1 sink mobile and 1 sink micro) 
	static double numOfDepts = 7; // = number of gateways (1 gateway in each department)
	static int numOfSinkMobiles = 1; //number of mobile sink nodes (patients ) in department
	static int numOfMicrocontrollersPerDept = 1; //number of micro-controller sink nodes (WSNs) in department*/
 
	//--------------------------------------------------------------------------
	// Setting Environment/Topolgy (Dynamic)
	static int numberOfCloudNodes = 60; //static int numberOfMachines = 25;
	static int totalNumOfTasks = 50;
	static int numOfSensorsTasksPerSink = 5;
	static int numOfSensorsTasksPerSinkMobile= 5; 
	static int numOfSinks = (int) Math.ceil(totalNumOfTasks / numOfSensorsTasksPerSink);
	static int seed = 30;
	static int stepPolicy = 1;
	static int numSinksMob = 0;
	static String []directions = {"NONE" , "East", "NorthEast" , "North", "NorthWest" , "West" , "SouthWest" , "South" , "SouthEast"};
	static HashMap< FogDevice,Coordinate > sinksInitCoordinates = new HashMap< FogDevice,Coordinate> ();
	static HashMap< FogDevice,FogDevice > sinksInitGateways = new HashMap< FogDevice,FogDevice> ();

	// Sensor's information : initialized in start of main method
	static HashMap<Integer, SensorsInfo> sensorsMap1= new HashMap <Integer, SensorsInfo>();
	static HashMap<Integer, SensorsInfo> sensorsMap2= new HashMap <Integer, SensorsInfo>();
	static int numOfSensorsTypes_BSN = 4;
	static int numOfSensorsTypes_WSN = 1;
	
	//--------------------------------------------------------------------------------
	// Application arguments 
	static HashMap<Integer, AppModule > modulesMap = new HashMap<Integer, AppModule>();
	static int allocationTechnique = 0; 				// 0 for MBAR, 1 for HEFT, 2 for HealthEdge, 3 for CloudOnly
	static int reallocationStrategy = 3;				// 0 for random, 1 for classification, 2 for max response
	static int allocationSamplingFreq = 10;
	static double fractionSinkMobile = 0; 
	static Application application ;
	static double varyingFactor;
	
	//--------------------------------------------------------------------------
	// Supplementary Variables  	
	static double []randomBW = new double[4];
	static int []fogMIPS = new int[3];
	static int []cloudMIPS = new int[3];
	static int []taskMIPS = new int[5];
	
	//--------------------------------------------------------------------------------
	public static void main(String[] args) {
		 
		varyingFactor = totalNumOfTasks ;//MaxAndMin.MAX_DISTANCE_TO_HANDOFF;
		//----------------------------------------------------------------------------
		// Sensors Information Initialization: updated only whenever new sensors are added to the environment	
		//BSN:
		SensorsInfo s_BSN1= new SensorsInfo(0,"EEG",5);		
		SensorsInfo s_BSN2= new SensorsInfo(1,"ECG_HeartBeat_Pair",5);		
		SensorsInfo s_BSN3= new SensorsInfo(2,"Audio",5); 
		SensorsInfo s_BSN4= new SensorsInfo(3,"Oxygen",5);	
		sensorsMap1.put(s_BSN1.getID(), s_BSN1);
		sensorsMap1.put(s_BSN2.getID(), s_BSN2);
		sensorsMap1.put(s_BSN3.getID(), s_BSN3);
		sensorsMap1.put(s_BSN4.getID(), s_BSN4);
		//WSN
		SensorsInfo s_WSN1= new SensorsInfo(0,"Camera",5);		
		sensorsMap2.put(s_WSN1.getID(), s_WSN1);
		 
		randomBW[0]=10;randomBW[1]=100;randomBW[2]=512;
		//fogMIPS[0]=16;fogMIPS[1]=32;fogMIPS[2]=64;//fog : 100MIPS-500MIPS -> 128(2^7 MIPS/MHz),256(2^8 MIPS/MHz),512(3^9 MIPS/MHz)
		//cloudMIPS[0]=2048;cloudMIPS[1]=4096;cloudMIPS[2]=8192;
		//taskMIPS[0]=1000;taskMIPS[1]=5000;taskMIPS[2]=10000;taskMIPS[3]=15000;taskMIPS[4]=20000;
		taskMIPS[0]=500;taskMIPS[1]=600;taskMIPS[2]=700;taskMIPS[3]=800;taskMIPS[4]=900;
		
		//------------------------------------------------------------------------------------------------
		// Modules Information Initialization: updated only whenever new types of modules are added to the environment
		// (For simulation purposes, can be generated dynamicly randomly in the future)
		//	Module type									 Sensors needed			classification		maxResponse ((hours)*minutes*seconds*1000)
		//__________________________________________     ________________		______________		___________
		// 1)critical analysis: ECG_Monitoring	-   	 ECG, HearBeat				3
		// 2)critical analysis: Epileptic_Detection -     EEG						3
		// 3)context management: Activity_Monitoring -	 Camera						2
		// 4)data analysis: Parkinson_Speech_Analysis -    Audio					1
	    // 5)critical control: Oxygen_Level_Control -    Oxygen						3
		//-------------------------------------------------------------------------------------------------
		Log.printLine("Starting Cloud-Fog Architecture for IoT Healthcare...");
		
		
		
		try {
			//------------------------------------------------------
			// Initialization of Simulator
			//Log.disable();
			Log.enable();
			LogMobile.setEnabled(false);
			LogPlacement.setEnabled(false);
			LogResult.setEnabled(false);
			int num_user = 1; // number of cloud users(applications)
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			//-------------------------------------------------------
			//-------------------------------------------------------
			//Initialization of Application
			String appId = "IoT_HC"; // identifier of the application		
			FogBroker broker = new FogBroker("broker");			
			
			//---------------------------------------------------------
			// Devices creation
			createFogDevices(broker.getId(), appId);
			
			//---------------------------------------------------------
			//Continue of Initialization of Application
			//Application application = createApplication(appId, broker.getId());
			application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			//---------------------------------------------------------
			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators,sinksMobile);
			
			//---------------------------------------------------------
			// Mapping/ Placement: I am not using any static mapping
			while(allocationTechnique < 2) {
				ModulePlacement myPlacement = null;
				
				switch(allocationTechnique) {
				case Policies.Allocation_CloudOnly:
					myPlacement = new ModulePlacementCloudOnly(fogDevices, sensors, actuators, application , reallocationStrategy , allocationSamplingFreq , varyingFactor);
					break;
				case Policies.Allocation_MBAR:
					myPlacement = new ModulePlacementBAR(fogDevices, sensors, actuators, application , reallocationStrategy , allocationSamplingFreq , varyingFactor);
					break;
				case Policies.Allocation_HEFT:
					 myPlacement = new ModulePlacementHEFT(fogDevices, sensors, actuators, application , reallocationStrategy , allocationSamplingFreq , varyingFactor);
					break;
				case Policies.Allocation_HealthEdge:
					 myPlacement = new ModulePlacementHealthEdge(fogDevices, sensors, actuators, application , reallocationStrategy , allocationSamplingFreq , varyingFactor);
					break;
				}
						
				controller.submitApplication(application, myPlacement);
				ImplMobility mobility = new ImplMobility(fogDevices, sinksMobile, stepPolicy, actuators, sensors,  controller, application , allocationTechnique, reallocationStrategy , allocationSamplingFreq, myPlacement, varyingFactor); // 3rd argument: step policy = 1. 4th to 11th arguments are for placement			
				mobility.Move();
				resetInitialLoadIssue();
				allocationTechnique++;
			}
			
			//---------------------------------------------------------		
			//EvaluatePrintedResults.Evaluate();					
			//TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			//CloudSim.startSimulation();
			CloudSim.stopSimulation();

			Log.printLine("IoT_HC finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		int cloudRAM = 40000000;
		FogDevice cloud = null;
		for( int i = 0 ; i < numberOfCloudNodes ; i++) {
			Random r1 = new Random();
			//random BW, random MIPS
			int randomValueBW = r1.nextInt(2 - 0 + 1) + 0;
			//double randomCloudBW = randomBW[randomValueBW];
			int randomValueMIPS = r1.nextInt(1 - 0 + 1) + 0;
			//int randomCloudMPIS = cloudMIPS[randomValueMIPS];//getValueI(20000,100000);
			
			//Fixed BW,MIPs as in Healthedge (2.2Ghz ~= 2.2*1024 MIPS ~= 2252 MIPS) & BW = 100	- homogenous
			int randomCloudBW = 100;
			int randomCloudMPIS = 2252;
			cloud = createCloudDatacenter("cloud"+i, randomCloudMPIS, cloudRAM, randomCloudBW, 10000, 0, 0.01, 16*103, 16*83.25);
			cloud = createCloudDatacenter("cloud"+i, 2252, cloudRAM, 100, 10000, 0, 0.01, 16*103, 16*83.25);
			System.out.println("cloud"+i + ": MIPS=" + randomCloudMPIS + ", RAM ="+ cloudRAM + ". ");
			cloud.setParentId(-1);
			fogDevices.add(cloud);
		}
		
		Random r1 = new Random();
		int randomValueMIPS = r1.nextInt(1 - 0 + 1) + 0;
		
		//Fixed BW,MIPs as in Healthedge cloud = 200 times fog (2.2Ghz/200 ~= 11 MIPS)	- homogenous
		int randomFogMIPS = 11;
		int randomfogRAM = getValueI(512,1000);

		FogDevice proxy = createFogDevice("proxy-server", randomFogMIPS, randomfogRAM, 10000, 10000, 1, 0.0, 107.339, 83.4333,0,0,0,0); // creates the fog device Proxy Server (level=1)
		System.out.println("proxy-server: MIPS=" + randomFogMIPS + ", RAM ="+ randomfogRAM + ". ");
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms	
		printFogsFile("proxy-server" + ": X=0" + ", Y=0" + ", MIPS=" + randomFogMIPS +", RAM = " + randomfogRAM);
		
		fogDevices.add(proxy);
	
		//---------------------------------------------------------
		// Creation of gateways and their nested sink devices - static/ enviromental 
		/*evenly distributed*/
		System.out.println("____________________________________");
		System.out.println("Creating gateways:");
		int coordY=0;
		boolean control = true;
		int i = 0;
		for(int coordX=1; coordX<MaxAndMin.MAX_X-1; coordX+=100){ 
		/*	if(control){
				coordY=4000;
			}
			else{
				coordY = 10500;
			}
			control=!control;*/
			
			for(coordY=1; coordY<MaxAndMin.MAX_Y-1; coordY+=100, i++){
				addGw( i, userId, appId, proxy.getId(),coordX,coordY); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
			}
		}
		/*for(int i=0;i<numOfDepts;i++){
			addGw( i+"", userId, appId, proxy.getId(),coordX,coordY); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}*/
		System.out.println(i + " gateways created");
		System.out.println("___________________________________");
		
		//-------------------------------------------------------------
			
		System.out.println("Creating " +  numOfSinks + " sinks:");
		
		//1- Creating Sinks Static(WSN-Microcontrolers)
		System.out.println("Creating " + Math.ceil(numOfSinks*(1-fractionSinkMobile)) + " sinks static (WSN) ");
		printSinksFile(Math.ceil(numOfSinks*(1-fractionSinkMobile)) + " static sinks");
		int remainingTasks = totalNumOfTasks;// (int) (Math.ceil(numOfSinks*(1-fractionSinkMobile))*numOfSensorsTasksPerSink);
		for( int j =0; j < Math.ceil(numOfSinks*(1-fractionSinkMobile)); j++){
			if(remainingTasks<numOfSensorsTasksPerSink) numOfSensorsTasksPerSink=remainingTasks;		
			FogDevice sinkstatic = addSinkStatic(j, userId, appId);
			remainingTasks-= numOfSensorsTasksPerSink;
			sinkstatic.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
			fogDevices.add(sinkstatic);
		}
		
		
		//2- Creating Sinks Mobile (BSN-Smart Devices)
		System.out.println("Creating " + Math.floor(numOfSinks*(fractionSinkMobile)) + " sinks mobile (BSN) ");
		printSinksFile(Math.floor(numOfSinks*(fractionSinkMobile)) + " mobile sinks");
		//int remainingMobileTasks = (totalNumOfTasks - (int)(Math.ceil(numOfSinks*(1-fractionSinkMobile))*5));
		//if(remainingMobileTasks <numOfSensorsTasksPerSinkMobile) numOfSensorsTasksPerSinkMobile =remainingMobileTasks;
		for( int j =0; j <Math.floor(numOfSinks*(fractionSinkMobile)); j++){			
			if(remainingTasks <numOfSensorsTasksPerSinkMobile) numOfSensorsTasksPerSinkMobile =remainingTasks;	
			FogDevice mobile = addSinkMobile(j, userId, appId);
			remainingTasks-=numOfSensorsTasksPerSinkMobile;
			mobile.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
			fogDevices.add(mobile);
			sinksMobile.add(mobile);
			}
		}
		private static FogDevice addGw(int id, int userId, String appId, int parentId,int coordX, int coordY){
		
		String gatewayName = "gateway-"+id;
		Random r1 = new Random();
		int randomValueMIPS = r1.nextInt(1 - 0 + 1) + 0;
	
		//Fixed BW,MIPs as in Healthedge cloud = 200 times fog (2.2Ghz/200 ~= 11 MIPS)
		int randomFogMIPS = 11;
		
		int randomfogRAM = getValueI(512,1000);
		int upBW = 	getValueI(200, 300);
		int dWBW = getValueI(500, 1000);
		int busyPwr = getValueI(100,120);
		int IdlePwr = getValueI(70, 75);
		FogDevice dept = createFogDevice(gatewayName, randomFogMIPS ,randomfogRAM, 200, 500, 2, 0.0, 107.339, 83.4333, coordX,coordY,0,0);
		dept.setMyId(id);
		dept.setMaxSinks(MaxAndMin.MAX_SM_IN_GATEWAY);//maxSmartThing
		System.out.println(gatewayName+ ": X=" + coordX + ", Y=" + coordY + ", MIPS=" + randomFogMIPS + ", RAM ="+ randomfogRAM + ". ");
		printFogsFile( gatewayName+ ": X=" + coordX + ", Y=" + coordY  + ", MIPS=" + randomFogMIPS +", RAM = " + randomfogRAM);
		fogDevices.add(0,dept);
		dept.setParentId(parentId);
		dept.setUplinkLatency(4); // latency of connection between gateways and proxy server is 4 ms
	
		return dept;
	}
		private static FogDevice addSinkStatic(int id, int userId, String appId){
			String microName = "sink-static-"+id;
			int randomSinkMIPS = getValueI(10,100);
			int randomSinkRAM = getValueI(512,1000);
			int upBW = 	getValueI(200, 300);
			int dWBW = getValueI(3, 200);
			int busyPwr = getValueI(100,120);
			int IdlePwr = getValueI(70, 75);
			
			int coordX,coordY;
			while(true) {
				coordX =  getValueI(1, (int)(MaxAndMin.MAX_X*0.8));
				coordY = getValueI(1,(int)(MaxAndMin.MAX_Y*0.8));
				if((coordX >= MaxAndMin.MAX_X*0.2) ||(coordY >= MaxAndMin.MAX_Y*0.2)){
					break;
				}
			}
			FogDevice micro = createFogDevice(microName, randomSinkMIPS, randomSinkRAM, 200, 3, 3, 0, 87.53, 82.44, coordX, coordY, 0, 0);
			System.out.println("\t"+microName + ": X=" + coordX + ", Y=" + coordY + ", MIPS= " + randomSinkMIPS + ", RAM = " + randomSinkRAM );
			printSinksFile("\t"+microName + ": X=" + coordX + ", Y=" + coordY + ", MIPS= " + randomSinkMIPS + ", RAM = " + randomSinkRAM );
			//--------------------------------------------------------------
			while(!connectGatewaySink(getGWDevices(),micro, 0.5)){
				while(true) {
					coordX =  getValueI(1, (int)(MaxAndMin.MAX_X*0.8));
					coordY = getValueI(1,(int)(MaxAndMin.MAX_Y*0.8));
					if((coordX >= MaxAndMin.MAX_X*0.2) ||(coordY >= MaxAndMin.MAX_Y*0.2)){
						break;
					}
				}
				micro.setCoord(coordX, coordY);
			}
		/*	int myCount=0;
		    if(!connectGatewaySink(getGWDevices(),micro, 0.5)){//getRand().nextDouble())){
				myCount++;
				System.out.println( micro.getName()+" isn't connected");
			}
			System.out.println("\t \t total no connection in sink mobile : "+myCount);*/
			//------------------------------------------------------------------
			
			int randomNumOfSensorsForMicro= getValueI( 1, 5);
			int randomSensorsID= (int) getValueI( 0 , numOfSensorsTypes_WSN-1);
			System.out.println("\t \t Creating " + numOfSensorsTasksPerSink + " WSN sensors in " +microName +":");
			for(int i= 0 ; i < numOfSensorsTasksPerSink ; i ++) {
				SensorsInfo s = sensorsMap2.get(randomSensorsID);
				String sensorName = "sinkst-" + id + "-s-" +i;
				System.out.println("\t \t \t"+ sensorName + ":Type = " + s.getSensorType() + ".");
				double samplingRate = getValueI(20, 5000);
				double transmissionTime = 1 / samplingRate;
				Sensor sensor =new Sensor(sensorName, "", userId, appId, new DeterministicDistribution(transmissionTime));
				sensors.add(sensor);
				sensor.setGatewayDeviceId(micro.getId());
				sensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
				micro.setNumTasksAttached(numOfSensorsTasksPerSink);
			}
			
			return micro;
		}
	private static FogDevice addSinkMobile(int id, int userId, String appId){
		String mobileName = "sink-mobile-"+id;
		int randomSinkMIPS = getValueI(10,100);//1000
		int randomSinkRAM = getValueI(512,1000);
		int upBW = 	getValueI(200, 300);
		int dWBW = getValueI(3, 200);
		int busyPwr = getValueI(100,120);
		int IdlePwr = getValueI(70, 75);
	
		int direction = getValueI(1, MaxAndMin.MAX_DIRECTION-1);// getRand().nextInt(MaxAndMin.MAX_DIRECTION-1)+1;
		double speed = 1.4; //getValueI(1,MaxAndMin.MAX_SPEED-1); 
		int coordX,coordY;
		while(true) {
			coordX =  getValueI(1, (int)(MaxAndMin.MAX_X*0.8));
			coordY = getValueI(1,(int)(MaxAndMin.MAX_Y*0.8));
			if((coordX >= MaxAndMin.MAX_X*0.2) ||(coordY >= MaxAndMin.MAX_Y*0.2)){
				break;
			}
		}
		Coordinate initialCoord = new Coordinate();
		initialCoord.setCoordX(coordX);
		initialCoord.setCoordY(coordY);
		//static initialy for testing
		//coordX = 100 ; coordY = 7200;
		//FogDevice mobile = createFogDevice(mobileName, randomSinkMIPS, randomfogRAM, 10000, 200, 3, 0, 87.53, 82.44,100,7200,7,20);
		//FogDevice mobile = createFogDevice(mobileName, randomSinkMIPS, randomfogRAM, 10000, 200, 3, 0, 87.53, 82.44,100,8920,7,20);
		//dynamic
		FogDevice mobile = createFogDevice(mobileName, randomSinkMIPS, randomSinkRAM, 10000, 200, 3, 0, 87.53, 82.44,coordX,coordY,direction,speed);	
		System.out.println("\t"+mobileName+ ": X=" + coordX + ", Y=" + coordY + ", Direction=" + directions[direction] + ", Speed=" + speed + ", MIPS= " + randomSinkMIPS + ", RAM = " + randomSinkRAM);
		printSinksFile(mobileName + ": X=" + coordX + ", Y=" + coordY + ", MIPS= " + randomSinkMIPS + ", RAM = " + randomSinkRAM + ", Direction=" + directions[direction] + ", Speed=" + speed );
		
		//--------------------------------------------------------------
		while(!connectGatewaySink(getGWDevices(),mobile, 0.5)){
			while(true) {
				coordX =  getValueI(1, (int)(MaxAndMin.MAX_X*0.8));
				coordY = getValueI(1,(int)(MaxAndMin.MAX_Y*0.8));
				if((coordX >= MaxAndMin.MAX_X*0.2) ||(coordY >= MaxAndMin.MAX_Y*0.2)){
					break;
				}
			}
			mobile.setCoord(coordX, coordY);
		}
		
		sinksInitCoordinates.put(mobile,initialCoord);
		
		/*int myCount=0;
		if(!connectGatewaySink(getGWDevices(),mobile, 0.5)){//getRand().nextDouble())){
			myCount++;
			System.out.println( mobile.getName()+" isn't connected");
		}
		System.out.println("\t \t total no connection in sink mobile : "+myCount);*/
		//mobile.setParentId(parentId);
		//---------------------------------------------------------------
	
		int randomNumOfSensorsForMobile= getValueI( 1 , 5);
		int randomSensorsID= getValueI( 0 , numOfSensorsTypes_BSN-1);
		System.out.println("		Creating " + numOfSensorsTasksPerSink + " BSN sensors in " +mobileName +":");
		
		for(int i= 0 ; i < numOfSensorsTasksPerSinkMobile ; i ++) {	
			SensorsInfo s = sensorsMap1.get(randomSensorsID);
			String sensorName = "sinkMob" + id + "-s-" + i;
			System.out.println("\t \t \t"+ sensorName + ":Type = " + s.getSensorType() + ".");
			double samplingRate = getValueI(20, 5000);
			double transmissionTime = 1 / samplingRate;
			Sensor sensor =new Sensor(sensorName, "", userId, appId, new DeterministicDistribution(transmissionTime));
			sensors.add(sensor);
			sensor.setGatewayDeviceId(mobile.getId());
			sensor.setLatency(6.0);  // latency of connection between sensors and the parent Smartphone is 6 ms
			mobile.setNumTasksAttached(numOfSensorsTasksPerSinkMobile);
		}
		numSinksMob++;
		return mobile;
	}
	public static List<FogDevice> getGWDevices(){
		List<FogDevice> gatewayList = new ArrayList<FogDevice>();
		for(FogDevice dev : fogDevices){
			if(dev.getName().startsWith("g"))
				gatewayList.add(dev);
		}
		
		return gatewayList;
	}
	public static boolean connectGatewaySink(List<FogDevice> gatewayDevcices, FogDevice sink, double delay){
		int	index=Distances.theClosestGateway(gatewayDevcices, sink);
		if(index>=0){
			if(gatewayDevcices.get(index).getMaxSinks() > gatewayDevcices.get(index).getSinks().size()){//it checks the accessPoint limit
				sink.setSourceGateway(gatewayDevcices.get(index));
				sink.setParentId(gatewayDevcices.get(index).getId());
				gatewayDevcices.get(index).setSinks(sink,Policies.ADD);
				gatewayDevcices.get(index).setUplinkLatency(gatewayDevcices.get(index).getUplinkLatency()+delay);
				NetworkTopology.addLink(gatewayDevcices.get(index).getId(), sink.getId(), sink.getUplinkBandwidth(), delay);
				System.out.println("		" + sink.getName()+" was connected to "+sink.getSourceGateway().getName());
				sinksInitGateways.put(sink,gatewayDevcices.get(index));
				return true;
			}
			else{//Gateway is full 
				return false;
			}
		}
		else {//The next Gateway is far way
			return false;
		}

	}
	
	
	private static double getValueD(double min, double max)
	{
		Random r = new Random();
		double randomValue = min + (max - min) * r.nextDouble();
		return randomValue;
	}
	private static int getValueI(int min, int max)
	{
		Random r = new Random();
		int randomValue = r.nextInt(max - min + 1) + min;
		return randomValue;
	}

	
	/**
	 * Creates a vanilla cloud device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createCloudDatacenter(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Host> hostList = new ArrayList<Host>();
		PowerHost host = null;
		
		//for( int i = 0 ; i < numberOfMachines ; i++) {
		// Create PEs and add these into a list- now iam supposing one PE for each machine
		List<Pe> peList = new ArrayList<Pe>();
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 11000000; // host storage
		int bw = 10000;

	    host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		
		
		hostList.add(host);
		//}
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		//double cost = getValueD(0.1,0.5);
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		// Data center Characteristics
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		
		// Create the data center 
		FogDevice datacenter = null;
		try {
			datacenter = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
					//new VmAllocationPolicySimple(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
					
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		datacenter.setLevel(level);
		datacenter.setMips((int) mips);
		return datacenter;
	}
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower,int coordX, int coordY, int direction, double speed) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 4000; // host storage
		int bw = 10000;	// bandwidth to virtual machines inside a Host

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		
		
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		//This is for grouping all the four powers together despite that idle and busy power are parameters in the FogDevice class
		//0: IdleCPU, 1: ExecCPU, 2:IdleNIC, 3:RecvNIC
		ArrayList<Double> powerList = new ArrayList<Double>();
		powerList.add(360*(Math.pow(10, -3)));
		powerList.add(440*(Math.pow(10, -3)));
		powerList.add(Math.pow(10, -14));
		powerList.add(2.85*(Math.pow(10, -1)));
		
		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		fogdevice.setMips((int) mips);
		fogdevice.setPowerList(powerList);
		fogdevice.setCoord(coordX, coordY);
		fogdevice.setDirection(direction);
		fogdevice.setSpeed(speed);
		return fogdevice;
	}

	/**
	 * IoTHC application 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		List<AppLoop> loops = new ArrayList<AppLoop>();
		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
		
		//----------------------------------------------------------------------
		// Adding Modules (vertices) to the application model (bipartite graph)	
		/*for( Sensor sensor: sensors) {
			String sensorName = sensor.getTupleType();//.getName();		
			for ( int i = 0 ; i< numOfModules ;i++) {
				final AppModule app  = modulesMap.get(i);
				if((app.getSensorsName().get(0)).equals(sensorName)) {
					Calendar issueCalendar = Calendar.getInstance(); 
					//Module
					application.addAppModule(app.getName(), app.getRam(), app.getMips(), app.getSize(), app.getBw(), app.getClassification(),app.getSensorsName(), sensor, app.getMaxResponseTime(), issueCalendar.getTimeInMillis(),0);
					//Edge
					application.addAppEdge(sensorName, app.getName(), 3000, 500, sensorName, Tuple.UP, AppEdge.SENSOR);//Note: till now tupleCPULength & tupleNWLength are static defined. Later, after we understand what they are, can be dynamicly defined or staticly saved with the information of the sensors in sensorsInformation class instance at the head of the main method.
					//Tuple
					application.addTupleMapping( app.getName(), app.getSensorsName().get(0), "_SENSOR", new FractionalSelectivity(1));//Note: I am defining only one tuple for each module (relating the first sensor input of the module to the output)
					//Loop
					final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{add(app.getSensorsName().get(0));add(app.getName());}});					
					loops.add(loop1);
				}	
			}
		}*/
		int x = 0;
		for( final Sensor sensor: sensors) {
			String sensorName = sensor.getTupleType();//.getName();		
			
					Calendar issueCalendar = Calendar.getInstance(); 
					//Module
					final String moduleName= "Task(" + sensor.getName()+")";
					Random r1 = new Random();
					int randomValueMIPS = r1.nextInt(4 - 0 + 1) + 0;
					int randomtaskMIPS = taskMIPS[randomValueMIPS]; //getValueI(100,500);
					double moduleMIPS =getValueI(10,1000);//randomtaskMIPS ; 
					double moduleDataSize = getValueD(((36.0/1000.0)*8.0),((500.0/1000.0)*8.0)); // 100 -500 MB= 100*8 Mb - 500*8 Mb cause BW of links is in Mbps
					//double moduleDataSize = getValueD(((100/1000.0)*8.0),((500.0)*8.0)); // 100 -500 MB= 100*8 Mb - 500*8 Mb cause BW of links is in Mbps
					int moduleClassification = getValueI(1,3);	
					double moduleMaxResponse;
					
					if(moduleClassification == 1) { // data analysis task has no max response (not real time nor critical) 
						moduleMaxResponse = 1000.0 * 1000.0 * 1000.0;// very big value //2 * 60 * 60 * 1000;
					}
					else {
						
					    moduleMaxResponse = getValueD(15.0 * 1000.0, 30.0*60.0* 1000.0); //  in millisecond 
					}
					application.addAppModule(moduleName, 10, moduleMIPS, moduleDataSize, 100, moduleClassification,null, sensor, moduleMaxResponse, 0 ,0 ,0);
					//Edge
					application.addAppEdge(sensorName , moduleName, 3000, 500, sensorName, Tuple.UP, AppEdge.SENSOR);//Note: till now tupleCPULength & tupleNWLength are static defined. Later, after we understand what they are, can be dynamicly defined or staticly saved with the information of the sensors in sensorsInformation class instance at the head of the main method.
					//Tuple
					application.addTupleMapping( moduleName, sensor.getName(), 	"_SENSOR"+x, new FractionalSelectivity(1));//Note: I am defining only one tuple for each module (relating the first sensor input of the module to the output)
					x++;
						
		}
		
		//Calculate energy consumed by the transmission of sink devices
		double energySink = 0; double powerNICTxWifi = 1.254; //Watt
		double BWs = 300; //Mbps
		for( AppModule app: application.getModules())
			energySink+= (app.getSize() / BWs) * powerNICTxWifi;
		
		//Print in file  
				try(FileWriter fwMakespan = new FileWriter("EnergySink.txt", true);
				BufferedWriter bwMakespan = new BufferedWriter(fwMakespan);
				PrintWriter outMakespan = new PrintWriter(bwMakespan))
					{
					String energyString = Double.toString(energySink);
					outMakespan.println(varyingFactor + " " + energyString);
					   
					} 
				catch (IOException e) {
					    //e.printStackTrace();
					}

		application.setLoops(loops);
		System.out.println("Total number of tasks = " + totalNumOfTasks  );
		return application;
	}
	private static void printSinksFile(String msg) {
		try(FileWriter sinksFile = new FileWriter("sinks.txt", true);
				BufferedWriter sinksBuffer = new BufferedWriter(sinksFile);
				PrintWriter sinksPrint = new PrintWriter(sinksBuffer))
				{
			sinksPrint.println( msg);		   
				} 
			catch (IOException e) {
			   //e.printStackTrace();
			}
	}
	private static void printFogsFile(String msg) {
		try(FileWriter sinksFile = new FileWriter("Fogs.txt", true);
				BufferedWriter sinksBuffer = new BufferedWriter(sinksFile);
				PrintWriter sinksPrint = new PrintWriter(sinksBuffer))
				{
			sinksPrint.println( msg);		   
				} 
			catch (IOException e) {
			   //e.printStackTrace();
			}
	}
	private static void resetInitialLoadIssue() {
		// reset initial loads		
		for(FogDevice device : fogDevices){		
			device.setInitialLoad(0);			
		}
		
		//reset issue times
		Calendar issueCalendar = Calendar.getInstance(); 
		//long timeDiffernece = issueCalendar.getTimeInMillis() - application.getModules().get(0).getIssueTime();
		for(AppModule module : application.getModules()){
			//module.setIssueTime(issueCalendar.getTimeInMillis());
			module.setIssueTime(0);
		}
		
		// reset initial sinks mobile location and data
		for(FogDevice sinkMobile: sinksMobile) {
			// Get initial coordinates and reset them
			Coordinate initCoord = sinksInitCoordinates.get(sinkMobile);
			sinkMobile.setCoord(initCoord.getCoordX(), initCoord.getCoordY());
			
			
			// Get current gateway and remove it 
			FogDevice currentSourceGateway = sinkMobile.getSourceGateway();
			if(currentSourceGateway != null) {
			currentSourceGateway.setSinks(sinkMobile, Policies.REMOVE);
			sinkMobile.getSourceGateway().setUplinkLatency(currentSourceGateway.getUplinkLatency()-0.5);
			NetworkTopology.addLink(currentSourceGateway.getId(), sinkMobile.getId(), 0.0, 0.0);//remove link
			}
			
			// Get initial Gateways and reset it
			sinkMobile.setSourceGateway( sinksInitGateways.get(sinkMobile));
			sinkMobile.setParentId(sinksInitGateways.get(sinkMobile).getId());
			sinksInitGateways.get(sinkMobile).setSinks(sinkMobile,Policies.ADD);
			sinksInitGateways.get(sinkMobile).setUplinkLatency(sinksInitGateways.get(sinkMobile).getUplinkLatency()+0.5);
			
			
		}
	}
	
	
}