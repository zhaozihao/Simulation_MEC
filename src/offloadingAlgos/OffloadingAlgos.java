package offloadingAlgos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import simulation.MobileDevice;
import simulation.WirelessStation;

public class OffloadingAlgos {

	// matrix contains -1: not initialzied; 0: offloading; 1: offload
	private static List<Integer> matrix; 
	
	static{
		matrix = Collections.synchronizedList(new ArrayList<Integer>());
	}
	
	/**
	 * Use Efficient Multi-user Algorithm to make offload decision
	 * @param device : current mobile device
	 * @param port : randomized port number which was used to connect to wireless station
	 */
	public void offloadingDecisionByMEC(MobileDevice device, int port) {
		// get the port which provides best performance
		int bestPort = calculateOverheadForMEC(port, device);
		// set the port 
		device.setTargetPort(bestPort);
		// decide offloading weight according to the status of current device
		setOffloadingWeight(device);
	}

	/**
	 * Use the Dynamic Algorithm to make offload decision
	 * @param device : current mobile device
	 */
	public void offloadingDecisionByDynamic(MobileDevice device) {
		// the matrix is a list of integer which stores decisons for all the tasks
		// to make it consistent, if the device's id is bigger than current list's size,
		// the previous not made decisions should be filled as -1
		if( device.getId() >= matrix.size() ) {
			for( int i = matrix.size(); i <= device.getId(); ++i) {
				matrix.add(-1);		// -1 indicates the decision for task i has not made
			}
		}
		// random(): a 0 - 1 generator which is used to 
		// generate decisions according to decision history;
		// 
		if( matrix.get(device.getId()) == -1) {
			matrix.set(device.getId(), random());
		}
		// use the dynamic algorithm to make decision
		if( matrix.get(device.getId()) == 0 && !isBeneficial(device) ) {
			// offloading is not applicable, set offloading weight to 0
			device.setOffloadWeight(0);
		}
	}

	/**
	 * To see whether a decision is beneficial
	 * @param device
	 * @return
	 */
	public boolean isBeneficial(MobileDevice device) {
		// connectionInfo of the wireless station
		Hashtable<Integer,Integer> connectionInfo = WirelessStation.getConnectionInfo();
		//	get the total time span to complete the task
		double localTimeOverhead = device.getCompletelyLocalySpan();
		//	get the total energy consumption
		double localEnergyOverhead = localTimeOverhead/2;
		double minCloudOverhead = Double.MAX_VALUE;
		int transferTimeSpan = -1;
		int bestPort = -1;
		// compare each channel's overhead with locally computing's, and to find
		// a best channel which gives best performance
		for( int port : WirelessStation.getPorts()) {
			int lossFactor = connectionInfo.get(port) * 2;
			double curOverhead = 6 
					+  ( (double) device.getDataSize() / (double)WirelessStation.getBandWidth()) * lossFactor
					+  ( (double) device.getDataSize() / (double)WirelessStation.getBandWidth()) / 3;
			if( curOverhead < minCloudOverhead) {
				minCloudOverhead = curOverhead;
				transferTimeSpan = device.getDataSize() / WirelessStation.getBandWidth() * lossFactor;
				bestPort = port;
			}
		}
		if( minCloudOverhead < (localTimeOverhead + localEnergyOverhead)) {
			if( transferTimeSpan > 30)	return false;
			device.setTargetPort(bestPort);
			device.setCloudTimeSpan(6);
			device.setTransferTimeSpan(transferTimeSpan ) ;
			device.setOffloadWeight(1);
			return true;
		}
		return false;
	} 
	
	/***
	 * calculate overhead for emu algorithm
	 * @param connectionInfo
	 * @param port
	 * @param device
	 * @return
	 */
	public int calculateOverheadForMEC(int port, MobileDevice device) {
		//	get current connection info
		int currentChannelConnectionNum = WirelessStation.getConnectionInfo().get(port);
		int lossFactor = WirelessStation.getLossFactor();
		int bandWidth = WirelessStation.getBandWidth();
		// get current overhead 
		double currentOverHead = calculateOverhead(lossFactor, currentChannelConnectionNum, bandWidth);
		// get conditional overhead which represents the overhead to let the task use another channel
		double conditionalOverhead = calculateOverhead(lossFactor,currentChannelConnectionNum - 1, bandWidth);
		// calculate other channel's overhead and find a best one
		double[]  connections= new double[WirelessStation.getPorts().length - 1];
		double[]  lossFactors = new double[connections.length];
		double[]  overheads = new double[connections.length];
		int[] ports = new int[overheads.length];
		int index = 0;
		for( int otherPort : WirelessStation.getPorts() ) {
			if( otherPort == port) continue;
//			overheads[index] = WirelessStation.getConnectionInfo().get(otherPort);
			connections[index] = WirelessStation.getConnectionInfo().get(otherPort);
			lossFactors[index] = Math.pow(WirelessStation.getLossFactor(), connections[index]);
			ports[index] = otherPort;
			currentOverHead += calculateOverhead(lossFactor, connections[index],bandWidth);
			index++;
		}
		index = 0;
		for( int i = 0; i < overheads.length; ++i) {
			overheads[i] = conditionalOverhead + calculateOverhead(lossFactor, connections[i]+1, bandWidth);
		}
		double minOverhead = currentOverHead;
		int bestPort = port;
		// get the best channel
//		System.out.println("initialOverhead:   "+currentOverHead);
		for( int i = 0; i < overheads.length; ++i) {
			if( minOverhead > overheads[i]) {
				minOverhead = overheads[i];
				bestPort = ports[i];
			}
		}
		return bestPort;
	}

	public double calculateOverhead(double lossFactor, double connectionNum, double bandWidth) {
		return  (Math.pow((double)lossFactor, (double)connectionNum) * (double)connectionNum) / (double)bandWidth;
	}
	
	public void setOffloadingWeight(MobileDevice device) {
		if( device.getBattery() > 80) 
			device.setOffloadWeight(0.5f);
		else if( device.getBattery() > 60 && device.getBattery() <= 80) 
			device.setOffloadWeight(0.6f);
		else if( device.getBattery() > 40 && device.getBattery() <= 60)
			device.setOffloadWeight(0.7f);
		else if( device.getBattery() > 20 && device.getBattery() <= 40) 
			device.setOffloadWeight(0.8f);
		else 
			device.setOffloadWeight(0.9f);
	}
	
	private int random() {
		Random rand = new Random();
		int oneCount = 0, ZeroCount = 0;
		for( int i : matrix) 
			if( i == 1 ) oneCount ++;
			else if ( i == 0) ZeroCount ++;
		
		int[] nums = new int[oneCount + ZeroCount];
		for( int i = 0; i < ZeroCount; ++i) {
			nums[i] = 1;
		}
		
		return nums.length == 0 ? rand.nextInt(2) : rand.nextInt(nums.length);
	}
}


