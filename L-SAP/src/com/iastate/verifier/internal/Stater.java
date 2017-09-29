package com.iastate.verifier.internal;

import java.util.Date;
import java.util.HashSet;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;

public class Stater {

	private long startTime;
	
	private double processingTime;
	
	private HashSet<GraphElement> e1Events;
	
	private HashSet<GraphElement> e2Events;
	
	/**
	 * The count of L events that are verified with U on every execution path
	 */
	private HashSet<GraphElement> verifiedE1Events;
	
	/**
	 * The count of L events that are verified with U on some execution paths
	 */
	private HashSet<GraphElement> partiallyVerifiedE1Events;
	
	/**
	 * The count of L events that are not verified with any U on every execution paths
	 */
	private HashSet<GraphElement> notVerifiedE1Events;
	private HashSet<GraphElement> actualNotVerifiedE1Events;
	
	/**
	 * The count of L events that are followed by another L event causing a race condition
	 */
	private HashSet<GraphElement> racedE1Events;
	private HashSet<GraphElement> actualRacedE1Events;
	
	private HashSet<GraphElement> interproceduralVerification;
	
	private HashSet<GraphElement> intraproceduralVerification;
	
	public Stater() {
		this.startTime = System.currentTimeMillis();
		Utils.debug(0, "Started at [" + new Date().toString() + "]");
		
		this.e1Events = new HashSet<GraphElement>(); 
		this.e2Events = new HashSet<GraphElement>();
		this.verifiedE1Events = new HashSet<GraphElement>();
		this.partiallyVerifiedE1Events = new HashSet<GraphElement>();
		this.notVerifiedE1Events = new HashSet<GraphElement>();
		this.actualNotVerifiedE1Events = new HashSet<GraphElement>();
		this.racedE1Events = new HashSet<GraphElement>();
		this.actualRacedE1Events = new HashSet<GraphElement>();
		this.interproceduralVerification = new HashSet<GraphElement>();
		this.intraproceduralVerification = new HashSet<GraphElement>();
	}
	
	public void done(){
	    this.processingTime = (System.currentTimeMillis() - startTime)/(60*1000F);
	    Utils.debug(0, "Done in [" + this.processingTime + " minutes]!");
	}
	
	public void aggregate(Stater subStats){
		this.e1Events.addAll(subStats.getE1Events());
		this.e2Events.addAll(subStats.getE2Events());
		this.verifiedE1Events.addAll(subStats.getVerifiedE1Events());
		this.partiallyVerifiedE1Events.addAll(subStats.getPartiallyVerifiedE1Events());
		this.notVerifiedE1Events.addAll(subStats.getNotVerifiedE1Events());
		this.racedE1Events.addAll(subStats.getRacedE1Events());
		this.interproceduralVerification.addAll(subStats.getInterproceduralVerification());
		this.intraproceduralVerification.addAll(subStats.getIntraproceduralVerification());
		this.actualRacedE1Events.addAll(subStats.getActualRacedE1Events());
		this.actualNotVerifiedE1Events.addAll(subStats.getActualNotVerifiedE1Events());
	}
	
	public double getProcessingTime(){
		return this.processingTime;
	}
	
	public HashSet<GraphElement> getE1Events() {
		return e1Events;
	}

	public void setE1Events(HashSet<GraphElement> l) {
		this.e1Events = l;
	}

	public HashSet<GraphElement> getE2Events() {
		return e2Events;
	}

	public void setE2Events(HashSet<GraphElement> l) {
		this.e2Events = l;
	}

	public HashSet<GraphElement> getPartiallyVerifiedE1Events() {
		return partiallyVerifiedE1Events;
	}

	public void setPartiallyVerifiedE1Events(HashSet<GraphElement> l) {
		this.partiallyVerifiedE1Events = l;
	}
	
	public HashSet<GraphElement> getVerifiedE1Events() {
		return verifiedE1Events;
	}

	public void setVerifiedE1Events(HashSet<GraphElement> verifiedLEvents) {
		this.verifiedE1Events = verifiedLEvents;
	}

	public HashSet<GraphElement> getNotVerifiedE1Events() {
		return notVerifiedE1Events;
	}

	public void setNotVerifiedE1Events(HashSet<GraphElement> l) {
		this.notVerifiedE1Events = l;
	}

	public HashSet<GraphElement> getInterproceduralVerification() {
		return interproceduralVerification;
	}

	public void setInterproceduralVerification(HashSet<GraphElement> interproceduralVerification) {
		this.interproceduralVerification = interproceduralVerification;
	}

	public HashSet<GraphElement> getIntraproceduralVerification() {
		return intraproceduralVerification;
	}

	public void setIntraproceduralVerification(HashSet<GraphElement> intraproceduralVerification) {
		this.intraproceduralVerification = intraproceduralVerification;
	}
	
	public HashSet<GraphElement> getRacedE1Events() {
		return racedE1Events;
	}

	public void setRacedE1Events(HashSet<GraphElement> l) {
		this.racedE1Events = l;
	}
	
	public HashSet<GraphElement> getActualNotVerifiedE1Events() {
		return actualNotVerifiedE1Events;
	}

	public void setActualNotVerifiedE1Events(HashSet<GraphElement> actualNotVerifiedE1Events) {
		this.actualNotVerifiedE1Events = actualNotVerifiedE1Events;
	}

	public HashSet<GraphElement> getActualRacedE1Events() {
		return actualRacedE1Events;
	}

	public void setActualRacedE1Events(HashSet<GraphElement> actualRacedE1Events) {
		this.actualRacedE1Events = actualRacedE1Events;
	}
	
	public void printResults(String title){
	    Utils.debug(0, "******************************************");
	    Utils.debug(0, "*****************" + title + " STATISTICS***************");
	    Utils.debug(0, "******************************************");
	    
	    Utils.debug(0, "Number of E1 Events: " + this.e1Events.size());
	    Utils.debug(0, "Number of E2 Events: " + this.e2Events.size());
	    
	    double verifiedPercentage = (((double)this.verifiedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Verified E1 Events: " + this.verifiedE1Events.size() + "\t[" + verifiedPercentage + "%]");
	    Utils.debug(0, "Number of Intra-procedural Cases: " + this.intraproceduralVerification.size());
	    Utils.debug(0, "Number of Inter-procedural Cases: " + this.interproceduralVerification.size());
	    
	    double partiallyVerifiedPercentage = (((double)this.partiallyVerifiedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Partially Verified E1 Events: " + this.partiallyVerifiedE1Events.size() + "\t[" + partiallyVerifiedPercentage + "%]");
	    
	    double notVerifiedPercentage = (((double)this.notVerifiedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Dangling E1 Events: " + this.notVerifiedE1Events.size() + "\t[" + notVerifiedPercentage + "%]");
	    
	    double actualDanglingPercentage = (((double)this.actualNotVerifiedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Actual Dangling E1 Events: " + this.actualNotVerifiedE1Events.size() + "\t[" + actualDanglingPercentage + "%]");
	    
	    double racedPercentage = (((double)this.racedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Raced E1 Events: " + this.racedE1Events.size() + "\t[" + racedPercentage + "%]");
	    
	    double actualRacedPercentage = (((double)this.actualRacedE1Events.size()) / ((double) this.e1Events.size())) * 100.0;
	    Utils.debug(0, "Number of Actual Raced E1 Events: " + this.actualRacedE1Events.size() + "\t[" + actualRacedPercentage + "%]");
	    
	    Utils.debug(0, "******************************************");
	    Utils.debug(0, "******************************************");
	    Utils.debug(0, "******************************************");
	}
}
