package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudfogHC.LogPlacement;
import org.cloudfogHC.SortByEmergency;
import org.cloudfogHC.SortByWeight;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.mobility.MaxAndMin;

public class ModulePlacementHealthEdge extends ModulePlacement{

	Calendar startCalendar ;
	protected ModuleMapping moduleMapping;
	private List<Sensor> sensors;
	private List<Actuator> actuators;
	private int cloudId;
	List<AppModule> modules;
	List<FogDevice> fogDevicesList;
	List<FogDevice> cloudDevicesList;
	HashMap< AppModule , FogDevice > balancedAllocation = new HashMap< AppModule , FogDevice> ();	// Balanced Allocation
	HashMap< FogDevice , List<AppModule> > balancedAllocation2 = new HashMap<  FogDevice , List<AppModule>> ();	// Balanced Allocation
	double resultVaryingFactorValue;
	double []randomBW = new double[4];
	private double finalTotalCostForCloudNodes;
	private int reallocationStrategy;
	private int clock;
	private double mobilityAccumulatedMakespan;
	private int allocationSamplingFreq;
	private int randomValue1ForCloudBW;
	// Output
	double finalMakespan;
	double missRatio;
	double AvgDelay;
	double accumulatedMissRatio;
	double accumulatedAvgDelay;
	double roundProcessingTime;
	double accumulatedProcessingTime;
	double accumulatedCost;
	double networkLoad;
	double energyConsumption;
	
	//HealthEdge	
	double L; //Emergency Threshold
	List<AppModule> totalCloudQueue = new ArrayList<AppModule>();
	HashMap< FogDevice , List<AppModule> > EdgeCloudAllocation = new HashMap<  FogDevice , List<AppModule>> ();
	
