package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudfogHC.LogPlacement;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.mobility.MaxAndMin;

public class ModulePlacementCloudOnly extends ModulePlacement{

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
	
	/**
	 * Stores the current mapping of application modules to fog devices 
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;
	
	public ModulePlacementCloudOnly(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, Application application , int reallocationStrategy , int allocationSamplingFreq , double resultVaryingFactorValue){
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
		mapModules();
		calculateEvaluation();
		//printMobilityResultsVsClock();
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
        cloud();
        setRoundProcessingTime((Calendar.getInstance().getTimeInMillis() - startCalendar.getTimeInMillis()));
        setAccumulatedProcessingTime(getAccumulatedProcessingTime()+getRoundProcessingTime());
        LogPlacement.debug("\nEnded Module placement. \n ");
        LogPlacement.debug("Total Allocation Algorithm Time: "+ roundProcessingTime + " milliseconds");
        LogPlacement.debug("__________________________________________________________________________________");
	}
	public void cloud() {
		modules = getApplication().getModules(); 
		//fogDevicesList = getFogDevices();
		cloudDevicesList = getCloudDevices();
		int iterator = 0;
		for(AppModule module : modules){
			//HashMap< Double , FogDevice > cloud_costList = new HashMap< Double , FogDevice> ();	//load of all servers if task t is allocated
			//HashMap<  FogDevice , Double > lastCostL = new HashMap<  FogDevice, Double> ();	//load of all servers if task t is allocated
			int sinkID = module.getSensors().getGatewayDeviceId();
			FogDevice sm = getFogDeviceById(sinkID)	;
			if(sm.getSourceGateway()!=null){
				
				//5- Allocate to next cloud data center			
				double remoteCost, lastCost = 0;	
				FogDevice cloud = cloudDevicesList.get(iterator);
				remoteCost =  (module.getMips()	/ cloud.getMips())+ Math.max(cloud.getInitialLoad(), (module.getSize() / cloud.getUplinkBandwidth()));	
				lastCost =  (module.getMips()	/ cloud.getMips())+ Math.abs(cloud.getInitialLoad()- (module.getSize() / cloud.getUplinkBandwidth()));
				module.setLastCost(lastCost);
				LogPlacement.debug( module.getName() + " current cost = " +  lastCost);
				module.setFinishTime(remoteCost *1000);
		
				// allocate the task/module to that fog/cloud device
			    balancedAllocation.put(module, cloud);
			    LogPlacement.debug("-> Allocating " +  module.getName() +  " to " + cloud.getName() );
				
			    //calculate network load
			    setNetworkLoad(getNetworkLoad()+module.getSize());
				
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
				if(iterator%cloudDevicesList.size()==0) iterator=0;
				
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
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanClkCloud.txt", true);
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
		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioClkCloud.txt", true);
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
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayClkCloud.txt", true);
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
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeClkCloud.txt", true);
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
		try(FileWriter fwCost = new FileWriter("MobilitycostClkCloud.txt", true);
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
		balancedAllocation.clear();
		balancedAllocation2.clear();
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
		try(FileWriter fwMakespan = new FileWriter("MobilitymakespanCloud.txt", true);
		BufferedWriter bwMakespan = new BufferedWriter(fwMakespan);
		PrintWriter outMakespan = new PrintWriter(bwMakespan))
			{
			String makespanString = Double.toString(finalMakespan);
			outMakespan.println(getResultVaryingFactorValue() + " " + makespanString);
			   
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
		try(FileWriter fwMissRatio = new FileWriter("MobilitymissratioCloud.txt", true);
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
		try(FileWriter fwAvgDelay = new FileWriter("MobilityAvgDelayCloud.txt", true);
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
		try(FileWriter fwProcessing = new FileWriter("MobilityprocessingTimeCloud.txt", true);
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
		try(FileWriter fwCost = new FileWriter("MobilitycostCloud.txt", true);
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
		try(FileWriter fwNetload = new FileWriter("MobilitynetworkloadCloud.txt", true);
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
	public double getNetworkLoad() {
		return networkLoad;
	}

	public void setNetworkLoad(double networkLoad) {
		this.networkLoad = networkLoad;
	}
}
