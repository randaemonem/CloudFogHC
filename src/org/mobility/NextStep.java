package org.mobility;

import java.util.List;

import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.*;

import org.cloudfogHC.*;
/**
 * @author Marcio Moraes Lopes
 */
public class NextStep {

	public static  void nextStep( List<FogDevice> apDevices, List<FogDevice> sinksMobile, int stepPolicy) {
		FogDevice sm=null;
		Coordinate coordinate = new Coordinate();
		for(int i = 0;i<sinksMobile.size();i++){//It makes the new position according direction and speed
			sm=sinksMobile.get(i);
		
			if((sm.getDirection()!=Directions.NONE))
				coordinate.newCoordinate(sm, stepPolicy);//1 -> It means that only one step 
			if(sm.getCoord().getCoordX()==-1){
			
				
				if(sm.getSourceGateway()==null){
					sinksMobile.remove(sm);
					LogMobile.debug("NextStep.java", sm.getName()+" was removed!");
				}
				else{
					sm.getSourceGateway().setSinks(sm, Policies.REMOVE);//it'll remove the smartThing from gateway-sinks mobile node's set
					LogMobile.debug("NextStep.java", sm.getName()+" was removed!");
					sinksMobile.remove(sm);
				}
			}
		}

	

	}

}



