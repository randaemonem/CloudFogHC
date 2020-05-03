package org.mobility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.print.attribute.standard.Severity;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudfogHC.LogMobile;
import org.fog.entities.*;
public class HandOff {


	private static boolean migrationPoint;
	private static boolean migrationZone;
	private int location;	
	private FogDevice serverCloudletAvailable;
	private int flowDirection;
	private static List<FogDevice> gatewaysAvailable;
	private static List<FogDevice> serverCloudletsAvailable;
	private static int policyReplicaVM;
	private static int totalNumOfHandoffs = 0;
	//private static int totalNumofUnconnectedSinks = 0;
	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 */


	public static List<FogDevice> gatewayAvailableList(List<FogDevice> oldGatewaysList, FogDevice sinkMobile ){//It looks to cone and return the Aps available list
		List<FogDevice> newGatewaysList =new ArrayList<>();

		for(FogDevice gateway: oldGatewaysList){
			if(!sinkMobile.getSourceGateway().equals(gateway))
				newGatewaysList.add(gateway);
		}
		return newGatewaysList;
	}

	public static int nextGateway(List<FogDevice> gatewayDevices, FogDevice sinkMobile){//Policy: the closest Ap 

		setGatewaysAvailable(gatewayAvailableList(gatewayDevices,sinkMobile));//It return gatewayDevice list without the sinkmobile's sourceGteway 
		if (getGatewaysAvailable().size() == 0){
			return -1;
		}

		return  Distances.theClosestGateway(getGatewaysAvailable(), sinkMobile);//return the closest ap's id or -1 if it doesn't exist
	}
	public static void unLockedHandoff(FogDevice sinkMobile) {
		sinkMobile.setLockedToHandoff(false);
		//System.out.println(sinkMobile.getName() +" has the handoff unlocked");
		LogMobile.debug("HandOff.java", sinkMobile.getName() +" has the handoff unlocked");
		
	}
	
	public static void doHandOff(FogDevice sinkMobile, FogDevice sourceGateway, double handoffTime){ //handofftime= random delay time for the operation 
	
		if(sourceGateway.getSinks().contains(sinkMobile)){
			
			//Remove the sink mobile from the members list of the source gateway, remove link
			sourceGateway.setSinks(sinkMobile, Policies.REMOVE);//it'll remove the smartThing from ap-smartThing's set
			sinkMobile.getSourceGateway().setUplinkLatency(sourceGateway.getUplinkLatency()-handoffTime);
			NetworkTopology.addLink(sinkMobile.getSourceGateway().getId(), sinkMobile.getId(), 0.0, 0.0);//remove link
			
			// Update the source gateway for the sink mobile with the destination gateway previously chosen in Controller class
			sinkMobile.setOldSourceGateway(sourceGateway);//(sinkMobile.getSourceGateway());		//to be used by placement module
			sinkMobile.setSourceGateway(sinkMobile.getDestinationGateway());
			sinkMobile.setParentId(sinkMobile.getDestinationGateway().getId());
			
			// Add the sink mobile to the members list of the new gateway, add link
			sinkMobile.getSourceGateway().setSinks(sinkMobile, Policies.ADD);
			sinkMobile.getSourceGateway().setUplinkLatency(sinkMobile.getSourceGateway().getUplinkLatency()+handoffTime);
			NetworkTopology.addLink(sinkMobile.getSourceGateway().getId(), sinkMobile.getId(), sinkMobile.getUplinkBandwidth(), handoffTime);
			
			// Handoff is done, Delete the destination gateway, and close the handoff status 
			sinkMobile.setDestinationGateway(null);
			sinkMobile.setHandoffStatus(false);
			sinkMobile.setHandoffHandled(false);			//to be used by placement module
			sinkMobile.setHandOffClock(ImplMobility.getClock());
	
			LogMobile.debug("HandOff.java",sinkMobile.getName()+" was disconnected (inHandoff) to "+sourceGateway.getName());			
			LogMobile.debug("HandOff.java","++++++++++++++++++++HandoffSimple++++++++++++++++++++: "+sinkMobile.getName()+" temp: "+CloudSim.clock());			
			LogMobile.debug("HandOff.java", sinkMobile.getName()+" was connected (in Handoff) to "+sinkMobile.getSourceGateway().getName());
			sinkMobile.setTimeFinishHandoff(CloudSim.clock());
			//totalNumOfHandoffs++;
			
			setTotalNumOfHandoffs(getTotalNumOfHandoffs()+1);
		}
		else{
			System.out.println("*_No Handoff for " + sinkMobile.getName() + "*: " );
			//totalNumofUnconnectedSinks++;
		}
		//sm.setTimeFinishHandoff(CloudSim.clock());

	}
	