	/**
	 * Stores the current mapping of application modules to fog devices 
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;
	
	public ModulePlacementHealthEdge(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, Application application , int reallocationStrategy , int allocationSamplingFreq , double resultVaryingFactorValue){
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setSensors(sensors);
		this.setActuators(actuators);
		//setCurrentCpuLoad(new HashMap<Integer, Double>());
		setCurrentModuleMap(new HashMap<Integer, List<String>>());
		setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
		setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
		for(FogDevice dev : getFogDevices()){
			//getCurrentCpuLoad().put(dev.getId(), 0.0);
			getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
			getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
			getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}
		
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
		this.cloudId = CloudSim.getEntityId("cloud");
		this.setResultVaryingFactorValue(resultVaryingFactorValue);
		//this.randomBW[0]=10;this.randomBW[1]=100;this.randomBW[2]=512;this.randomBW[3]=1024;
		this.setReallocationStrategy(reallocationStrategy);
		this.setAllocationSamplingFreq(allocationSamplingFreq);
		updateInitialLoads(allocationSamplingFreq);
		this.setClock(0);
		this.setNetworkLoad(0);
	
		this.setL(0.8);
		mapModules();
		calculateEvaluation();
		//printMobilityResultsVsClock();
		//if(clock == MaxAndMin.MAX_SIMULATION_TIME/1000-getAllocationSamplingFreq()) {
			//printMobilityResults();
		//}
	}
	
	////////////////////////////////////////////////////////////////////////
	@Override
	protected void mapModules() {
		setStartCalendar(Calendar.getInstance());
		
		//------------------------------------------------------------
        // Call the HEFT scheduling algorithm by calling the two methods consecutively 
        LogPlacement.debug("====================================================");
		LogPlacement.debug("			Cloud-Only Allocation 			                 ");
		LogPlacement.debug("====================================================");
        healthEdge();
        LogPlacement.debug("\nEnded Module placement. \n ");
        LogPlacement.debug("Total Allocation Algorithm Time: "+ roundProcessingTime + " milliseconds");
        LogPlacement.debug("__________________________________________________________________________________");
	}
	
	public void healthEdge() {
		//	healthEdgeTaskEmergencDetermination:
		// 0.3 :Data analysis, 0.6: context management, 0.9: critical analysis,critical control
		modules = getApplication().getModules(); //int numOfModules = modules.size();
		List<AppModule> edgeTasks = new ArrayList<AppModule>();
		List<FogDevice> gatewayDevicesList = getGateways();	
		List<AppModule> returnedCloudQueue = new ArrayList<AppModule>(); 
		for(FogDevice device : gatewayDevicesList){	
			edgeTasks.clear();
			for(AppModule module : modules){
				int sinkID = module.getSensors().getGatewayDeviceId();
				//int gatewayID = getDeviceById(sinkID).getParentId(); 
				int gatewayID = getFogDeviceById(sinkID).getParentId();			
				if(	 (gatewayID == device.getId())  ) { 
					edgeTasks.add(module);
				}
			}
			if(edgeTasks.size() != 0)
			returnedCloudQueue = healthEdgeTaskQueuing(device, edgeTasks);
		}

		// allocate on the cloud (based on prediction!)
		//List<FogDevice> cloudFogDevices = allocateCloud(returnedCloudQueue,gatewayDevicesList);
		List<FogDevice> cloudFogDevices = allocateCloud(totalCloudQueue,gatewayDevicesList);
		//allocateCloudEvenly();
		//healthEdgeTaskScheduling();
		
		//6- Calculate edge workstation Makespan
  		double makeSpanPre = getFogDevices().get(0).getInitialLoad();	
  		for(FogDevice device : cloudFogDevices){ //for(FogDevice device : getFogDevices()){
  			double load = device.getInitialLoad();
  			if(load > makeSpanPre) {
  				makeSpanPre = device.getInitialLoad();
  			}		
  		}
  		LogPlacement.debug("Makespan  =  " + makeSpanPre);
  		finalMakespan = makeSpanPre;
  		
  		setRoundProcessingTime((Calendar.getInstance().getTimeInMillis() - startCalendar.getTimeInMillis()));
        setAccumulatedProcessingTime(getAccumulatedProcessingTime()+getRoundProcessingTime());
        
        // Energy Evaluation
			//1-Computing Energy
			 double E_fComp = 0;
			 double E_fComm = 0;
			 double E_f = 0;
			 double E_Ftotal = 0;
			 for(Map.Entry<FogDevice, List<AppModule>> allocation: EdgeCloudAllocation.entrySet()) {
				 if(allocation.getKey().getName().startsWith("g")) {
					// 1-Computation
						//Eq16 (remove sum from paper)
							E_fComp = ( allocation.getKey().getPowerList().get(0)) * (finalMakespan-allocation.getKey().getInitialLoad()) + ( allocation.getKey().getPowerList().get(1) *allocation.getKey().getInitialLoad());
							
					//2-Communication Energy
					double T_FComm_Recv = 0;
					for(AppModule app : allocation.getValue()) {
						int sinkID = app.getSensors().getGatewayDeviceId();
						int gatewayID = getFogDeviceById(sinkID).getParentId();
						if(	gatewayID != (allocation.getKey().getId())){
							T_FComm_Recv = app.getSize() / 1024; //fogBW
							E_fComm += ( allocation.getKey().getPowerList().get(2) * (finalMakespan - T_FComm_Recv )) + (allocation.getKey().getPowerList().get(3) * T_FComm_Recv);
						}
					}					
					// 3- Final energy consumption for a fog and total for all 
					E_f = E_fComp + E_fComm;
					E_Ftotal+= E_f;
					E_fComm = 0;
				 }
			 }
			 energyConsumption = E_Ftotal;
         
	}
	
	public List<AppModule> healthEdgeTaskQueuing(FogDevice device, List<AppModule> edgeTasks ) {
		HashMap< FogDevice , List<AppModule> > edgeAllocatedTasks = new HashMap<  FogDevice , List<AppModule>> ();	
		List<AppModule> edgeQueue = new ArrayList<AppModule>();
		List<AppModule> cloudQueue = new ArrayList<AppModule>();
		int numOfModules = edgeTasks.size();
		for( int i = 0 ; i < numOfModules; i++) {		
			// we assume mapping the three level classes into 3 emergency values: class 3-> 0.9 , class 2->0.6, class1->0.3 because I don't have the dataset to calculate emergency
			double emergency = edgeTasks.get(i).getClassification() * 0.3;	
			if(emergency > L) {
				edgeQueue.add(edgeTasks.get(i));
			}
			else {
				cloudQueue.add(edgeTasks.get(i));
			}
		}
		
		/*// Phase 1 : order according to emergency
		// I. Edge Queue
			// Print the list of tasks unsorted
			LogPlacement.debug("\nEdgeList before sorting Emergency:");
	        for(AppModule module : edgeQueue){
	        	LogPlacement.debug("\t"+module.getName() + (module.getClassification() * 0.3)) ;
	         
	        }
	        // Sort the tasks according to the emergencies
	        Collections.sort(edgeQueue, new SortByEmergency());		   
	        // Print the list of tasks after sorting 
	        LogPlacement.debug("\nEdgeList after sorting Emergency:");
	        for(AppModule module : edgeQueue){
	        	LogPlacement.debug("\t"+module.getName()+ (module.getClassification() * 0.3)) ;
	        }
        // I. Cloud Queue
     		// Print the list of tasks unsorted
     		LogPlacement.debug("\nCloudList before sorting Emergency:");
             for(AppModule module : cloudQueue){
             	LogPlacement.debug("\t"+module.getName() + (module.getClassification() * 0.3)) ;
              
             }
             // Sort the tasks according to the emergencies
             Collections.sort(cloudQueue, new SortByEmergency());		   
             // Print the list of tasks after sorting 
             LogPlacement.debug("\nCloudList after sorting Emergency:");
             for(AppModule module : cloudQueue){
             	LogPlacement.debug("\t"+module.getName()+ (module.getClassification() * 0.3)) ;
             }*/
        
		//---------------------------------------
       // Phase 2: Priority based task queuing order according to priorities
       // double priorityScore[] = new double [numOfModules];	
		LogPlacement.debug(device.getName());
		for(AppModule module:edgeQueue) {
			LogPlacement.debug("edgequeue: " + module.getName());			
		}
		
		for(AppModule module:cloudQueue) {
			LogPlacement.debug("cloudqueue: " + module.getName());
		}
		
