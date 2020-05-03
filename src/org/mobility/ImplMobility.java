package org.mobility;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudfogHC.LogMobile;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementBAR;
import org.fog.placement.ModulePlacementCloudOnly;
import org.fog.placement.ModulePlacementHEFT;
import org.fog.placement.ModulePlacementHealthEdge;
import org.fog.placement.ModulePlacementOnlyCloud;
import org.fog.utils.FogEvents;
import org.fog.utils.TimeKeeper;

public class ImplMobility {
	
	private static int seed;
	private static Random rand;
	private static List<FogDevice> fogDevices;
	private static List<FogDevice> sinksMobile;
	private static int stepPolicy;
	private static List<Actuator> actuators;
	private static List<Sensor> sensors;
	private static Controller controller;
	private static Application application;
	private static boolean CLOUD_ONLY;
	private static double resultVaryingFactorValue;
	private static int allocationTechnique;
	private static int reallocationStrategy;
	private static int allocationSamplingFreq;
	private static ModulePlacement myPlacement;
	private static String allocationTechniqueName;
	
	private static int clock;
	private static int totalNumofUnconnectedSinks = 0;
	private static int missedTasksInAllocation= 0;
	
	private static double fogUtilization,fogClass1Utilization,fogClass2Utilization,fogClass3Utilization; 
	private static double cloudUtilization,cloudClass1Utilization,cloudClass2Utilization,cloudClass3Utilization; 
	List<FogDevice> unconnectedSinks = new ArrayList<FogDevice>();
	
	public ImplMobility(List<FogDevice> fogDevices, List<FogDevice> sinksMobile , int stepPolicy , List<Actuator> actuators, List<Sensor> sensors, Controller controller , Application application ,int allocationTechnique, int reallocationStrategy, int allocationSamplingFreq , ModulePlacement myPlacement , double resultVaryingFactorValue) {
		resetHandoffCounters();
		setFogDevices(fogDevices);
		setSinksMobile(sinksMobile);
		setStepPolicy(stepPolicy);
		setSeed(30); 
		setRand(new Random(getSeed()*Long.MAX_VALUE));
		setActuators(actuators);
		setSensors(sensors);
		setController(controller);
		setApplication(application);
		setResultVaryingFactorValue(resultVaryingFactorValue);
		setAllocationTechnique(allocationTechnique);
		setReallocationStrategy(reallocationStrategy);	
		setAllocationSamplingFreq(allocationSamplingFreq);
		setMyPlacement(myPlacement);
		setTotalNumofUnconnectedSinks(0);
		setMissedTasksInAllocation(0);
		initUtilization();
		}
	