	/*public int nextApFromCloudlet(Set<ApDevice> apDevices, MobileDevice smartThing){

		return 0;
	}*/
	
	public static boolean insideCone(int smartThingDirection, int zoneDirection){//
		int ajust1, ajust2;

		if(smartThingDirection==Directions.EAST){
			ajust1=Directions.SOUTHEAST;
			ajust2=Directions.EAST+1;
		}
		else if(smartThingDirection==Directions.SOUTHEAST){
			ajust1=Directions.SOUTHEAST-1;
			ajust2=Directions.EAST;
		}
		else{
			ajust1=smartThingDirection-1; /*plus 45 degree*/
			ajust2=smartThingDirection+1;
		}

		if(zoneDirection == smartThingDirection || 
				zoneDirection==ajust1 || 
				zoneDirection == ajust2) /*Define Migration Zone -> it looks for 135 degree = 45 way + 45 way1 +45 way2*/
			return true;
		else
			return false;
	}




	/*public static List<FogDevice> serverClouletsAvailableList(List<FogDevice> oldServerCloudlets, MobileDevice smartThing){
		List<FogDevice> newServerCloudlets = new ArrayList<>();

		int localServerCloudlet;
		boolean cone;
		for(FogDevice sc: oldServerCloudlets ){
			localServerCloudlet=DiscoverLocalization.discoverLocal(
					smartThing.getCoord(),sc.getCoord());//return the relative position between Server Cloudlet and smart thing -> set this value
			cone = insideCone(localServerCloudlet,smartThing.getDirection());
			//, localAp);
			if(cone&&(sc.getMyId()!=smartThing.getSourceServerCloudlet().getMyId())){
				newServerCloudlets.add(sc);
			}
		}
		return newServerCloudlets;
	}
	
	public static int nextServerCloudlet(List<FogDevice> serverCloudlets, MobileDevice smartThing){//Policy: the closest serverCloudlet

		setServerCloudletsAvailable(serverClouletsAvailableList(serverCloudlets, smartThing));
		if(getServerCloudletsAvailable().size()==0){
			return -1;
		}
		else{
			return Distances.theClosestServerCloudlet(getServerCloudletsAvailable(), smartThing);
		}
	}

	public static boolean isEdgeAp(ApDevice apDevice, MobileDevice smartThing){
		if(apDevice.getServerCloudlet().getMyId() == smartThing.getSourceServerCloudlet().getMyId())// verify if the next Ap is edge
			return false; 
		else 
			return true;
	}

	public static int lowestLatencyCostServerCloudlet(List<FogDevice> oldServerCloudlets, List<ApDevice> oldApDevices, MobileDevice smartThing){
		List<FogDevice> newServerCloudlets = new ArrayList<>();
		List<FogDevice> numServerCloudlets = new ArrayList<>(); 

		for(FogDevice sc: oldServerCloudlets){ 
			newServerCloudlets.add(sc);
		}

		for(int i = 0; i<9; i++){
			int destinationServerCloudlet = nextServerCloudlet(newServerCloudlets, smartThing);
			if(destinationServerCloudlet>=0){
				for(FogDevice sc1:newServerCloudlets){
					if(sc1.getMyId()==destinationServerCloudlet){
						numServerCloudlets.add(sc1);
						break;
					}
				}

				FogDevice sc=null;
				for(int j = 0;j < newServerCloudlets.size();j++){

					sc=newServerCloudlets.get(j);
					if(sc.getMyId()==destinationServerCloudlet){
						newServerCloudlets.remove(sc);
						break;
					}
				}
			}
			else {
				break;
			}
		}

		if(numServerCloudlets.size()==0){
			return -1;
		}
		double sumCost;
		int choose =-1;
		double minCost=-1;
		int idNextAp = nextAp(oldApDevices, smartThing);

		if(idNextAp<0){
			return -1;
		}

		for(int i = 0; i<numServerCloudlets.size();i++){
			minCost = sumCostFunction(numServerCloudlets.get(i),oldApDevices.get(idNextAp),smartThing);
			if(minCost>=0){
				choose=numServerCloudlets.get(i).getMyId();
				break;
			}
		}
		if(minCost<0){
			return -1;
		}

		for(FogDevice sc: numServerCloudlets){
			sumCost = sumCostFunction(sc,oldApDevices.get(idNextAp),smartThing);
			if(sumCost<0){
				continue;
			}
			if(sumCost<minCost){
				minCost = sumCost;
				choose = sc.getMyId();
			}
		}

		return choose;
	}

	public static double sumCostFunction(FogDevice serverCloudlet, ApDevice nextAp, MobileDevice smartThing){
		double sum = -1;
		List<ApDevice> tempListAps = new ArrayList<>(); // It creates a temporary List to invoke the nextAp
		if(nextAp.getServerCloudlet().equals(serverCloudlet)){
			sum = NetworkTopology.getDelay(smartThing.getId(), nextAp.getId()) 
					+ NetworkTopology.getDelay(nextAp.getId(),nextAp.getServerCloudlet().getId())
					+ (1.0/nextAp.getServerCloudlet().getHost().getAvailableMips())
					+ LatencyByDistance.latencyConnection(nextAp.getServerCloudlet(), smartThing);


		}
		else{
			sum = NetworkTopology.getDelay(smartThing.getId(), nextAp.getId()) 
					+ NetworkTopology.getDelay(nextAp.getId(),nextAp.getServerCloudlet().getId())
					+ 1.0 //router
					+ NetworkTopology.getDelay(nextAp.getServerCloudlet().getId(),serverCloudlet.getId())
					+ (1.0/serverCloudlet.getHost().getAvailableMips())
					+ LatencyByDistance.latencyConnection(serverCloudlet, smartThing);


		}
		return sum;



	}
	*/

