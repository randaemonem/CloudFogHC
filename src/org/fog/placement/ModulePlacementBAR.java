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
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudfogHC.AHP;
import org.cloudfogHC.LogMobile;
import org.cloudfogHC.LogPlacement;
import org.cloudfogHC.LogResult;
import org.cloudfogHC.SortByWeight;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.mobility.HandOff;
import org.mobility.ImplMobility;
import org.mobility.MaxAndMin;
import org.mobility.Policies;

/**
 * In (Map modules) method:
 * Implementation of BAR (Balance Reduce) heuristic task allocation algorithm. Ref:[BAR: An Efficient Data Locality Driven Task
Scheduling Algorithm for Cloud Computing]. AHP (Analytic hyrarichy process) scheduling was first implemented. Ref[The analytic hierarchy process: Task scheduling and resource allocation in
cloud computing environment]. Modifications were made to fit in the healthcare application cloud-Fog architecture proposed. 

 * @author Randa Mohammed
 *
 */


public class ModulePlacementBAR extends ModulePlacement{
	
	Calendar startCalendar ;
	protected ModuleMapping moduleMapping;
	private List<Sensor> sensors;
	private List<Actuator> actuators;
	private int cloudId;
	List<AppModule> modules;
	List<FogDevice> fogDevicesList;  
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
	double sizeMissedDataMobility;
	double energyConsumption;
	