        LogPlacement.debug("Priorities: ");
		// Calculate priorities (I'll name it weights here because other classes are using the same attribute)
		// I. Edge 
			for( int i = 0 ; i < edgeQueue.size(); i++) {			
				double emergency = edgeQueue.get(i).getClassification() * 0.3;
				double queueLatency = 1;	//i; 
				double priorityScore= (emergency * queueLatency) ;				
				edgeQueue.get(i).setWeight( priorityScore);
			}
		// II. cloud
			for( int i = 0 ; i < cloudQueue.size(); i++) {			
				double emergency = cloudQueue.get(i).getClassification() * 0.3;
				double queueLatency = 1;	//i; 
				LogPlacement.debug(cloudQueue.get(i).getName() + "queue latency:" + queueLatency+ " data :" + cloudQueue.get(i).getSize());
				double priorityScore= (emergency * queueLatency) / cloudQueue.get(i).getSize();				
				cloudQueue.get(i).setWeight( priorityScore);
			}
		
        // Sort the tasks according to the priorities
        Collections.sort(edgeQueue, new SortByWeight());
        Collections.sort(cloudQueue, new SortByWeight());
        
        // Print the list of tasks after sorting and calculate workload of this edge workstation
		LogPlacement.debug("\nEdgeList after sorting Priorites:");		
		LogPlacement.debug("Device " +  device.getName() + " has the following tasks: (" );
	
		// Allocate on edge and calculate the queue latency
		double loadEdgeWorkstation = device.getInitialLoad();
		for(AppModule module : edgeQueue){
			
			// 1.	Caculate queue latency
			double tQueue = 0; // queue latency
			// Get preceding tasks
			List<AppModule> precedingTasks = new ArrayList<AppModule>();
			precedingTasks = edgeQueue.subList(0, edgeQueue.indexOf(module));
			// Loop over the preceding tasks to calculate summation of their computation 
			for(AppModule precedingModule : precedingTasks){
				double tCMPPreceding;	// predicted processing / computation time
				if(precedingModule.gettCMPHistory() == 0 ) {				
					// First run. So, there is no history. The current is the history 
					tCMPPreceding = precedingModule.getMips() / device.getMips();
					precedingModule.setUtHistory(device.getMips());
					precedingModule.settCMPHistory(tCMPPreceding);				
				}
				else {
					double tCMPHistory = precedingModule.gettCMPHistory();
					double utHistory = precedingModule.getUtHistory();
					double ut = device.getMips(); // for now (future predicted capacity)
					tCMPPreceding = utHistory / ut * tCMPHistory;
				}
				tQueue+= tCMPPreceding;
			}
			
			// 2. Calculate computation time of current task
			double tCMP;	// predicted processing / computation time
			if(module.gettCMPHistory() == 0 ) {
				// First run. So, there is no history. The current is the history 
				tCMP = module.getMips() / device.getMips();
				module.setUtHistory(device.getMips());
				module.settCMPHistory(tCMP);		
			}
			else {
				double tCMPHistory = module.gettCMPHistory();
				double utHistory = module.getUtHistory();
				double ut = device.getMips(); // for now (future predicted capacity)
				tCMP = utHistory / ut * tCMPHistory;
			}
			
			// 3. Calculate predicted processing time 
			double tProcessing = tCMP + tQueue;
			
			loadEdgeWorkstation = tProcessing;
			module.setFinishTime(tProcessing *1000);
			
			
		}
		device.setInitialLoad(loadEdgeWorkstation);	// loadEdgeWorkstation holds queue latency of last task which is time to finish last task and at the same time workload of device
		EdgeCloudAllocation.put(device, edgeQueue);
		
	
		/*// Allocate on edge 
		double localCost;
		for(AppModule module : edgeQueue){
        	LogPlacement.debug("\t"+module.getName()+ module.getWeight()+", ") ;
        	localCost = module.getMips()	/ device.getMips();
			loadEdgeWorkstation+= localCost; //device.getInitialLoad() + localCost;
			device.setInitialLoad(loadEdgeWorkstation);
			module.setFinishTime((long) loadEdgeWorkstation *1000);
			//System.out.println("Load for device " + device.getName() + " after allocating task " + module.getName() + + module.getId() + " = " + load_s);		
		}*/
		//LogPlacement.debug("Load for device " + device.getName() + " after allocating=" + device.getInitialLoad());
		