	public static boolean isMigrationPoint() {
		return migrationPoint;
	}

	public static void setMigrationPoint(boolean migrationPoint) {
		HandOff.migrationPoint = migrationPoint;
	}

	public static boolean isMigrationZone() {
		return migrationZone;
	}

	public static void setMigrationZone(boolean migrationZone) {
		HandOff.migrationZone = migrationZone;
	}

	public int getLocation() {
		return location;
	}

	public void setLocation(int location) {
		this.location = location;
	}

	/*public ApDevice getCorrentAP() {
		return correntAP;
	}

	public void setCorrentAP(ApDevice correntAP) {
		this.correntAP = correntAP;
	}

	public FogDevice getCorrentServerCloudlet() {
		return correntServerCloudlet;
	}

	public void setCorrentServerCloudlet(FogDevice correntServerCloudlet) {
		this.correntServerCloudlet = correntServerCloudlet;
	}
    
	public MobileDevice getCorrentSmartThing() {
		return correntSmartThing;
	}

	public void setCorrentSmartThing(MobileDevice correntSmartThing) {
		this.correntSmartThing = correntSmartThing;
	}

	public ApDevice getApAvailable() {
		return apAvailable;
	}

	public void setApAvailable(ApDevice apAvailable) {
		this.apAvailable = apAvailable;
	}

	public FogDevice getServerCloudletAvailable() {
		return serverCloudletAvailable;
	}

	public void setServerCloudletAvailable(FogDevice serverCloudletAvailable) {
		this.serverCloudletAvailable = serverCloudletAvailable;
	}
	 */
	public int getFlowDirection() {
		return flowDirection;
	}

	public void setFlowDirection(int flowDirection) {
		this.flowDirection = flowDirection;
	}

	public static List<FogDevice> getGatewaysAvailable() {
		return gatewaysAvailable;
	}

	public static void setGatewaysAvailable(List<FogDevice> agatewaysAvailable) {
		HandOff.gatewaysAvailable = agatewaysAvailable;
	}

	public static List<FogDevice> getServerCloudletsAvailable() {
		return serverCloudletsAvailable;
	}

	public static void setServerCloudletsAvailable(List<FogDevice> serverCloudletsAvailable) {
		HandOff.serverCloudletsAvailable = serverCloudletsAvailable;
	}

	public static int getPolicyReplicaVM() {
		return policyReplicaVM;
	}

	public static void setPolicyReplicaVM(int policyReplicaVM) {
		HandOff.policyReplicaVM = policyReplicaVM;
	}

	public static int getTotalNumOfHandoffs() {
		return totalNumOfHandoffs;
	}

	public static void setTotalNumOfHandoffs(int totalNumOfHandoffs) {
		HandOff.totalNumOfHandoffs = totalNumOfHandoffs;
	}

/*	public static int getTotalNumofUnconnectedSinks() {
		return totalNumofUnconnectedSinks;
	}

	public static void setTotalNumofUnconnectedSinks(int totalNumofUnconnectedNodes) {
		HandOff.totalNumofUnconnectedSinks = totalNumofUnconnectedNodes;
	}
*/



}