	/**
	 * Stores the current mapping of application modules to fog devices 
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;
	
	public ModulePlacementBAR(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, Application application , int reallocationStrategy , int allocationSamplingFreq , double resultVaryingFactorValue) {
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
		this.randomBW[0]=10;this.randomBW[1]=100;this.randomBW[2]=512;this.randomBW[3]=1024;
		this.setReallocationStrategy(reallocationStrategy);
		this.setAllocationSamplingFreq(allocationSamplingFreq);
		updateInitialLoads(allocationSamplingFreq);
		this.setClock(0);
		this.setNetworkLoad(0);
		this.setSizeMissedDataMobility(0);
		this.setEnergyConsumption(0);
		//computeModuleInstanceCounts();
		mapModules();
		calculateEvaluation();
		printMobilityResultsVsClock();
	}
	


	private void computeModuleInstanceCounts(){
		FogDevice cloud = getDeviceById(CloudSim.getEntityId("cloud"));
		getModuleInstanceCountMap().put(cloud.getId(), new HashMap<String, Integer>());
		
		for(Sensor sensor : getSensors()){
			String sensorType = sensor.getSensorName();
			if(!getModuleInstanceCountMap().get(cloud.getId()).containsKey(sensorType))
				getModuleInstanceCountMap().get(cloud.getId()).put(sensorType, 0);
			getModuleInstanceCountMap().get(cloud.getId()).put(sensorType, getModuleInstanceCountMap().get(cloud.getId()).get(sensorType)+1);
		}
		
		for(Actuator actuator : getActuators()){
			String actuatorType = actuator.getActuatorType();
			if(!getModuleInstanceCountMap().get(cloud.getId()).containsKey(actuatorType))
				getModuleInstanceCountMap().get(cloud.getId()).put(actuatorType, 0);
			getModuleInstanceCountMap().get(cloud.getId()).put(actuatorType, getModuleInstanceCountMap().get(cloud.getId()).get(actuatorType)+1);
		}
		
		while(!isModuleInstanceCalculationComplete()){
			for(AppModule module : getApplication().getModules()){
				int maxInstances = 0;
				for(AppEdge edge : getApplication().getEdges()){
					if(!getModuleInstanceCountMap().get(cloudId).containsKey(edge.getSource()))
						continue;
					if(edge.getDestination().equals(module.getName()) && edge.getDirection()==Tuple.UP){
						maxInstances = Math.max(maxInstances, getModuleInstanceCountMap().get(cloudId).get(edge.getSource()));
					}
				}
				getModuleInstanceCountMap().get(cloudId).put(module.getName(), maxInstances);
			}
		}
		LogPlacement.debug(getModuleInstanceCountMap().toString());
	}

	private boolean isModuleInstanceCalculationComplete() {
		for(AppModule module : getApplication().getModules()){
			if(!getModuleInstanceCountMap().get(cloudId).containsKey(module.getName()))
				return false;
		}
		return true;
	}

	@Override
	protected void mapModules() {
		setStartCalendar(Calendar.getInstance());

		//------------------------------------------------------------
		//AHP ranking/ scheduling
		LogPlacement.debug("====================================================");
		LogPlacement.debug("			Weighted Sum Model ranking                             ");
		LogPlacement.debug("====================================================");
		//AHP();
		WSM();		
        // Call the BAR allocation algorithm by calling the two methods consecutively 
        LogPlacement.debug("====================================================");
		LogPlacement.debug("			BAR Allocation 			                 ");
		LogPlacement.debug("====================================================");
        balancedAllocation();
        reduceMakespan();
        LogPlacement.debug("\nEnded Module placement. \n ");
        LogPlacement.debug("Total Allocation Algorithm Time: "+ (Calendar.getInstance().getTimeInMillis() - startCalendar.getTimeInMillis()) + " milliseconds");
        LogPlacement.debug("__________________________________________________________________________________");
        
        //printResult();
      
        
	}
	
	public void WSM() {
		// Get all the tasks in "modules" list and set the total number of tasks in "nrVx"
		modules = getApplication().getModules(); 
		int numOfModules = modules.size();
		double weightedSumScore[] = new double [numOfModules];
		
		LogPlacement.debug("Weights: ");
		//LogPlacement.debug("Weights: ");
		// Start calculating weights and print them
		// assuming weight of criterion c1(classification) = weight of criterion c2(inverse of maxResponseTime) = 0.5
		for( int i = 0 ; i < modules.size(); i++) {
			weightedSumScore[i]= (modules.get(i).getClassification() * 0.5) + (1/(modules.get(i).getMaxResponseTime()) * 0.5);
			modules.get(i).setWeight( weightedSumScore[i]);
			LogPlacement.debug(modules.get(i).getName() + ": " +  weightedSumScore[i] );
		}
		
        // Print the list of tasks unsorted
		LogPlacement.debug("\nList before sorting:");
        for(AppModule module : modules){
        	LogPlacement.debug("\t"+module.getName() + module.getWeight()) ;
         
        }
        
        // Sort the tasks according to the weights
        Collections.sort(modules, new SortByWeight());
        
        // Print the list of tasks after sorting 
        LogPlacement.debug("\nList after sorting:");
        for(AppModule module : modules){
        	LogPlacement.debug("\t"+module.getName()+ module.getWeight()) ;
        }
	}
	public void AHP() {
		// Get all the tasks in "modules" list and set the total number of tasks in "nrVx"
				modules = getApplication().getModules(); 
				int nrVx = modules.size();
				String labels[] = new String [modules.size()];
				for( int i = 0 ; i < modules.size(); i++)
					labels[i]= modules.get(i).getName() + i;
				
				// Instantiate new AHP instance (matrix) from the AHP class
		        AHP ahp = new AHP(nrVx);
		        //LogPlacement.debug(ahp);
		        int d = ahp.getNrOfPairwiseComparisons();
		        
		        // Declare and initialize the comparison array (relative importance) for comparing the tasks based on two factors: task classification and response time 
		        // Initialization is done according to the table defined in my paper. Ref [A Cloud-Fog based Architecture for IoT Applications Dedicated to Healthcare] 
		        double compArray[] = ahp.getPairwiseComparisonArray();
		        int x = 0;
				for (int i = 0 ; i < modules.size()-1; i++) {
					AppModule module1 = modules.get(i);
			        for (int j = i+1 ; j < modules.size(); j++) {
			        	AppModule module2 = modules.get(j);
			        	if((module1.getClassification() > module2.getClassification())) {
			        		if(module1.getClassification()	-	module2.getClassification() == 2 ) {compArray[x] = 9.0;}
			        		else if(module1.getClassification()	-	module2.getClassification() == 1 ) {
			        			if ((module1.getMaxResponseTime() < module2.getMaxResponseTime()) || (module1.getMaxResponseTime() == module2.getMaxResponseTime())) {
				        			compArray[x] = 7.0;
				        		}
			        			else
				        			compArray[x] = 5.0;
			        		}
			        		
			        	}
			        	
			        	else if ((module1.getClassification() < module2.getClassification() )) {
			        		if(module2.getClassification()	-	module1.getClassification() == 2 ) {compArray[x] = 1.0	/  9.0;}
			        		else if(module2.getClassification()	-	module1.getClassification() == 1 ) {
			        			if ((module2.getMaxResponseTime() < module1.getMaxResponseTime()) || (module2.getMaxResponseTime() == module1.getMaxResponseTime())) {
				        			compArray[x] = 1.0	/	7.0;
				        		}
			        			else
				        			compArray[x] = 1.0	/	5.0;
			        		}
			        	}
			        	else if ((module1.getClassification() == module2.getClassification())) {
			        		if (module1.getMaxResponseTime() < module2.getMaxResponseTime())
			        			compArray[x] = 3.0;
			        		else if (module1.getMaxResponseTime() == module2.getMaxResponseTime())
			        			compArray[x] = 1.0;
			        		else 
			        			compArray[x] = 1.0 / 3.0;
			        	}	        	
			        	  x++;
			        }	      
				}
				
				// Perform the matrix operations to get the weights of the tasks
				ahp.setPairwiseComparisonArray(compArray);
				
				// Print the relative importance
				for (int k = 0; k < ahp.getNrOfPairwiseComparisons(); k++) {
		            System.out.print("Importance of " + labels[ahp.getIndicesForPairwiseComparison(k)[0]] + " compared to ");
		            System.out.print(labels[ahp.getIndicesForPairwiseComparison(k)[1]] + "= ");
		            LogPlacement.debug(ahp.getPairwiseComparisonArray()[k]);
		        }
		        
				// Print the matrix and its factors
				//LogPlacement.debug("\n" + ahp + "\n");
		        //LogPlacement.debug("Consistency Index: " + ahp.getConsistencyIndex());
		        //LogPlacement.debug("Consistency Ratio: " + ahp.getConsistencyRatio() + "%");
				
				// Print the weights of the tasks 
		       
		        LogPlacement.debug("Weights: ");
		        for (int k=0; k<ahp.getWeights().length; k++) {
		            LogPlacement.debug(labels[k] + ": " + ahp.getWeights()[k] * 100);
		            modules.get(k).setWeight( ahp.getWeights()[k] * 100);
		        }
		        
		        // Print the list of tasks unsorted
		        LogPlacement.debug("\nList before sorting:");
		        for(AppModule module : modules){
		        	
		            LogPlacement.debug("\t"+module.getName() );
		        }
		        
		        // Sort the tasks according to the weights
		        Collections.sort(modules, new SortByWeight());
		        
		        // Print the list of tasks after sorting 
		        LogPlacement.debug("\nList after sorting:");
		        for(AppModule module : modules){
		            LogPlacement.debug("\t"+module.getName() );
		        }
	}
	public void balancedAllocation() {
		
		//------------------------------------------------------------------------------
		// Start the Balanced phase
		LogPlacement.debug("		1- Balanced                  ");
		
		// n: number of Servers S
		// m: number of Tasks T
		// e: edges between s and t
		// G: (S U T , E)
		// S(t): preferred server for task t >=1 for all		
		double load_s_t = 0;
		fogDevicesList = getGateways(); //getFogDevices(); //getGateways();
		for(AppModule module : modules){
			HashMap< Double , FogDevice > load_S_t = new HashMap< Double , FogDevice> ();	//load of all servers if task t is allocated
			HashMap<  FogDevice , Double > lastCostL = new HashMap<  FogDevice, Double> ();	//load of all servers if task t is allocated
			LogPlacement.debug("\n");
			
			int sinkID = module.getSensors().getGatewayDeviceId();
			FogDevice sm = getFogDeviceById(sinkID)	;
			if(sm.getSourceGateway()!=null){
				for(FogDevice device : fogDevicesList){		
					
					double load_s = 0 ; 		//load of server s
					//------------------------------------------------------------------
					// Check locality or partial locality
					
					// 1 - get the sink node responsible for this task and its current gateway fog node
					//int sinkID = module.getSensors().getGatewayDeviceId();
					//int gatewayID = getDeviceById(sinkID).getParentId();			
					int gatewayID = getFogDeviceById(sinkID).getParentId();
					
					// 2- check partial locality (semi locality) if handoff is this sink performed a handoff from last allocation
					if(getDeviceById(sinkID).isHandoffHandled() == false) {	
						// if handoff was performed, get the old gateway where the first part of data resides
						int oldGatewayID =  getDeviceById(sinkID).getOldSourceGateway().getId();
						
						//Calculate partial local cost for all nodes
						if(	 (gatewayID == device.getId())  ||  (oldGatewayID == device.getId())) {
							
							//Calculating partial local cost if processing here on sinknode or processing on sourceGateway/parent node or processing on old gateway node 				
							double partialLocalLastCost;			// local cost for task/module
							double fogBandWidth = 1024;	
							double dataToBeTransfered,communicationTime, timeDifference , computationTime;
							int timeFromLastAllocTillHandoff;
							
							double dataSize = module.getSize();
							double dataSizeSentInSampFreq = dataSize / getAllocationSamplingFreq();
							
							if( (gatewayID == device.getId())) {
								 timeFromLastAllocTillHandoff = ((getDeviceById(sinkID).getHandOffClock())- (getClock()-10)) ; // converted from ms to sec 
								 dataToBeTransfered = timeFromLastAllocTillHandoff * dataSizeSentInSampFreq; // Part 1 of data was at oldGateway during time from last allocation till handoff clock 

							}
							else {
								timeFromLastAllocTillHandoff = (getClock()- (getDeviceById(sinkID).getHandOffClock())); // converted from ms to sec
								dataToBeTransfered = timeFromLastAllocTillHandoff * dataSizeSentInSampFreq;	// part 2 of data resides n sink or current gateway during time from handoff clock till current clock 
							}
							module.setPartialDataSize(dataToBeTransfered);
							/////////////////////////////////////////////////////////
							//calculate size of missed data due to mobility when healthedge is not handling them
							
							setSizeMissedDataMobility(getSizeMissedDataMobility()+dataToBeTransfered);
							
							/////////////////////////////////////////////////////
							computationTime = module.getMips()	/ device.getMips();
							communicationTime = dataToBeTransfered/fogBandWidth;
							timeDifference = communicationTime - device.getInitialLoad();
							partialLocalLastCost = computationTime +  ( timeDifference > 0 ? timeDifference : 0);
							load_s_t = partialLocalLastCost;			
							lastCostL.put(device , load_s_t);
							load_s = (computationTime) +  Math.max(device.getInitialLoad(),  communicationTime);
							load_S_t.put(load_s , device);
							LogPlacement.debug("Load for device " + device.getName() + " after allocating task " + module.getName() +  " = " + load_s);
						}
					}
					else {
						// 
						if(		 (gatewayID == device.getId())  ) {
							//---------------------------------------------------------------
							//Calculating local cost 				
							double localCost = 0;			// local cost for task/module						
							localCost = module.getMips()	/ device.getMips();
							load_s_t = localCost;	
							lastCostL.put(device , load_s_t);
							load_s = device.getInitialLoad() + load_s_t;
							load_S_t.put(load_s , device);
							LogPlacement.debug("Load for device " + device.getName() + " after allocating task " + module.getName() +  " = " + load_s);
							
						}
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
						   double chosenlastCost = lastCostL.get(chosenServerToAllocateT);
						   module.setLastCost(chosenlastCost);
						   LogPlacement.debug( module.getName() + " current cost = " +  chosenlastCost);
						   module.setFinishTime( min *1000+(double)getClock());
							
						   // -------------------------------------------
						   // allocate the task/module to that server/fogdevice
						   balancedAllocation.put(module, chosenServerToAllocateT);
						   LogPlacement.debug("-> Allocating " +  module.getName() +  " to " + chosenServerToAllocateT.getName() );
						  
						   //-------------------------------------------
						   // add the task to the list of tasks allocated to that server				
						   //double min = load_S_t.keySet().iterator().next();
						   boolean firstTaskToBeAllocated = true;
						   List<AppModule> modulesList = null;
							for( FogDevice keyDevice : balancedAllocation2.keySet()) {
								if(keyDevice.getName().equals(chosenServerToAllocateT.getName())){
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
								 balancedAllocation2.put(chosenServerToAllocateT, modulesList);
								
							}
							
							LogPlacement.debug("Device " +  chosenServerToAllocateT.getName() + " has the following tasks: (" );
							for(AppModule m : modulesList) LogPlacement.debug(m.getName() + " , ");
							LogPlacement.debug(")");
							chosenServerToAllocateT.setInitialLoad(min);
							
							// partial locality handeled so :
							sinkID = module.getSensors().getGatewayDeviceId();			
							getFogDeviceById(sinkID).setHandoffHandled(true);
							
					   }
			
				LogPlacement.debug("\n");
				for(Map.Entry<FogDevice, List<AppModule>> allocation: balancedAllocation2.entrySet()) {
					LogPlacement.debug("FogDevice " + allocation.getKey().getName() + " has the following tasks allocated - With Final load = " + allocation.getKey().getInitialLoad());
					LogPlacement.debug("-------------------------------------------------------");
					for(AppModule app : allocation.getValue())
						LogPlacement.debug(app.getName() );	
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void reduceMakespan() {
		HashMap< FogDevice, List<AppModule> > partialAllocation =new HashMap< FogDevice, List<AppModule>> ();	
		HashMap< FogDevice, List<AppModule> > totalAllocation_pre = new HashMap< FogDevice, List<AppModule>> ();
		HashMap<  FogDevice, List<AppModule> > totalAllocation = new HashMap< FogDevice, List<AppModule>> ();
		
		// Deep copy (clone performs shallow copy)
		for (Map.Entry< FogDevice, List<AppModule> >  entry : balancedAllocation2.entrySet()){
	    	partialAllocation.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
	    }
	    for (Map.Entry< FogDevice, List<AppModule> >  entry : balancedAllocation2.entrySet()){
	    	totalAllocation_pre.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
	    }
	    double mExpected = 0;
	    double oldmExpected = 0;
	    LogPlacement.debug("\n__________________________________________________________________________________");
		LogPlacement.debug("			2- Reduce(Reallocation)                  ");
		boolean endReduce = false;
		while(!endReduce) {
			
			//--------------------------------------------------------------
			//Save loads of totalAllocation_pre
			List<Double> preAllocationLoads = new ArrayList<Double>();
			for(FogDevice device : fogDevicesList){
				double load = device.getInitialLoad();
				preAllocationLoads.add(load);
			}
			//---------------------------------------------------------------
			// Old makespan / MAx load server
			//double makeSpanPre = fogDevicesList.get(0).getInitialLoad();
			double makeSpanPre = 0;
			for(FogDevice device : partialAllocation.keySet() ) {
			//for(FogDevice device : fogDevicesList){
				double load = device.getInitialLoad();
				//preAllocationLoads.add(load);
				if(load > makeSpanPre) {
					makeSpanPre = device.getInitialLoad();
				}		
			}
			LogPlacement.debug("Makespan  =  " + makeSpanPre);
			
			// Choose random task to remove and reallocate
			for(FogDevice fdevice : fogDevicesList){
				if(fdevice.getInitialLoad() == makeSpanPre) {
					//LogPlacement.debug("-> Choosing device " + fdevice.getName() + " to reallocate its tasks because it has max load");
					LogPlacement.debug("-> Choosing device " + fdevice.getName() + " to reallocate its tasks");
					List<AppModule> modulesList = partialAllocation.get(fdevice);
					
					LogPlacement.debug("\nList of tasks on device:" );
					for(AppModule app : modulesList)
						LogPlacement.debug(app.getName()  + ", ");						
					
					AppModule module = null;
					if(reallocationStrategy == org.mobility.Policies.RANDOM_STRATEGY) {
						//-------------------------------------
						//Strategy 1 : Choose random task to be reallocated			
						Random r = new Random();
						int randomValue = r.nextInt((modulesList.size()-1) - 0 + 1) + 0;
						module = modulesList.get(randomValue);
						LogPlacement.debug("\n-> Choosing task " + module.getName() + " to reallocate ");			
						//-----------------------------------
					 	// Update finish time of all tasks following this task
								double finishTimeOfRemoved = module.getFinishTime();
								for(AppModule mymodule : modulesList) {
									if(mymodule.getFinishTime() > finishTimeOfRemoved) 
										mymodule.setFinishTime((mymodule.getFinishTime() - (module.getLastCost()*1000)));
								}
						// Remove the task from that server  
						modulesList.remove(randomValue);
					}
					else if (reallocationStrategy == org.mobility.Policies.CLASSIFICATION_STRATEGY) {
						//--------------------------------------------------------
						//Strategy 2 : Choose task with higher classification to be reallocated
						
						if(modulesList.size() == 0) {
							endReduce = true;
						}
						if(endReduce == false) {
							int lowestClassification = modulesList.get(0).getClassification();
							for(AppModule module1 : modulesList) {
								if(module1.getClassification() < lowestClassification) 
									lowestClassification = module1.getClassification();
							}
							boolean found = false;
							for(AppModule module2 : modulesList) {
								if(module2.getClassification() == lowestClassification) {
									LogPlacement.debug("\n-> Choosing task " + module2.getName() + " to reallocate ");
									module =module2;
									// Update finish time of all tasks following this task
										double finishTimeOfRemoved = module.getFinishTime();
										for(AppModule mymodule : modulesList) {
											if(mymodule.getFinishTime() > finishTimeOfRemoved) 
												mymodule.setFinishTime((mymodule.getFinishTime() - (module.getLastCost()*1000)));
										}
									//----------------------------------
									// Remove the task from that server  
									modulesList.remove(module2);
									found = true;
									break;
								}
								if(found == true) break;
							}
						}
					}
					
					//----------------------------------------
					//Strategy 3: Max Response 
					//choose the task with max response error where response error is for the task whose max response time is not met (response error = meaning finish time greater that issue time+max response time)
					else if (reallocationStrategy == Policies.MAXRESPONSE_STRATEGY) {
						double maxResponseError = -1 ;//(modulesList.get(0).getFinishTime()) - (modulesList.get(0).getIssueTime() + modulesList.get(0).getMaxResponseTime() );
						for(AppModule module1 : modulesList) {
							if(module1.getClassification() != 1) {
								//LogPlacement.debug("task: " + module1.getName()+ " issue: " +module1.getIssueTime() + " , response: "+ module1.getMaxResponseTime() + ", finish: "+ module1.getFinishTime() );
							if((module1.getIssueTime() + module1.getMaxResponseTime() )< module1.getFinishTime()) {
								double responseError = (module1.getFinishTime()) - (module1.getIssueTime() + module1.getMaxResponseTime() );
								if(responseError > maxResponseError) maxResponseError = responseError;
							}
							}
						}
						if(maxResponseError > 0 ) {
						boolean found = false;
						for(AppModule module2 : modulesList) {
							double responseError = (module2.getFinishTime()) - (module2.getIssueTime() + module2.getMaxResponseTime() );
							if( responseError == maxResponseError) {
								LogPlacement.debug("\n-> Choosing task " + module2.getName() +  module2.getId() + " to reallocate with delay (maxResponse error) =" + responseError);
								module = module2;
								// Update finish time of all tasks following this task
								double finishTimeOfRemoved = module.getFinishTime();
								for(AppModule mymodule : modulesList) {
									if(mymodule.getFinishTime() > finishTimeOfRemoved) 
										mymodule.setFinishTime((mymodule.getFinishTime() - (module.getLastCost()*1000)));
								}
								//----------------------------------
								// Remove the task from that server  
								modulesList.remove(module2);
								found = true;
								break;
							}
							if(found == true) break;
						}
					
						}
						else{
							LogPlacement.debug("\nNo delay in any max response time. ");
							endReduce = true;
							
						}
					}
					//----------------------------------------
					//Strategy 4: Mixed 
					//choose the task with max response error where response error is for the task whose max response time is not met (response error = meaning finish time greater that issue time+max response time)
					else if (reallocationStrategy == Policies.Mixed) {
						double maxResponseError = -1 ;//(modulesList.get(0).getFinishTime()) - (modulesList.get(0).getIssueTime() + modulesList.get(0).getMaxResponseTime() );
						for(AppModule module1 : modulesList) {
							if(module1.getClassification() != 1) {
								//LogPlacement.debug("task: " + module1.getName()+ " issue: " +module1.getIssueTime() + " , response: "+ module1.getMaxResponseTime() + ", finish: "+ module1.getFinishTime() );
							if((module1.getIssueTime() + module1.getMaxResponseTime() )< module1.getFinishTime()) {
								double responseError = (module1.getFinishTime()) - (module1.getIssueTime() + module1.getMaxResponseTime() );
								if(responseError > maxResponseError) maxResponseError = responseError;
							}
							}
						}
						if(maxResponseError > 0 ) {
						boolean found = false;
						for(AppModule module2 : modulesList) {
							double responseError = (module2.getFinishTime()) - (module2.getIssueTime() + module2.getMaxResponseTime() );
							if( responseError == maxResponseError) {
								LogPlacement.debug("\n-> Choosing task " + module2.getName() +  module2.getId() + " to reallocate with delay (maxResponse error) =" + responseError);
								module = module2;
								// Update finish time of all tasks following this task
								double finishTimeOfRemoved = module.getFinishTime();
								for(AppModule mymodule : modulesList) {
									if(mymodule.getFinishTime() > finishTimeOfRemoved) 
										mymodule.setFinishTime( (mymodule.getFinishTime() - (module.getLastCost()*1000)));
								}
								//----------------------------------
								// Remove the task from that server  
								modulesList.remove(module2);
								found = true;
								break;
							}
							if(found == true) break;
						}
					
						}
						else{
							double largestMaxResponse = modulesList.get(0).getMaxResponseTime();
							for(AppModule module1 : modulesList) {
								if(module1.getMaxResponseTime() > largestMaxResponse) 
									largestMaxResponse = module1.getMaxResponseTime();
							}
							boolean found = false;
							for(AppModule module2 : modulesList) {
								if(module2.getMaxResponseTime() == largestMaxResponse) {
									LogPlacement.debug("\n-> Choosing task " + module2.getName() + " to reallocate ");
									module =module2;
									// Update finish time of all tasks following this task
										double finishTimeOfRemoved = module.getFinishTime();
										for(AppModule mymodule : modulesList) {
											if(mymodule.getFinishTime() > finishTimeOfRemoved) 
												mymodule.setFinishTime( (mymodule.getFinishTime() - (module.getLastCost()*1000)));
										}
									//----------------------------------
									// Remove the task from that server  
									modulesList.remove(module2);
									found = true;
									break;
								}
								if(found == true) break;
							}
							
						}
					}
					LogPlacement.debug("endReduce = " + endReduce);
					if(endReduce ==  false) {					
					//------------------------------------------------------
					// Finished strategy		
					if(modulesList.size() != 0)
						partialAllocation.put(fdevice,modulesList);
					else {
						partialAllocation.remove(fdevice);
						fdevice.setInitialLoad(0);
					}
					LogPlacement.debug("\nList of fogs in allocation" );
					for( FogDevice keyDevice : partialAllocation.keySet()) {
						LogPlacement.debug(keyDevice.getName());
					}
					// Subtracting the last cost for allocating this module on this device for confirming the removal of the task
					String msg = "local cost = " + module.getLastCost();
					LogPlacement.debug(msg );
					LogPlacement.debug("load before removing = " +fdevice.getInitialLoad());
					//if(fdevice.getInitialLoad()-module.getLastCost() <= 0) {
					if(fdevice.getInitialLoad()-module.getLastCost() < 0) {
						fdevice.setInitialLoad(0);
					}
					else
						fdevice.setInitialLoad(fdevice.getInitialLoad()-module.getLastCost());
					
					/*for(FogDevice allocationDevice : partialAllocation.keySet() ) {
						if(allocationDevice.getId() == fdevice.getId()) {
							if(allocationDevice.getInitialLoad()-module.getLastCost() < 0)
								allocationDevice.setInitialLoad(0);
							else
								allocationDevice.setInitialLoad(allocationDevice.getInitialLoad()-module.getLastCost());
						}
					}*/
					LogPlacement.debug("load after removing = " +fdevice.getInitialLoad());
					
					
					LogPlacement.debug("\nList of tasks after removing random task (partial allocation):" );
					for(AppModule app : modulesList)
						LogPlacement.debug(app.getName() + ", ");	
					LogPlacement.debug("\n\nCurrent loads:");
					for(FogDevice f : fogDevicesList){LogPlacement.debug( f.getName() + " : " + f.getInitialLoad());}
	
