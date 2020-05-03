package org.mobility;
/**
 * @author Marcio Moraes Lopes
 */
public class Policies {
	public static final int Allocation_MBAR = 0;
	public static final int Allocation_HEFT = 1;
	public static final int Allocation_HealthEdge = 2;
	public static final int Allocation_CloudOnly = 3;
	
	/*public static final int Allocation_CloudOnly = 0;
	public static final int Allocation_MBAR = 1;
	public static final int Allocation_HEFT = 2;
	public static final int Allocation_HealthEdge = 3;*/
	
	
	public static final int LOWEST_LATENCY = 0;
	public static final int LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET = 1;
	public static final int LOWEST_DIST_BW_SMARTTING_AP = 2;
	
	public static final int FIXED_MIGRATION_POINT = 0;
	public static final int SPEED_MIGRATION_POINT = 1;
	public static final int FIXED_AP_LOCATION = 0;
	public static final int RANDOM_AP_LOCATION = 1;
	public static final int MIGRATION_COMPLETE_VM = 0;
	public static final int MIGRATION_CONTAINER_VM = 1;
	public static final int LIVE_MIGRATION = 2;
	public static final int ADD = 0;
	public static final int REMOVE = 1;
	public static final int FIXED_SC_LOCATION = 0;
	public static final int RANDOM_SC_LOCATION = 1;

	public static final int RANDOM_STRATEGY = 0;
	public static final int CLASSIFICATION_STRATEGY = 1;
	public static final int MAXRESPONSE_STRATEGY = 2;
	public static final int Mixed = 3;

}
