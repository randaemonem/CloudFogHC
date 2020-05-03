
package org.mobility;
import java.time.Clock;
import java.util.Arrays;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudfogHC.LogMobile;
import org.fog.entities.*;
//import org.fog.vmmobile.constants.*;
/**
* @author Marcio Moraes Lopes
*/


public class Coordinate { //extends Map {

	private static final String ANSI_RED = "\\033[31;1m";
	private double coordX;
	private double coordY;

	public Coordinate(){
	}

	public  boolean isWithinLimitPosition(Coordinate c){
		if (c.getCoordX()>=MaxAndMin.MAX_X||
				c.getCoordX()<0||
				c.getCoordY()>=MaxAndMin.MAX_Y||
				c.getCoordY()<0)
			return false;
		else
			return true;
	}
	public  void desableSmartThing(FogDevice smartThing){
		//	System.out.println("Removing SmartThing: "+smartThing.getId());
		smartThing.setCoord((int) -1, (int)-1);
	}
	public  void newCoordinate(FogDevice sinkMobile, int add){//(pointUSER user,float add)
		double newCoordX = 0, newCoordY = 0;
		if(sinkMobile.getSpeed()!=0){
			double increaseX= (sinkMobile.getCoord().getCoordX()+(sinkMobile.getSpeed()*add));
			double increaseY= (sinkMobile.getCoord().getCoordY()+(sinkMobile.getSpeed()*add));
			double decreaseX= (sinkMobile.getCoord().getCoordX()-(sinkMobile.getSpeed()*add));
			double decreaseY= (sinkMobile.getCoord().getCoordY()-(sinkMobile.getSpeed()*add));
			int direction= sinkMobile.getDirection();
			double oldCoordX = sinkMobile.getCoord().getCoordX();
			double oldCoordY = sinkMobile.getCoord().getCoordY();

			if(decreaseX<0||decreaseY<0||increaseX>=MaxAndMin.MAX_X||increaseY>=MaxAndMin.MAX_Y){//It checks the CoordDevices limits.
				//desableSmartThing(sinkMobile);
				sinkMobile.setDirection(reverseDirection(direction));
				return;
			}

			if(direction==Directions.EAST){
				/*same Y, increase X*/
				sinkMobile.getCoord().setCoordX(increaseX);
				newCoordX = increaseX;
				newCoordY = oldCoordY;
			}
			else if(direction==Directions.WEST){
				/*same Y, decrease X*/
				sinkMobile.getCoord().setCoordX(decreaseX);//next position in the same direction
				newCoordX = decreaseX;
				newCoordY = oldCoordY;
			}
			else if(direction==Directions.SOUTH){//Directions.NORTH){
				/*same X, increase Y*/
				sinkMobile.getCoord().setCoordY(increaseY);//next position in the same direction
				newCoordX = increaseX;
				newCoordY = increaseY;
			}
			else if(direction==Directions.NORTH){//Directions.SOUTH){
				/*same X, decrease Y*/
				sinkMobile.getCoord().setCoordY(decreaseY);
				newCoordX = increaseX;
				newCoordY = decreaseY;
			}
			else if(direction==Directions.SOUTHEAST){//Directions.NORTHEAST){
				/*increase X and Y*/
				sinkMobile.getCoord().setCoordX(increaseX);
				sinkMobile.getCoord().setCoordY(increaseY);
				newCoordX = increaseX;
				newCoordY = increaseY;

			}
			else if(direction==Directions.NORTHWEST){//Directions.SOUTHWEST){
				/*decrease X and Y*/
				sinkMobile.getCoord().setCoordX(decreaseX);
				sinkMobile.getCoord().setCoordY(decreaseY);
				newCoordX = decreaseX;
				newCoordY = decreaseY;

			}
			else if(direction==Directions.SOUTHWEST){//Directions.NORTHWEST){
				/*decrease X increase Y*/
				sinkMobile.getCoord().setCoordX(decreaseX);
				sinkMobile.getCoord().setCoordY(increaseY);
				newCoordX = decreaseX;
				newCoordY = increaseY;

			}
			else if(direction==Directions.NORTHEAST){//Directions.SOUTHEAST){
				/*increase X decrease Y*/
				sinkMobile.getCoord().setCoordX(increaseX);
				sinkMobile.getCoord().setCoordY(decreaseY);
				newCoordX = increaseX;
				newCoordY = decreaseY;
			}
		
		}
	
	//System.out.println("____________________________________________________________________________________________________________________");	
	LogMobile.debug("Coordinate.jav","New coordinates for " + sinkMobile.getName() + " : " + newCoordX + " , " + newCoordY);
	
	}
	
	private int reverseDirection(int oldDirection) {
		int newDirection;
		if(oldDirection<5) newDirection = oldDirection+4;
		else newDirection = oldDirection-4;
		return newDirection;
		
	}
	public double getCoordX() {
		return coordX;
	}

	public void setCoordX(double coordX) {
		this.coordX = coordX;
	}

	public double getCoordY() {
		return coordY;
	}

	public void setCoordY(double coordY) {
		this.coordY = coordY;
	}



}