					//--------------------------------------------
					// Calculated makeSpan after removing the task
					mExpected = fogDevicesList.get(0).getInitialLoad();
					for(FogDevice fdevice2 : fogDevicesList){
						if(fdevice2.getInitialLoad() > mExpected) {
							mExpected = fdevice2.getInitialLoad();
						}		
					}
					LogPlacement.debug("\nFinal Expected makespan  =  " + mExpected);
									
					//-----------------------------------------------
					// Re-allocation
					//HashMap< Double , FogDevice > load_S_t = new HashMap< Double , FogDevice> ();	//new allocation map
					boolean reallocated = false;
					for(FogDevice device : fogDevicesList){		
						if(device.getName().startsWith("c") || device.getName().startsWith("g") ) {
							//----------------------------------------------------------------
							// Calculating remote cost
							double remoteCost, lastCost = 0;		// load/ time to process task t on server s
							double bandWidth;
							double communicationTime, timeDifference , computationTime;
							
							// From paper " A cost...", we'll specify cloudBW = 10-1024,  fogBW = 1024
							if(device.getName().startsWith("c")) bandWidth=device.getUplinkBandwidth();//bandWidth = randomBW[getRandomValue1ForCloudBW()];//512;
							else 	bandWidth = 1024;						
							computationTime = module.getMips()	/ device.getMips();
							communicationTime = module.getSize() / bandWidth;
							timeDifference = communicationTime - device.getInitialLoad();
							lastCost = computationTime +  ( timeDifference > 0 ? timeDifference : 0);
							remoteCost =  (computationTime)+ Math.max(device.getInitialLoad(), communicationTime);
							
							double sLoad = device.getInitialLoad() ;
							if(sLoad < mExpected-lastCost) {
								LogPlacement.debug("->Choosing device " + device.getName() + " for reallocation.");
								LogPlacement.debug("Load for device " + device.getName() + " after allocating task " + module.getName() + " = " + remoteCost);
								boolean previouslyHadAllocations = false;
								for( FogDevice keyDevice : partialAllocation.keySet()) {
									if(keyDevice.getName().equals(device.getName())){
										LogPlacement.debug(" device name: " + device.getName());
										 List<AppModule> modulesList2 = partialAllocation.get(keyDevice);
										 modulesList2.add(module);
										 partialAllocation.put(keyDevice, modulesList2);
										 keyDevice.setInitialLoad(remoteCost);
										 module.setLastCost(lastCost);
										 module.setFinishTime((remoteCost)*1000+(double)getClock());
										 LogPlacement.debug("\nList of tasks on this device now: ");
										 //System.out.print("\nList of tasks on this device now: ");
										 for(AppModule app : modulesList2)
												LogPlacement.debug(app.getName()  + ", ");	
										 previouslyHadAllocations = true;
									}
									if(previouslyHadAllocations == true) break;
								}
								
								if( previouslyHadAllocations == false) {
										 List<AppModule> newModulesList = new ArrayList<AppModule>();
										 newModulesList.add(module);
										 partialAllocation.put(device, newModulesList);
										 module.setLastCost(lastCost);
										 device.setInitialLoad(remoteCost);
										 module.setFinishTime((remoteCost)*1000+(double)getClock());
										 for(AppModule app : newModulesList)
												LogPlacement.debug(app.getName()+  ", ");									
									}
								reallocated = true;
							}
							if(reallocated == true) break;
						}
					}
					
