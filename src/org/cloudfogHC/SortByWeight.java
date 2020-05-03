package org.cloudfogHC;
import org.fog.application.AppModule;
import java.util.*; 
import java.lang.*; 
import java.io.*;
public class SortByWeight implements Comparator<AppModule>{

	    public int compare(AppModule a, AppModule b) 
	    { 
	    	if (b.getWeight() < a.getWeight()) return -1;
	        if (b.getWeight() > a.getWeight()) return 1;
	        return 0;
	       // return (int)( b.getWeight() - a.getWeight()); 	//descending
	        
	    } 
	
}

