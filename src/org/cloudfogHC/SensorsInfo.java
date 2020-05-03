package org.cloudfogHC;

public class SensorsInfo {

	int id;
    String sensorType;
    double transmissionTime;
 
    public SensorsInfo(int id, String sensorName, double transmissionTime) {
        this.id = id;
        this.sensorType = sensorName;
        this.transmissionTime = transmissionTime;
    }
    public int getID() {
    	return id;
    }
    public String getSensorType() {
    	return sensorType;
    }
    public double getTransmissionTime() {
    	return transmissionTime;
    }
}