		// ---------------------------------------------------------
        LogPlacement.debug("\nCloudList after sorting Priorites:");
        for(AppModule module : cloudQueue){
        	LogPlacement.debug("\t"+module.getName()+ module.getWeight()) ;
        }
        totalCloudQueue.addAll(cloudQueue);
        return cloudQueue;
	}
	public void healthEdgeTaskScheduling() {
		modules = getApplication().getModules(); 
		fogDevicesList = getFogDevices();
		cloudDevicesList = getCloudDevices();
		int iterator = 0;
		for(AppModule module : modules){
			//HashMap< Double , FogDevice > cloud_costList = new HashMap< Double , FogDevice> ();	//load of all servers if task t is allocated
			//HashMap<  FogDevice , Double > lastCostL = new HashMap<  FogDevice, Double> ();	//load of all servers if task t is allocated
			
			
			/*int sinkID = module.getSensors().getGatewayDeviceId();
			FogDevice sm = getFogDeviceById(sinkID)	;
			if(sm.getSourceGateway()!=null){
				
				//5- Allocate to next cloud data center			
				double remoteCost, lastCost = 0;	
				FogDevice cloud = cloudDevicesList.get(iterator);
				remoteCost =  (module.getMips()	/ cloud.getMips())+ Math.max(cloud.getInitialLoad(), (module.getSize() / cloud.getUplinkBandwidth()));	
				lastCost =  (module.getMips()	/ cloud.getMips())+ Math.abs(cloud.getInitialLoad()- (module.getSize() / cloud.getUplinkBandwidth()));
				module.setLastCost(lastCost);
				LogPlacement.debug( module.getName() + " current cost = " +  lastCost);
				module.setFinishTime((long) remoteCost *1000);
		
				// allocate the task/module to that fog/cloud device
			    balancedAllocation.put(module, cloud);
			    LogPlacement.debug("-> Allocating " +  module.getName() +  " to " + cloud.getName() );
		
				// add the task to the list of tasks allocated to that server				
				
			   boolean firstTaskToBeAllocated = true;
			   List<AppModule> modulesList = null;
				for( FogDevice keyDevice : balancedAllocation2.keySet()) {
					if(keyDevice.getName().equals(cloud.getName())){
						 modulesList = balancedAllocation2.get(keyDevice);
						 AppModule moduleWithClock = new AppModule(module);
						 moduleWithClock.setName(module.getName()+getClock());
						 modulesList.add(moduleWithClock);
						 balancedAllocation2.put(keyDevice, modulesList);
						 firstTaskToBeAllocated = false;
					}
				}
				if(firstTaskToBeAllocated == true) {
					 
					 modulesList = new ArrayList<AppModule>();						 
					 // Renaming the task with its name accompanied with the current clock
					 AppModule moduleWithClock = new AppModule(module);
					 moduleWithClock.setName(module.getName()+getClock());							 
					 modulesList.add(moduleWithClock);
					 balancedAllocation2.put(cloud, modulesList);
					
				}
				LogPlacement.debug("Load for device " + cloud.getName() + " after allocating task " + module.getName() +  " = " + remoteCost);		
				LogPlacement.debug("Device " +  cloud.getName() + " has the following tasks: (" );
				for(AppModule m : modulesList) LogPlacement.debug(m.getName() + " , ");
				LogPlacement.debug(")");
				cloud.setInitialLoad(remoteCost);
				}
			
			iterator++;			
				if(iterator%cloudDevicesList.size()==0) iterator=0;*/
				
		}
		//6- Calculate Makespan
		double makeSpanPre = fogDevicesList.get(0).getInitialLoad();	
		for(FogDevice device : cloudDevicesList){
			double load = device.getInitialLoad();
			if(load > makeSpanPre) {
				makeSpanPre = device.getInitialLoad();
			}		
		}
		LogPlacement.debug("Makespan  =  " + makeSpanPre);
		finalMakespan = makeSpanPre;
	}
	public List<FogDevice> allocateCloud(List<AppModule> cloudQueue , List<FogDevice> gatewayDevicesList) {
		
		//fogDevicesList = getGateways();
		List<FogDevice> cloudFogDevices = new ArrayList<FogDevice>();
		cloudFogDevices.addAll(gatewayDevicesList); //cloudFogDevices.addAll(getGateways());
		cloudFogDevices.addAll(getCloudDevices());
		
		for(AppModule module : cloudQueue){
			double lastCost = 0;
			
			// Allocate to cloud or remote edge, the one which has shortest task processing time
			HashMap< Double , FogDevice > load_S_t = new HashMap< Double , FogDevice> ();	//load of all servers if task t is allocated
			HashMap<  FogDevice , Double > lastCostL = new HashMap<  FogDevice, Double> ();	//load of all servers if task t is allocated
			for(FogDevice device : cloudFogDevices){	
				int sinkID = module.getSensors().getGatewayDeviceId();
				int gatewayID = getDeviceById(sinkID).getParentId(); 
				if(	 (gatewayID != device.getId())  ) { 
				
					double tQueue = 0;	// Queue latency
					double tCMP;	// predicted processing / computation time
					double tTran;	// predicted transmission time		
					if(device.getName().startsWith("c")) {
						
						// Get preceding tasks
						List<AppModule> precedingTasks = new ArrayList<AppModule>();
						precedingTasks = cloudQueue.subList(0, cloudQueue.indexOf(module));
						
						// 1. Calculate Queue latency tQueue 
						// Loop over the preceding tasks to calculate summation of their computation 
						for(AppModule precedingModule : precedingTasks){
							double tTranPreceding = precedingModule.getSize() / device.getUplinkBandwidth();	//bandwidth of cloud
							tQueue+= tTranPreceding;
						}
						
						// Calculate computation and transmission time
						
						// 2. Calculate computation time of current task
						//double theta  = 1/200; // I remove theta because i am assuming heterogenouty not homogenous
						if(module.gettCMPHistory() == 0 ) {
							// First run. So, there is no history. The current is the history 
							tCMP = module.getMips() / device.getMips();
							module.setUtHistory(device.getMips());
							module.settCMPHistory(tCMP);		
						}
						else {
							double tCMPHistory = module.gettCMPHistory();
							double utHistory = module.getUtHistory();
							double ut = device.getMips(); // for now (future predicted capacity)
							tCMP = utHistory / ut * tCMPHistory;
						}
						//tCMP = tCMP / theta;
						
						
						// Processing time on cloud 
						// 3. Calculate transmission time of current task
						double bandWidth=device.getUplinkBandwidth();
					    tTran = module.getSize() / bandWidth;	
					}
					else {
						
						// 1.	Calculate queue latency
						
						// Loop over the preceding tasks to calculate summation of their computation 
						if(EdgeCloudAllocation.get(device) != null) {
							// Get preceding tasks
							List<AppModule> precedingTasks = EdgeCloudAllocation.get(device);
							
							for(AppModule precedingModule : precedingTasks){
								double tCMPPreceding;	// predicted processing / computation time
								if(precedingModule.gettCMPHistory() == 0 ) {				
									// First run. So, there is no history. The current is the history 
									tCMPPreceding = precedingModule.getMips() / device.getMips();
									precedingModule.setUtHistory(device.getMips());
									precedingModule.settCMPHistory(tCMPPreceding);				
								}
								else {
									double tCMPHistory = precedingModule.gettCMPHistory();
									double utHistory = precedingModule.getUtHistory();
									double ut = device.getMips(); // for now (future predicted capacity)
									tCMPPreceding = utHistory / ut * tCMPHistory;
								}
								tQueue+= tCMPPreceding;
							}
						}
						
						// Calculate computation and transmission time
						// 2. Calculate computation time of current task
						if(module.gettCMPHistory() == 0 ) {
							// First run. So, there is no history. The current is the history 
							tCMP = module.getMips() / device.getMips();
							module.setUtHistory(device.getMips());
							module.settCMPHistory(tCMP);		
						}
						else {
							double tCMPHistory = module.gettCMPHistory();
							double utHistory = module.getUtHistory();
							double ut = device.getMips(); // for now (future predicted capacity)
							tCMP = utHistory / ut * tCMPHistory;
						}
						//tCMP = tCMP / theta;
						
						
						// Processing time on cloud 
						// 3. Calculate transmission time of current task
						double bandWidth = 1024;
					    tTran = module.getSize() / bandWidth;	
					}
					
					// 4. Calculate predicted processing time 
					double tProcessing = tQueue + tCMP + tTran;	
					lastCost = tCMP + tTran;
					lastCostL.put(device, tProcessing);
					load_S_t.put(tProcessing , device);
				}
			}	

		//-----------------------------------------------------------------
		// Get minimum load from all server loads
		double min = load_S_t.keySet().iterator().next();
		for( double keyLoad : load_S_t.keySet()) {
			if(keyLoad < min) 
				min = keyLoad;
		}
	
		//-----------------------------------------------------------------
		// Get the server with the minimum load to allocate the task to it
		for(Map.Entry< Double , FogDevice > entryMinLoad : load_S_t.entrySet()) 
			   if(entryMinLoad.getKey() == min) {
				   
				   FogDevice chosenServerToAllocateT = entryMinLoad.getValue();
				  // LogPlacement.debug("->Choosing "+ chosenServerToAllocateT.getName() + " to allocate the task to.");
				   
				   // -------------------------------------------
				   // allocate the task/module to that server/fogdevice

				   //-------------------------------------------
				   // add the task to the list of tasks allocated to that server				
				   //double min = load_S_t.keySet().iterator().next();
				   boolean firstTaskToBeAllocated = true;
					for( FogDevice keyDevice : EdgeCloudAllocation.keySet()) {
						if(keyDevice.getName().equals(chosenServerToAllocateT.getName())){
							 List<AppModule> modulesList2 = EdgeCloudAllocation.get(keyDevice);
							 modulesList2.add(module);
							 EdgeCloudAllocation.put(keyDevice, modulesList2);
							 firstTaskToBeAllocated = false;
						}
					}
					if(firstTaskToBeAllocated == true) {
						 List<AppModule> modulesList2 = new ArrayList<AppModule>();
						 modulesList2.add(module);
						 EdgeCloudAllocation.put(chosenServerToAllocateT, modulesList2);
					}

					chosenServerToAllocateT.setInitialLoad(min);
					module.setLastCost(lastCost);
					module.setFinishTime( min * 1000);
					if(chosenServerToAllocateT.getName().startsWith("c")) {
					setNetworkLoad(getNetworkLoad()+module.getSize());
					}
			   }
		
		
	
		}
		return cloudFogDevices;
	}
	public void allocateCloudEvenly() {
		cloudDevicesList = getCloudDevices();
		int iterator = 0;
		for(AppModule module : totalCloudQueue){
			
			int sinkID = module.getSensors().getGatewayDeviceId();
			FogDevice sm = getFogDeviceById(sinkID)	;
			if(sm.getSourceGateway()!=null){
				
				// Allocate to next cloud data center			
				double remoteCost, lastCost = 0;	
				FogDevice cloud = cloudDevicesList.get(iterator);
				remoteCost =  (module.getMips()	/ cloud.getMips())+ Math.max(cloud.getInitialLoad(), (module.getSize() / cloud.getUplinkBandwidth()));	
				lastCost =  (module.getMips()	/ cloud.getMips())+ Math.abs(cloud.getInitialLoad()- (module.getSize() / cloud.getUplinkBandwidth()));
				//module.setLastCost(lastCost);
				LogPlacement.debug( module.getName() + " current cost = " +  lastCost);
				module.setFinishTime(remoteCost *1000);
		
				/*// allocate the task/module to that fog/cloud device
			    balancedAllocation.put(module, cloud);
			    LogPlacement.debug("-> Allocating " +  module.getName() +  " to " + cloud.getName() );
		*/
				// add the task to the list of tasks allocated to that server				
				
			   boolean firstTaskToBeAllocated = true;
			   List<AppModule> modulesList = null;
				for( FogDevice keyDevice : EdgeCloudAllocation.keySet()) {
					if(keyDevice.getName().equals(cloud.getName())){
						 modulesList = EdgeCloudAllocation.get(keyDevice);
						 AppModule moduleWithClock = new AppModule(module);
						 moduleWithClock.setName(module.getName());
						 modulesList.add(moduleWithClock);
						 EdgeCloudAllocation.put(keyDevice, modulesList);
						 firstTaskToBeAllocated = false;
					}
				}
				if(firstTaskToBeAllocated == true) {
					 
					 modulesList = new ArrayList<AppModule>();						 
					 // Renaming the task with its name accompanied with the current clock
					 AppModule moduleWithClock = new AppModule(module);
					 moduleWithClock.setName(module.getName());			//module.getName()+getClock()				 
					 modulesList.add(moduleWithClock);
					 EdgeCloudAllocation.put(cloud, modulesList);
					
				}
				LogPlacement.debug("Load for device " + cloud.getName() + " after allocating task " + module.getName() +  " = " + remoteCost);		
				//calculate network load
				setNetworkLoad(getNetworkLoad()+module.getSize());
				LogPlacement.debug("Device " +  cloud.getName() + " has the following tasks: (" );
				for(AppModule m : modulesList) LogPlacement.debug(m.getName() + " , ");
				LogPlacement.debug(")");
				cloud.setInitialLoad(remoteCost);
				}
			
			iterator++;			
				if(iterator%cloudDevicesList.size()==0) iterator=0;
				
		}
		
	}
	//////////////////////////////////////////////////////////////////////
	public void calculateEvaluation() {
		// Calculate missratio and delay
				double numOfModulesMissMaxResTime = 0;
				double totalNumOfModulesWzMaxResponse = 0;
				double totalDelay = 0 ;
				missRatio = 0 ;
				AvgDelay = 0;
				for(AppModule resultModule : modules){
					int sinkID = resultModule.getSensors().getGatewayDeviceId();
					FogDevice sm = getFogDeviceById(sinkID)	;
					//FogDevice sm = getDeviceById(sinkID);
					if(sm.getSourceGateway()!=null){
						if(resultModule.getClassification() != 1) {
							//LogPlacement.debug("task: " + resultModule.getName()+ " issue: " +resultModule.getIssueTime() + " , response: "+ resultModule.getMaxResponseTime() + ", finish: "+ resultModule.getFinishTime() );
							totalNumOfModulesWzMaxResponse++;
							if((resultModule.getIssueTime() + resultModule.getMaxResponseTime() )< resultModule.getFinishTime()) {
								numOfModulesMissMaxResTime++;
								totalDelay+= (  resultModule.getFinishTime() - (resultModule.getIssueTime() + resultModule.getMaxResponseTime()));
							}
						}
					}
				}
				
				missRatio = numOfModulesMissMaxResTime / totalNumOfModulesWzMaxResponse;
				accumulatedMissRatio+= missRatio;
				AvgDelay = totalDelay / numOfModulesMissMaxResTime;
				accumulatedAvgDelay+= (Double.isNaN(AvgDelay)?0:AvgDelay);
	}	
	public void printMobilityResultsVsClock() {
		
		//Printing Makespan Vs Clock  
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanClkHealthedge.txt", true);
		BufferedWriter bwMakespan = new BufferedWriter(fwMakespan);
		PrintWriter outMakespan = new PrintWriter(bwMakespan))
			{
			String makespanString = Double.toString(finalMakespan);
			outMakespan.println(getClock() + " " + makespanString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
	
		//Printing MaxResponseTime(deadline) miss ratio Vs the varying factor
		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioClkHealthedge.txt", true);
				BufferedWriter bwMissRatio = new BufferedWriter(fwMissRatio);
				PrintWriter outMissRatio = new PrintWriter(bwMissRatio))
					{
					String missRatioString = Double.toString(missRatio);
					outMissRatio.println(getClock()+ " " + missRatioString);
					   
					} 
				catch (IOException e) {
					    //e.printStackTrace();
					}
		
		//Printing Average delay in missing MaxResponseTime Vs the varying factor   
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayClkHealthedge.txt", true);
		BufferedWriter bwAvgDelay = new BufferedWriter(fwAvgDelay);
		PrintWriter outAvgDelay = new PrintWriter(bwAvgDelay))
			{
			String AvgDelayString = Double.toString(AvgDelay);
			outAvgDelay.println(getClock() + " " + AvgDelayString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		//Printing processing time Vs the varying factor   
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeClkHealthedge.txt", true);
		BufferedWriter bwProcessing = new BufferedWriter(fwProcessing);
		PrintWriter outProcessing = new PrintWriter(bwProcessing))
			{
			String processingTimeString = Double.toString(getRoundProcessingTime());
			outProcessing.println(getClock() + " " + processingTimeString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing cost for Cloud processing cost time Vs the varying factor 
		try(FileWriter fwCost = new FileWriter("MobilitycostClkHealthedge.txt", true);
		BufferedWriter bwCost = new BufferedWriter(fwCost);
		PrintWriter outCost = new PrintWriter(bwCost))
			{
			String averageCloudCostString = Double.toString(finalTotalCostForCloudNodes);
			accumulatedCost+=finalTotalCostForCloudNodes;
			outCost.println(getClock() + " " + averageCloudCostString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
	LogPlacement.debug("\nEnded printing Mobility files. \n ");
	}
	//////////////////////////////////////////////////////////////////////
	public void updateInitialLoads(int allocationSamplingFreq) {
		fogDevicesList = getFogDevices();
		for(FogDevice device : fogDevicesList){		
			if((device.getInitialLoad() - allocationSamplingFreq) > 0)
				device.setInitialLoad(device.getInitialLoad() - allocationSamplingFreq);
			else
				device.setInitialLoad(0);
		}
	}
	public void updateIssueTimes() {
		Calendar issueCalendar = Calendar.getInstance(); 
		for(AppModule module : modules){
			module.setIssueTime(getClock()*1000);
		}
	}
	public void reInitializePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, Application application , int reallocationStrategy , int allocationSamplingFreq , int clock , int randomValue1ForCloudBW , double resultVaryingFactorValue) {
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setSensors(sensors);
		this.setActuators(actuators);
		setCurrentModuleMap(new HashMap<Integer, List<String>>());
		setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
		setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
		for(FogDevice dev : getFogDevices()){
			getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
			getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
			getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}
		
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
		this.cloudId = CloudSim.getEntityId("cloud");
		this.setResultVaryingFactorValue(resultVaryingFactorValue);
		this.randomBW[0]=10;this.randomBW[1]=100;this.randomBW[2]=512;this.randomBW[3]=1024;
		this.setReallocationStrategy(reallocationStrategy);
		this.setClock(clock); 
		this.setRandomValue1ForCloudBW(randomValue1ForCloudBW);
		//balancedAllocation.clear();
		//balancedAllocation2.clear();
		EdgeCloudAllocation.clear();
		totalCloudQueue.clear();
		updateInitialLoads(allocationSamplingFreq);
		updateIssueTimes();
		mapModules();
		calculateEvaluation();
		
		//printMobilityResultsVsClock();
		//if(clock == MaxAndMin.MAX_SIMULATION_TIME/1000-getAllocationSamplingFreq()) {
		if(clock == (MaxAndMin.MAX_SIMULATION_TIME/1000)-((MaxAndMin.MAX_SIMULATION_TIME/1000)%getAllocationSamplingFreq())) {
			printMobilityResults();
		}
	}
public void printMobilityResults() {
		
		//Printing Makespan Vs Clock  
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanHealthedge.txt", true);
		BufferedWriter bwMakespan = new BufferedWriter(fwMakespan);
		PrintWriter outMakespan = new PrintWriter(bwMakespan))
			{
			String makespanString = Double.toString(finalMakespan);
			outMakespan.println(getResultVaryingFactorValue() + " " + makespanString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing EnergyConsumption   
				try(FileWriter fwEnergy = new FileWriter("MobilityEnergyHealthedge.txt", true);
				BufferedWriter bwEnergy = new BufferedWriter(fwEnergy);
				PrintWriter outEneregy = new PrintWriter(bwEnergy))
					{
					String energyString = Double.toString(energyConsumption);
					outEneregy.println(getResultVaryingFactorValue() + " " + energyString);
					   
					} 
				catch (IOException e) {
					    //e.printStackTrace();
					}
		//Printing MaxResponseTime(deadline) miss ratio Vs the varying factor
		/*double numOfModulesMissMaxResTime = 0;
		double totalNumOfModulesWzMaxResponse = 0;
		double missRatio = 0 ;
		double totalDelay = 0 ;
		double AvgDelay = 0;
		for(AppModule resultModule : modules){
			int sinkID = resultModule.getSensors().getGatewayDeviceId();
			FogDevice sm = getFogDeviceById(sinkID)	;
			if(sm.getSourceGateway()!=null){
				if(resultModule.getClassification() != 1) {
					//LogPlacement.debug("task: " + resultModule.getName()+ " issue: " +resultModule.getIssueTime() + " , response: "+ resultModule.getMaxResponseTime() + ", finish: "+ resultModule.getFinishTime() );
					totalNumOfModulesWzMaxResponse++;
					if((resultModule.getIssueTime() + resultModule.getMaxResponseTime() )< resultModule.getFinishTime()) {
						numOfModulesMissMaxResTime++;
						totalDelay+= (  resultModule.getFinishTime() - (resultModule.getIssueTime() + resultModule.getMaxResponseTime()));
					}
				}
			}
		}
		
		missRatio = numOfModulesMissMaxResTime / totalNumOfModulesWzMaxResponse;
		AvgDelay = totalDelay / numOfModulesMissMaxResTime;*/
		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioHealthedge.txt", true);
				BufferedWriter bwMissRatio = new BufferedWriter(fwMissRatio);
				PrintWriter outMissRatio = new PrintWriter(bwMissRatio))
					{
					//String missRatioString = Double.toString(missRatio);
					outMissRatio.println(getResultVaryingFactorValue() + " " + accumulatedMissRatio/((MaxAndMin.MAX_SIMULATION_TIME/1000)/getAllocationSamplingFreq()));
					   
					} 
				catch (IOException e) {
					    //e.printStackTrace();
					}
		//Printing Average delay in missing MaxResponseTime Vs the varying factor   
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayHealthedge.txt", true);
		BufferedWriter bwAvgDelay = new BufferedWriter(fwAvgDelay);
		PrintWriter outAvgDelay = new PrintWriter(bwAvgDelay))
			{
			//String AvgDelayString = Double.toString(AvgDelay);
			if(accumulatedAvgDelay==0) accumulatedAvgDelay=Double.NaN;
			outAvgDelay.println(getResultVaryingFactorValue() + " " + accumulatedAvgDelay/((MaxAndMin.MAX_SIMULATION_TIME/1000)/getAllocationSamplingFreq()));
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		//Printing processing time Vs the varying factor   
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeHealthedge.txt", true);
		BufferedWriter bwProcessing = new BufferedWriter(fwProcessing);
		PrintWriter outProcessing = new PrintWriter(bwProcessing))
			{
			//String processingTimeString = Double.toString(Calendar.getInstance().getTimeInMillis() - getStartCalendar().getTimeInMillis());
			outProcessing.println(getResultVaryingFactorValue() + " " + getAccumulatedProcessingTime()/((MaxAndMin.MAX_SIMULATION_TIME/1000)/getAllocationSamplingFreq()));
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing cost for Cloud processing cost time Vs the varying factor 
		try(FileWriter fwCost = new FileWriter("MobilitycostHealthedge.txt", true);
		BufferedWriter bwCost = new BufferedWriter(fwCost);
		PrintWriter outCost = new PrintWriter(bwCost))
			{
			//String averageCloudCostString = Double.toString(finalTotalCostForCloudNodes);
			outCost.println(getResultVaryingFactorValue() + " " + accumulatedCost);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing newtwork load Vs the varying factor 
		try(FileWriter fwNetload = new FileWriter("MobilitynetworkloadHealthedge.txt", true);
		BufferedWriter fwnetload = new BufferedWriter(fwNetload);
		PrintWriter outNetLoad = new PrintWriter(fwnetload))
			{
			//String averageCloudCostString = Double.toString(finalTotalCostForCloudNodes);
			outNetLoad.println(getResultVaryingFactorValue() + " " + getNetworkLoad()/8); // we divide by 8 because it is saved here in Mbit but we'll print in MB
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}		
		
		
	LogPlacement.debug("\nEnded printing Mobility files. \n ");
	}
	
	//public static List<FogDevice> getGateways() {
	public List<FogDevice> getGateways() {
		List<FogDevice> GatewaysList =new ArrayList<>();
	
		for(FogDevice gateway: getFogDevices()){
			if(gateway.getName().startsWith("g"))
				GatewaysList.add(gateway);
		}
		return GatewaysList;
	}
	protected List<FogDevice> getCloudDevices(){
		List<FogDevice> cloudDevices = new ArrayList<FogDevice>();
		for(FogDevice dev : getFogDevices()){
			if(dev.getName().startsWith("c"))
				cloudDevices.add(dev);
		}
		return cloudDevices;
	}
	
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}
	
	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
	}
	
	public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
		return currentModuleLoadMap;
	}

	public void setCurrentModuleLoadMap(
			Map<Integer, Map<String, Double>> currentModuleLoadMap) {
		this.currentModuleLoadMap = currentModuleLoadMap;
	}

	public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
		return currentModuleInstanceNum;
	}

	public void setCurrentModuleInstanceNum(
			Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
		this.currentModuleInstanceNum = currentModuleInstanceNum;
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}
	public Calendar getStartCalendar() {
		return startCalendar;
	}

	public void setStartCalendar(Calendar startCalendar) {
		this.startCalendar = startCalendar;
	}
	public double getResultVaryingFactorValue() {
		return resultVaryingFactorValue;
	}

	public void setResultVaryingFactorValue(double resultVaryingFactorValue) {
		this.resultVaryingFactorValue = resultVaryingFactorValue;
	}
	public int getReallocationStrategy() {
		return reallocationStrategy;
	}

	public void setReallocationStrategy(int reallocationStrategy) {
		this.reallocationStrategy = reallocationStrategy;
	}
	public int getClock() {
		return clock;
	}

	public void setClock(int clock) {
		this.clock = clock;
	}

	public int getAllocationSamplingFreq() {
		return allocationSamplingFreq;
	}

	public void setAllocationSamplingFreq(int allocationSamplingFreq) {
		this.allocationSamplingFreq = allocationSamplingFreq;
	}

	public int getRandomValue1ForCloudBW() {
		return randomValue1ForCloudBW;
	}

	public void setRandomValue1ForCloudBW(int randomValue1ForCloudBW) {
		this.randomValue1ForCloudBW = randomValue1ForCloudBW;
	}

	public double getRoundProcessingTime() {
		return roundProcessingTime;
	}

	public void setRoundProcessingTime(double roundProcessingTime) {
		this.roundProcessingTime = roundProcessingTime;
	}

	public double getAccumulatedProcessingTime() {
		return accumulatedProcessingTime;
	}

	public void setAccumulatedProcessingTime(double accumulatedProcessingTime) {
		this.accumulatedProcessingTime = accumulatedProcessingTime;
	}

	public double getL() {
		return L;
	}

	public void setL(double l) {
		L = l;
	}
	public double getNetworkLoad() {
		return networkLoad;
	}

	public void setNetworkLoad(double networkLoad) {
		this.networkLoad = networkLoad;
	}
	public double getEnergyConsumption() {
		return energyConsumption;
	}

	public void setEnergyConsumption(double energyConsumption) {
		this.energyConsumption = energyConsumption;
	}
}
