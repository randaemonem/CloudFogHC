package org.cloudfogHC;
import org.fog.application.AppModule;
import java.util.*; 
import java.lang.*; 
import java.io.*;
public class SortByEmergency implements Comparator<AppModule>{

	    public int compare(AppModule a, AppModule b) 
	    { 
	    	double emergencyA = a.getClassification() * 0.3;
	    	double emergencyB = b.getClassification() * 0.3;
	    	if (emergencyB < emergencyA) return -1;
	        if (emergencyB > emergencyA) return 1;
	        return 0;
	       // return (int)( b.getWeight() - a.getWeight()); 	//descending
	        
	    } 
	
}