	private void initUtilization() {
		fogUtilization = 0;
		fogClass1Utilization = 0;
		fogClass2Utilization = 0;
		fogClass3Utilization = 0; 
		cloudUtilization = 0;
		cloudClass1Utilization = 0;
		cloudClass2Utilization = 0;
		cloudClass3Utilization = 0;
		
	}
	public void Move() {
		//For the duration of the simulation, with 1000 clock step size, move the nodesm the verify handoff and allocation/scheduling
		Random r1 = new Random();
		int randomValue1ForCloudBW = r1.nextInt(2 - 0 + 1) + 0;
		int allocationFrequency = 0;
		setClock(0);
		printMobilityVsClock();
		//for(int clock = 1; clock<MaxAndMin.MAX_SIMULATION_TIME; clock+=1000){	// in ms
		for(int clock = 1; clock<=MaxAndMin.MAX_SIMULATION_TIME/1000; clock+=1){	// in second
			setClock(clock);
			
			// Make next step
			NextStep.nextStep(fogDevices , sinksMobile, stepPolicy);
			
			// Check the step and do handoff if required
			checkNewStep();
			
			allocationFrequency++;
			if(allocationFrequency == getAllocationSamplingFreq()) {
				switch(allocationTechnique) {
				case Policies.Allocation_CloudOnly:	
					setAllocationTechniqueName("Cloud");
					//((ModulePlacementCloudOnly)getMyPlacement()).updateInitialLoads(getAllocationSamplingFreq());;
					((ModulePlacementCloudOnly)getMyPlacement()).reInitializePlacement(fogDevices, sensors, actuators, application , reallocationStrategy , getAllocationSamplingFreq() , getClock() , randomValue1ForCloudBW , getResultVaryingFactorValue());			
					break;
				case Policies.Allocation_MBAR:
					setAllocationTechniqueName("BAR");
					//((ModulePlacementBAR)getMyPlacement()).updateInitialLoads(getAllocationSamplingFreq());;
					((ModulePlacementBAR)getMyPlacement()).reInitializePlacement(fogDevices, sensors, actuators, application , reallocationStrategy , getAllocationSamplingFreq() , getClock() , randomValue1ForCloudBW , getResultVaryingFactorValue());
					break;
				case Policies.Allocation_HEFT:
					setAllocationTechniqueName("HEFT");
					//((ModulePlacementHEFT)getMyPlacement()).updateInitialLoads(getAllocationSamplingFreq());;
					((ModulePlacementHEFT)getMyPlacement()).reInitializePlacement(fogDevices, sensors, actuators, application , reallocationStrategy , getAllocationSamplingFreq() , getClock() , randomValue1ForCloudBW , getResultVaryingFactorValue());
					break;
				case Policies.Allocation_HealthEdge:
					setAllocationTechniqueName("HealthEdge");
					//((ModulePlacementHEFT)getMyPlacement()).updateInitialLoads(getAllocationSamplingFreq());;
					((ModulePlacementHealthEdge)getMyPlacement()).reInitializePlacement(fogDevices, sensors, actuators, application , reallocationStrategy , getAllocationSamplingFreq() , getClock() , randomValue1ForCloudBW , getResultVaryingFactorValue());
					break;
				}
				
				
				allocationFrequency = 0 ;
				for(FogDevice sink: unconnectedSinks) {
					setMissedTasksInAllocation(getMissedTasksInAllocation() + sink.getNumTasksAttached());
				}
				
				printMobilityVsClock();
				//HandOff.setTotalNumOfHandoffs(0);
				
			}
		
			//System.out.println("sinksMobileListSize: "+getSinksMobile().size());
			//if(getSinksMobile().isEmpty())
			//	sendNow(getId(), FogEvents.STOP_SIMULATION);
			
		}
		
		//This part prints resource utilization: how many tasks allocated to fog and how many to cloud, etc and what is the type of the classes etc..
		for(int i = 0; i<50 ; i++) {
			//System.out.println("Fog"+i+": "+ getMyPlacement().getNumberTasksPerFog()[i] + " , class1: " +  getMyPlacement().getNumberClass1TasksPerFog()[i] + " , class2: " +  getMyPlacement().getNumberClass2TasksPerFog()[i]+ " , class3:" +  getMyPlacement().getNumberClass3TasksPerFog()[i]);
			fogUtilization+= getMyPlacement().getNumberTasksPerFog()[i];
			fogClass1Utilization+= getMyPlacement().getNumberClass1TasksPerFog()[i];
			fogClass2Utilization+= getMyPlacement().getNumberClass2TasksPerFog()[i]; 
			fogClass3Utilization+=getMyPlacement().getNumberClass3TasksPerFog()[i];
		}
		for(int i = 0; i< 60; i++) {
			//System.out.println("Cloud"+i+": "+ getMyPlacement().getNumberTasksPerCloud()[i] + " , class1: " +  getMyPlacement().getNumberClass1TasksPerCloud()[i] + " , class2: " +  getMyPlacement().getNumberClass2TasksPerCloud()[i]+ " , class3:" +  getMyPlacement().getNumberClass3TasksPerCloud()[i]);
			cloudUtilization+= getMyPlacement().getNumberTasksPerCloud()[i];
			cloudClass1Utilization+= getMyPlacement().getNumberClass1TasksPerCloud()[i];
			cloudClass2Utilization+= getMyPlacement().getNumberClass2TasksPerCloud()[i];
			cloudClass3Utilization+=getMyPlacement().getNumberClass3TasksPerCloud()[i];
		}
		//System.out.println("Sinks: "+ getMyPlacement().getNumTasksSink() + " , class1: " +  getMyPlacement().getNumClass1TasksSink() + " , class2: " +  getMyPlacement().getNumClass2TasksSink()+ " , class3:" +  getMyPlacement().getNumClass3TasksSink());

		printMobility();
	}
	public void checkNewStep() {
	
		int index=0;

		for(FogDevice sm: sinksMobile){
			MyStatistics.getInstance().getEnergyHistory().put(sm.getMyId(), sm.getEnergyConsumption());
			MyStatistics.getInstance().getPowerHistory().put(sm.getMyId(),sm.getHost().getPower());
			if(sm.getSourceGateway()!=null){
				if(!sm.isLockedToHandoff()){//(!st.isHandoffStatus()){
					double distance=Distances.checkDistance(sm.getCoord(), sm.getSourceGateway().getCoord());

					//if(distance>=MaxAndMin.MAX_DISTANCE-MaxAndMin.MAX_DISTANCE_TO_HANDOFF && distance<MaxAndMin.MAX_DISTANCE){ //Handoff Zone
					if(distance>=MaxAndMin.MAX_DISTANCE-MaxAndMin.MAX_DISTANCE_TO_HANDOFF && distance<MaxAndMin.MAX_DISTANCE){ //Handoff Zone
						index=HandOff.nextGateway(getGateways(), sm);

						if(index >= 0){//index isn't negative 
							double newDistance=Distances.checkDistance(sm.getCoord(), getGatewayById(index).getCoord());
							if(newDistance < distance) {
								sm.setDestinationGateway(getGatewayById(index));	
								sm.setHandoffStatus(true);
								sm.setLockedToHandoff(true);
								float handoffLocked = (float) ((MaxAndMin.MAX_DISTANCE_TO_HANDOFF/(sm.getSpeed()+1))*2000);
								double handoffTime = MaxAndMin.MIN_HANDOFF_TIME + (MaxAndMin.MAX_HANDOFF_TIME - MaxAndMin.MIN_HANDOFF_TIME) * getRand().nextDouble(); //"Maximo" tempo para handoff
							
								
								
								HandOff.doHandOff(sm, sm.getSourceGateway(), handoffTime);
								
								
								//send(sm.getDestinationGateway().getId(),handoffLocked,FogEvents.UNLOCKED_HANDOFF,sm);
								HandOff.unLockedHandoff(sm);
								
								for(FogDevice unconnectedSM : unconnectedSinks) {
									if(unconnectedSM == sm) {
										unconnectedSinks.remove(unconnectedSM);
										setTotalNumofUnconnectedSinks(getTotalNumofUnconnectedSinks()-1);
									}
								}
							}
						}
						else{
							LogMobile.debug("MobileController.java", sm.getName()+" can't make handoff because don't exist closest nextAp");
							
						}
					}
					else if(distance>=MaxAndMin.MAX_DISTANCE) {
						sm.getSourceGateway().disconnectGatewaySinkMobile(sm);
						LogMobile.debug("MobileController.java", sm.getName()+" disconnected by MAX_DISTANCE - Distance: "+distance);
						LogMobile.debug("MobileController.java", sm.getName()+" X: "+sm.getCoord().getCoordX()+ " Y: "+sm.getCoord().getCoordY());
						unconnectedSinks.add(sm);
						setTotalNumofUnconnectedSinks(getTotalNumofUnconnectedSinks()+1);
					}
				}
			}
			else{
				if(FogDevice.connectGatewaySinkMobile(getGateways(), sm, getRand().nextDouble())){ 
					LogMobile.debug("MobileController.java", sm.getName() +" has a new connection - SourceGateway: "+sm.getSourceGateway().getName());
					unconnectedSinks.remove(sm);
					setTotalNumofUnconnectedSinks(getTotalNumofUnconnectedSinks()-1);
					
				}
				else{
					//To do something
				}
			}
		}
	}
	public static void printMobility() {

		//Printing number of handoffs 
		try(FileWriter fwNumHandoffs = new FileWriter("MobilityNumHandoffs.txt", true);
		BufferedWriter bwNumHandoffs = new BufferedWriter(fwNumHandoffs);
		PrintWriter outNumHandoffs = new PrintWriter(bwNumHandoffs))
			{
			String NumHandoffs = Double.toString(HandOff.getTotalNumOfHandoffs());
			outNumHandoffs.println(getResultVaryingFactorValue() + " " + NumHandoffs);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing number of handoffs 
		try(FileWriter fwNumMissedTasks = new FileWriter("MobilityMissedTasks.txt", true);
		BufferedWriter bwNumMissedTasks = new BufferedWriter(fwNumMissedTasks);
		PrintWriter outNumMissedTasks = new PrintWriter(bwNumMissedTasks))
			{
			String NumMissedTasks = Integer.toString(getMissedTasksInAllocation());
			outNumMissedTasks.println(getResultVaryingFactorValue() + " " + NumMissedTasks);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}		
		/*TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
		MyStatistics.getInstance().setSeed(seed); 
		for(FogDevice sm: sinksMobile){
			MyStatistics.getInstance().setFileMap("./outputLatencies/"+sm.getMyId()+"/latencies_FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"+seed+"_st_"+sm.getMyId()+".txt",sm.getMyId());
			MyStatistics.getInstance().putLantencyFileName("FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"+ seed+"_st_"+sm.getMyId(),sm.getMyId());
			MyStatistics.getInstance().setToPrint("FIXED_MIGRATION_POINT_with_LOWEST_LATENCY");
		}*/
		
		//Printing Utilization
		
		try(FileWriter fwNumUtilization = new FileWriter("Utilization"+getAllocationTechniqueName()+".txt", true);
		BufferedWriter bwNumUtilization = new BufferedWriter(fwNumUtilization);
		PrintWriter outNumUtilization = new PrintWriter(bwNumUtilization))
			{
			String NumMissedTasks = Integer.toString(getMissedTasksInAllocation());
			outNumUtilization.println("fogUtilization:" + fogUtilization);
			outNumUtilization.println("fogClass1Utilization:" + fogClass1Utilization);
			outNumUtilization.println("fogClass2Utilization:" + fogClass2Utilization);
			outNumUtilization.println("fogClass3Utilization:" + fogClass3Utilization);
			outNumUtilization.println("cloudUtilization:" + cloudUtilization);
			outNumUtilization.println("cloudClass1Utilization:" + cloudClass1Utilization);
			outNumUtilization.println("cloudClass2Utilization:" + cloudClass2Utilization);
			outNumUtilization.println("cloudClass3Utilization:" + cloudClass3Utilization);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}		
	}
	public static void printMobilityVsClock() {

		//Printing number of handoffs 
		try(FileWriter fwNumHandoffs = new FileWriter("MobilityNumHandoffsClk.txt", true);
		BufferedWriter bwNumHandoffs = new BufferedWriter(fwNumHandoffs);
		PrintWriter outNumHandoffs = new PrintWriter(bwNumHandoffs))
			{
			String NumHandoffs = Double.toString(HandOff.getTotalNumOfHandoffs());
			outNumHandoffs.println(getClock() + " " + NumHandoffs);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}
		
		//Printing number of handoffs 
		try(FileWriter fwNumHandUnconnectSinks = new FileWriter("MobilityMissedTasksClk.txt", true);
		BufferedWriter bwNumHandUnconnectSinks = new BufferedWriter(fwNumHandUnconnectSinks);
		PrintWriter outNumUnconnectSinks = new PrintWriter(bwNumHandUnconnectSinks))
			{
			String NumMissedTasks = Double.toString(getMissedTasksInAllocation());
			outNumUnconnectSinks.println(getClock()+ " " + NumMissedTasks);
			   
			} 
		catch (IOException e) {
			    //e.printStackTrace();
			}		
		/*TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
		MyStatistics.getInstance().setSeed(seed); 
		for(FogDevice sm: sinksMobile){
			MyStatistics.getInstance().setFileMap("./outputLatencies/"+sm.getMyId()+"/latencies_FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"+seed+"_st_"+sm.getMyId()+".txt",sm.getMyId());
			MyStatistics.getInstance().putLantencyFileName("FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"+ seed+"_st_"+sm.getMyId(),sm.getMyId());
			MyStatistics.getInstance().setToPrint("FIXED_MIGRATION_POINT_with_LOWEST_LATENCY");
		}*/
	}
	private static void resetHandoffCounters() {
		HandOff.setTotalNumOfHandoffs(0);

	}
	public static List<FogDevice> getGateways() {
		List<FogDevice> GatewaysList =new ArrayList<>();

		for(FogDevice gateway: getFogDevices()){
			if(gateway.getName().startsWith("g"))
				GatewaysList.add(gateway);
		}
		return GatewaysList;
	}
	public static FogDevice getGatewayById(int Id) {
		FogDevice selectedGateway = null ;

		for(FogDevice gateway: getFogDevices()){
			if(gateway.getMyId() == Id)
				selectedGateway = gateway;
		}
		return selectedGateway;
	}

	public static void setFogDevices(List<FogDevice> fogDevices) {
		ImplMobility.fogDevices = fogDevices;
	}
	public static List<FogDevice> getFogDevices() {
		return fogDevices;
	}
	public static void setSinksMobile(List<FogDevice> sinksMobile) {
		ImplMobility.sinksMobile = sinksMobile;
	}
	public static List<FogDevice> getSinksMobile() {
		return sinksMobile;
	}
	public static int getStepPolicy() {
		return stepPolicy;
	}
	public static void setStepPolicy(int stepPolicy) {
		ImplMobility.stepPolicy = stepPolicy;
	}
	public static Random getRand() {
		return rand;
	}
	public static void setRand(Random rand) {
		ImplMobility.rand = rand;
	}
	public static int getSeed() {
		return seed;
	}
	public static void setSeed(int seed) {
		ImplMobility.seed = seed;
	}
	public static List<Actuator> getActuators() {
		return actuators;
	}
	public static void setActuators(List<Actuator> actuators) {
		ImplMobility.actuators = actuators;
	}
	public static List<Sensor> getSensors() {
		return sensors;
	}
	public static void setSensors(List<Sensor> sensors) {
		ImplMobility.sensors = sensors;
	}
	public static Controller getController() {
		return controller;
	}
	public static void setController(Controller controller) {
		ImplMobility.controller = controller;
	}
	public static Application getApplication() {
		return application;
	}
	public static void setApplication(Application application) {
		ImplMobility.application = application;
	}

	public static double getResultVaryingFactorValue() {
		return resultVaryingFactorValue;
	}
	public static void setResultVaryingFactorValue(double resultVaryingFactorValue) {
		ImplMobility.resultVaryingFactorValue = resultVaryingFactorValue;
	}
	public static int getReallocationStrategy() {
		return reallocationStrategy;
	}
	public static void setReallocationStrategy(int reallocationStrategy) {
		ImplMobility.reallocationStrategy = reallocationStrategy;
	}
	public static int getAllocationSamplingFreq() {
		return allocationSamplingFreq;
	}
	public static void setAllocationSamplingFreq(int allocationSamplingFreq) {
		ImplMobility.allocationSamplingFreq = allocationSamplingFreq;
	}
	public static ModulePlacement getMyPlacement() {
		return myPlacement;
	}
	public static void setMyPlacement(ModulePlacement myPlacement) {
		ImplMobility.myPlacement = myPlacement;
	}
	public static int getClock() {
		return clock;
	}
	public static void setClock(int clock) {
		ImplMobility.clock = clock;
	}
	public static int getTotalNumofUnconnectedSinks() {
		return totalNumofUnconnectedSinks;
	}

	public static void setTotalNumofUnconnectedSinks(int totalNumofUnconnectedNodes) {
		ImplMobility.totalNumofUnconnectedSinks = totalNumofUnconnectedNodes;
	}
	public static int getMissedTasksInAllocation() {
		return missedTasksInAllocation;
	}
	public static void setMissedTasksInAllocation(int missedTasksInAllocation) {
		ImplMobility.missedTasksInAllocation = missedTasksInAllocation;
	}
	public static int getAllocationTechnique() {
		return allocationTechnique;
	}
	public static void setAllocationTechnique(int allocationTechnique) {
		ImplMobility.allocationTechnique = allocationTechnique;
	}
	public static String getAllocationTechniqueName() {
		return allocationTechniqueName;
	}
	public static void setAllocationTechniqueName(String allocationTechniqueName) {
		ImplMobility.allocationTechniqueName = allocationTechniqueName;
	}

}