					double remoteCost2 = 0, lastCost2 = 0;		// load/ time to process task t on server s
					if( reallocated == false) { // allocate remote task to the server with minimum load
					LogPlacement.debug("\nThe remote task is not reallocated to meet expected load. Will allocate it to server with min load.");
					
					HashMap< Double , FogDevice > load_S_t = new HashMap< Double , FogDevice> ();	//load of all servers if task t is allocated
					HashMap<  FogDevice , Double > lastCostL = new HashMap<  FogDevice, Double> ();	//load of all servers if task t is allocated
					for(FogDevice device2 : fogDevicesList){	
						if(device2.getName().startsWith("c") || device2.getName().startsWith("g") ) {
							//----------------------------------------------------------------
							// Calculating remote cost
							double bandWidth2;
							double communicationTime, timeDifference, computationTime;
							
							// From paper " A cost...", we'll specify cloudBW = 10-1024,  fogBW = 1024
							if(device2.getName().startsWith("c")) bandWidth2 = device2.getUplinkBandwidth();//randomBW[randomValue1];//;//512;
							else 	bandWidth2 = 1024;
							
							computationTime = module.getMips()	/ device2.getMips();
							communicationTime = module.getSize() / bandWidth2;
							timeDifference = communicationTime - device2.getInitialLoad();
							lastCost2 = computationTime +  ( timeDifference > 0 ? timeDifference : 0);
							remoteCost2 =  (computationTime)+ Math.max(device2.getInitialLoad(), communicationTime);
	
							LogPlacement.debug("\nLoad for device " + device2.getName() + " after allocating task " + module.getName() + " = " + remoteCost2);
							lastCostL.put(device2, lastCost2);
							load_S_t.put(remoteCost2 , device2);
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
							   LogPlacement.debug("->Choosing "+ chosenServerToAllocateT.getName() + " to allocate the task to.");
							   
							   // -------------------------------------------
							   // allocate the task/module to that server/fogdevice
							   // balancedAllocation.put(module, chosenServerToAllocateT);

							   //-------------------------------------------
							   // add the task to the list of tasks allocated to that server				
							   //double min = load_S_t.keySet().iterator().next();
							   boolean firstTaskToBeAllocated = true;
								for( FogDevice keyDevice : partialAllocation.keySet()) {
									if(keyDevice.getName().equals(chosenServerToAllocateT.getName())){
										 List<AppModule> modulesList2 = partialAllocation.get(keyDevice);
										 modulesList2.add(module);
										 partialAllocation.put(keyDevice, modulesList2);
										 firstTaskToBeAllocated = false;
									}
								}
								if(firstTaskToBeAllocated == true) {
									 List<AppModule> modulesList2 = new ArrayList<AppModule>();
									 modulesList2.add(module);
									 partialAllocation.put(chosenServerToAllocateT, modulesList2);
								}

								chosenServerToAllocateT.setInitialLoad(min);
								module.setLastCost(lastCostL.get(chosenServerToAllocateT));
								module.setFinishTime(min * 1000+(double)getClock());
						   }
					
						}
					}
				//	if(endReduce = true) break;
				}		
				if(endReduce == true) {
					finalMakespan = makeSpanPre;
					totalAllocation.clear();
					for (Map.Entry< FogDevice, List<AppModule> >  entry : totalAllocation_pre.entrySet()){
						  totalAllocation.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
					    }	
				break;
				}
			}  
			
			if(endReduce == false) {
			double makeSpanR = fogDevicesList.get(0).getInitialLoad();	
			for(FogDevice device : fogDevicesList){
				if(device.getInitialLoad() > makeSpanR) {
					makeSpanR = device.getInitialLoad();
				}
			}
			LogPlacement.debug("\nCurrent makespan  =  " + makeSpanR);
			LogPlacement.debug("mExpected  =  " + mExpected);
			LogPlacement.debug("Old makespan  =  " + makeSpanPre);
			
			boolean endReducePhase = false;
			if(makeSpanR > mExpected || mExpected==oldmExpected) {
				
				if(makeSpanR > makeSpanPre || makeSpanR == makeSpanPre) { 
					finalMakespan = makeSpanPre;
					LogPlacement.debug("\nNew makespan is worse than the expected makespan and worse than the old makespan.");
					LogPlacement.debug("So, will consider the old allocation as the final allocation and close."); 
					totalAllocation.clear();
					for (Map.Entry< FogDevice, List<AppModule> >  entry : totalAllocation_pre.entrySet()){
						  totalAllocation.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
					    }			    
					int x = 0;
					for(FogDevice device : fogDevicesList){
						device.setInitialLoad(preAllocationLoads.get(x));
						x++;			
					}
					endReducePhase = true;finalMakespan = makeSpanPre;
					break;
				}
				else {
					LogPlacement.debug("\nNew makespan is worse than the expected makespan, but it is better than the old makespan. So, will consider the new allocation as the final allocation and close.");
					totalAllocation.clear();
					  for (Map.Entry< FogDevice, List<AppModule> >  entry : partialAllocation.entrySet()){
						  totalAllocation.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
					    }
					endReducePhase = true;finalMakespan = makeSpanR;
					break;
				}
			}
			else {
				LogPlacement.debug("\nNew makespan is better than the expected makespan, and it is better than the old makespan. So, will update the allocation with the new parital allocation and continue redo reduce step." );
				totalAllocation_pre.clear();
				for (Map.Entry< FogDevice, List<AppModule> >  entry : partialAllocation.entrySet()){
					 totalAllocation_pre.put(entry.getKey(), new ArrayList<AppModule>(entry.getValue()));
				    }
				 oldmExpected = mExpected;
			}
			
			if(endReducePhase == true) break;
		}
			if(endReduce == true) break;
		//	LogPlacement.debug(" not ending");
		}	
		
		LogPlacement.debug("\n\nCurrent loads:");
		for(FogDevice f : fogDevicesList){LogPlacement.debug( f.getName() + " : " + f.getInitialLoad());}
		
		setRoundProcessingTime((Calendar.getInstance().getTimeInMillis() - startCalendar.getTimeInMillis()));
        setAccumulatedProcessingTime(getAccumulatedProcessingTime()+getRoundProcessingTime());
        
        //Start Evaluation
		double totalCloudCost = 0;
		double totalCloudLoad = 0;
		Map<Integer, List<AppModule>> myDeviceToModuleMap = new HashMap<Integer, List<AppModule>>();
		List<FogDevice> lastFogDevices = new ArrayList<FogDevice>();
		int fogCounter = 0; 
		int cloudCounter = 0;
		// Energy Evaluation
		//1-Computing Energy
		 double E_Ftotal = 0;
		for(Map.Entry<FogDevice, List<AppModule>> allocation: totalAllocation.entrySet()) {
			 double E_f = 0;
			 double E_fComp = 0;
			 double E_fComm = 0;
			LogResult.debug("\nFogDevice " + allocation.getKey().getName() + " has the following tasks allocated. With Final load = " + allocation.getKey().getInitialLoad());
			LogResult.debug("-------------------------------------------------------");
			int deviceId = CloudSim.getEntityId(allocation.getKey().getName());		
			myDeviceToModuleMap.put(deviceId, allocation.getValue());
			
			for(AppModule app : allocation.getValue()) {
				LogResult.debug(app.getName() );
				/*getCurrentModuleMap().get(deviceId).add(app.getName());
				getCurrentModuleLoadMap().get(deviceId).put(app.getName(), 0.0);
				getCurrentModuleInstanceNum().get(deviceId).put(app.getName(), 0);
				createModuleInstanceOnDevice(getApplication().getModuleByName(app.getName()), getFogDeviceById(deviceId));*/
				if(allocation.getKey().getName().startsWith("c")) {
				
				if(app.getClassification()==1) numberClass1TasksPerCloud[cloudCounter]++;
				else if(app.getClassification()==2) numberClass2TasksPerCloud[cloudCounter]++;
				else if(app.getClassification()==3) numberClass3TasksPerCloud[cloudCounter]++;
				
				//calculate network load
				setNetworkLoad(getNetworkLoad()+app.getSize());
				}
				else if(allocation.getKey().getName().startsWith("g")) {
				
				if(app.getClassification()==1) numberClass1TasksPerFog[fogCounter]++;
				else if(app.getClassification()==2) numberClass2TasksPerFog[fogCounter]++;
				else if(app.getClassification()==3) numberClass3TasksPerFog[fogCounter]++;
				//For Energy: 2-Communication Energy
				//Stationary Scenario
					 double T_FComm_Recv = 0;
					 double T_FComm_Recv_St  = 0;
					 double T_FComm_Recv_mob  = 0;
					//get current locality 
					int sinkID = app.getSensors().getGatewayDeviceId();
					int gatewayID = getFogDeviceById(sinkID).getParentId();
					if(	gatewayID != (allocation.getKey().getId())){
							T_FComm_Recv_St = app.getSize() / 1024; //fogBW
			
					}
				//Mobility scenario
					else{
							if(getClock() != 0) {	// if Not initial run
								
								//if(getDeviceById(sinkID).isHandoffHandled() == false) {	// If this sink moved since last allocation
								if(getDeviceById(sinkID).isHandoffStatus() == true) {
									//Get previous locality 
									int oldGatewayID =  getDeviceById(sinkID).getOldSourceGateway().getId();
									if (oldGatewayID != (allocation.getKey().getId())) {
										T_FComm_Recv_mob = app.getPartialDataSize() / 1024; //fogBW
									}
								// partial locality handeled so 
								//getFogDeviceById(sinkID).setHandoffHandled(true);
								

								/*boolean previousAllocFound = false;
								for(AppModule previousModule : PreviousCLKAllocation.get(allocation.getKey())) {
									if(app == previousModule) {previousAllocFound=true;break;}
								}
								if(!previousAllocFound) {
									// If was not local 
									T_FComm_Recv_mob = app.getPartialDataSize() / 1024; 
								}*/	
								}
							}
					}
				 T_FComm_Recv = T_FComm_Recv_St + T_FComm_Recv_mob;
				 E_fComm += ( allocation.getKey().getPowerList().get(2) * (finalMakespan - T_FComm_Recv )) + (allocation.getKey().getPowerList().get(3) * T_FComm_Recv);
				}
				else {
				if(app.getClassification()==1) numClass1TasksSink++;
				else if(app.getClassification()==2) numClass2TasksSink++;
				else if(app.getClassification()==3) numClass3TasksSink++;
					
				}
			}
			
			//Calculate cloud cost (Equation from their implementation in FogDevice Class line 565)
			/*if(allocation.getKey().getName().startsWith("c")) {
			int totalAvailableMips = 0;
			double currentCost = 0;
			double newCost = 0;
			double totalTimeAllocated = allocation.getKey().getInitialLoad();
			double lastUtilization = 1; // lastutilization = min ( 1 , (total allocated mips for all VMs / total mips)). We are assuming we are utilizing all the available MIPS for the Vms/Tasks
			
			// Calculate total available MIPS in that cloud node = Sum of all PEs assuming 1 host
			List<Pe> peList = allocation.getKey().getHostList().get(0).getPeList();
			for (Pe pe : peList ) {
				totalAvailableMips += pe.getMips();
			}
			// Calculate cost
			currentCost = allocation.getKey().getTotalCost();
			// Note: THis is their equation (time cost), but they are not multiplying by the cost money
			//newCost = currentCost + (totalTimeAllocated * totalAvailableMips * allocation.getKey().getRatePerMips() * lastUtilization);

			// My updated equation
			//same but remove rate
			//newCost = currentCost +  (totalTimeAllocated * totalAvailableMips  * lastUtilization);
			
			// multipling by cost per unit time with and without rate
			newCost = currentCost + (allocation.getKey().getCharacteristics().getCostPerSecond() )* (totalTimeAllocated * totalAvailableMips * allocation.getKey().getRatePerMips() * lastUtilization);
			//newCost = currentCost + (allocation.getKey().getCharacteristics().getCostPerSecond() )* (totalTimeAllocated * totalAvailableMips *  lastUtilization);
			
			// mine multiple time by cost per sec
			//newCost = (allocation.getKey().getCharacteristics().getCostPerSecond() )* (totalTimeAllocated );
		
			allocation.getKey().setTotalCost(newCost);
			
			totalCloudCost+= newCost;
			}*/
			
			if(allocation.getKey().getName().startsWith("c")) {
				totalCloudLoad+= allocation.getKey().getInitialLoad();
				
				numberTasksPerCloud[cloudCounter]+= allocation.getValue().size();
				cloudCounter++;
			}
			else if(allocation.getKey().getName().startsWith("g")) {
				numberTasksPerFog[fogCounter]+= allocation.getValue().size();
				fogCounter++;
				// For Energy: 1-Computation
				//Eq16 (remove sum from paper)
					E_fComp = ( allocation.getKey().getPowerList().get(0)) * (finalMakespan-allocation.getKey().getInitialLoad()) + ( allocation.getKey().getPowerList().get(1) *allocation.getKey().getInitialLoad());
					E_f = E_fComp + E_fComm;
					E_Ftotal+= E_f;
			}
			else {
				numTasksSink+= allocation.getValue().size();
			}
			
			// set the fogDevices with the last version of the allocated fog nodes with their initial loads
			lastFogDevices.add(allocation.getKey());
			// Final energy consumption for a fog and total for all 
					
		}
		
		
		//Currently I am saving the total cloud load in order to calculate the correct equation when I know
		finalTotalCostForCloudNodes = totalCloudLoad;
		//Currently I am saving the total Energy consumption
		setEnergyConsumption(getEnergyConsumption()+E_Ftotal);		
		setDeviceToModuleMap(myDeviceToModuleMap);
		setFogDevices(lastFogDevices);
		
		//Reset handoff status
		for(FogDevice sinkmob : getFogDevices()) {
			if(sinkmob.isHandoffStatus())
				sinkmob.setHandoffStatus(false);
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
		this.randomBW[0]=10;this.randomBW[1]=100;this.randomBW[2]=512;this.randomBW[3]=1024;
		this.setReallocationStrategy(reallocationStrategy);
		this.setClock(clock); 
		this.setRandomValue1ForCloudBW(randomValue1ForCloudBW);
		balancedAllocation.clear();
		balancedAllocation2.clear();
		updateInitialLoads(allocationSamplingFreq);
		updateIssueTimes();
		mapModules();
		calculateEvaluation();
		printMobilityResultsVsClock();
		//if(clock == MaxAndMin.MAX_SIMULATION_TIME/1000-getAllocationSamplingFreq()) {
		if(clock == (MaxAndMin.MAX_SIMULATION_TIME/1000)-((MaxAndMin.MAX_SIMULATION_TIME/1000)%getAllocationSamplingFreq())) {
			printMobilityResults();
		}
	
	}
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
	public void printMobilityResults() {
		
		//Printing Makespan Vs Clock  
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanBAR.txt", true);
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
		try(FileWriter fwEnergy = new FileWriter("MobilityEnergyBAR.txt", true);
		BufferedWriter bwEnergy = new BufferedWriter(fwEnergy);
		PrintWriter outEneregy = new PrintWriter(bwEnergy))
			{
			String energyString = Double.toString(getEnergyConsumption());
			outEneregy.println(getResultVaryingFactorValue() + " " + energyString);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		//Printing MaxResponseTime(deadline) miss ratio Vs the varying factor
/*		double numOfModulesMissMaxResTime = 0;
		double totalNumOfModulesWzMaxResponse = 0;
		double missRatio = 0 ;
		double totalDelay = 0 ;
		double AvgDelay = 0;
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
		AvgDelay = totalDelay / numOfModulesMissMaxResTime;*/
		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioBAR.txt", true);
				BufferedWriter bwMissRatio = new BufferedWriter(fwMissRatio);
				PrintWriter outMissRatio = new PrintWriter(bwMissRatio))
					{
				//	String missRatioString = Double.toString(missRatio);
					outMissRatio.println(getResultVaryingFactorValue() + " " + accumulatedMissRatio/((MaxAndMin.MAX_SIMULATION_TIME/1000)/getAllocationSamplingFreq()));
					   
					} 
				catch (IOException e) {
					    //e.printStackTrace();
					}
		//Printing Average delay in missing MaxResponseTime Vs the varying factor   
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayBAR.txt", true);
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
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeBAR.txt", true);
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
		try(FileWriter fwCost = new FileWriter("MobilitycostBAR.txt", true);
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
		try(FileWriter fwNetload = new FileWriter("MobilitynetworkloadBAR.txt", true);
		BufferedWriter fwnetload = new BufferedWriter(fwNetload);
		PrintWriter outNetLoad = new PrintWriter(fwnetload))
			{
			//String averageCloudCostString = Double.toString(finalTotalCostForCloudNodes);
			outNetLoad.println(getResultVaryingFactorValue() + " " + getNetworkLoad()/8); // we divide by 8 because it is saved here in Mbit but we'll print in MB
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing newtwork load Vs the varying factor 
		try(FileWriter fwSizeMissedData = new FileWriter("MobilitySizeMissedDataHealthEdge.txt", true);
		BufferedWriter bwSizeMissedData = new BufferedWriter(fwSizeMissedData);
		PrintWriter outSizeMissedData = new PrintWriter(bwSizeMissedData))
			{
			//String averageCloudCostString = Double.toString(finalTotalCostForCloudNodes);
			outSizeMissedData.println(getResultVaryingFactorValue() + " " + getSizeMissedDataMobility()); // we divide by 8 because it is saved here in Mbit but we'll print in MB
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}		
	LogPlacement.debug("\nEnded printing Mobility files. \n ");
	}

	public void printMobilityResultsVsClock() {
		
		//Printing Makespan Vs Clock  
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanClkBAR.txt", true);
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

		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioClkBAR.txt", true);
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
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayClkBAR.txt", true);
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
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeClkBAR.txt", true);
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
		try(FileWriter fwCost = new FileWriter("MobilitycostClkBAR.txt", true);
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
	
	//public static List<FogDevice> getGateways() {
		public List<FogDevice> getGateways() {
			List<FogDevice> GatewaysList =new ArrayList<>();
		
			for(FogDevice gateway: getFogDevices()){
				if(gateway.getName().startsWith("g") ||gateway.getName().startsWith("c")  )
					GatewaysList.add(gateway);
			}
			return GatewaysList;
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

	public double getNetworkLoad() {
		return networkLoad;
	}

	public void setNetworkLoad(double networkLoad) {
		this.networkLoad = networkLoad;
	}
	public double getSizeMissedDataMobility() {
		return sizeMissedDataMobility;
	}

	public void setSizeMissedDataMobility(double sizeMissedDataMobility) {
		this.sizeMissedDataMobility = sizeMissedDataMobility;
	}

	public double getEnergyConsumption() {
		return energyConsumption;
	}

	public void setEnergyConsumption(double energyConsumption) {
		this.energyConsumption = energyConsumption;
	}

/*	public int[] getNumberTasksPerFog() {
		return numberTasksPerFog;
	}

	public void setNumberTasksPerFog(int[] numberTasksPerFog) {
		this.numberTasksPerFog = numberTasksPerFog;
	}

	public int[] getNumberClass1TasksPerFog() {
		return numberClass1TasksPerFog;
	}

	public void setNumberClass1TasksPerFog(int[] numberClass1TasksPerFog) {
		this.numberClass1TasksPerFog = numberClass1TasksPerFog;
	}

	public int[] getNumberClass2TasksPerFog() {
		return numberClass2TasksPerFog;
	}

	public void setNumberClass2TasksPerFog(int[] numberClass2TasksPerFog) {
		this.numberClass2TasksPerFog = numberClass2TasksPerFog;
	}

	public int[] getNumberClass3TasksPerFog() {
		return numberClass3TasksPerFog;
	}

	public void setNumberClass3TasksPerFog(int[] numberClass3TasksPerFog) {
		this.numberClass3TasksPerFog = numberClass3TasksPerFog;
	}

	public int[] getNumberTasksPerCloud() {
		return numberTasksPerCloud;
	}

	public void setNumberTasksPerCloud(int[] numberTasksPerCloud) {
		this.numberTasksPerCloud = numberTasksPerCloud;
	}

	public int[] getNumberClass1TasksPerCloud() {
		return numberClass1TasksPerCloud;
	}

	public void setNumberClass1TasksPerCloud(int[] numberClass1TasksPerCloud) {
		this.numberClass1TasksPerCloud = numberClass1TasksPerCloud;
	}

	public int[] getNumberClass2TasksPerCloud() {
		return numberClass2TasksPerCloud;
	}

	public void setNumberClass2TasksPerCloud(int[] numberClass2TasksPerCloud) {
		this.numberClass2TasksPerCloud = numberClass2TasksPerCloud;
	}

	public int[] getNumberClass3TasksPerCloud() {
		return numberClass3TasksPerCloud;
	}

	public void setNumberClass3TasksPerCloud(int[] numberClass3TasksPerCloud) {
		this.numberClass3TasksPerCloud = numberClass3TasksPerCloud;
	}*/
}
