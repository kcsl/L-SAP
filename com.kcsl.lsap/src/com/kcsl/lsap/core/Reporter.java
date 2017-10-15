package com.kcsl.lsap.core;

import java.util.Date;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.kcsl.lsap.utils.LSAPUtils;

public class Reporter {

	private long analysisStartTime;
	private double analysisProcessingTime;
	private AtlasSet<Node> lockEvents;
	private AtlasSet<Node> unlockEvents;
	private AtlasSet<Node> verifiedLockEvents;
	private AtlasSet<Node> partiallyVerifiedLockEvents;
	private AtlasSet<Node> danglingLockEvents;
	private AtlasSet<Node> onlyDanglingLockEvents;
	private AtlasSet<Node> deadlockedLockEvents;
	private AtlasSet<Node> onlyDeadlockedLockEvents;
	private AtlasSet<Node> interproceduralVerificationLockEvents;
	private AtlasSet<Node> intraproceduralVerificationLockEvents;
	
	public Reporter() {
		LSAPUtils.log("Started at [" + new Date().toString() + "]");
		this.analysisStartTime = System.currentTimeMillis();
		this.lockEvents = new AtlasHashSet<Node>(); 
		this.unlockEvents = new AtlasHashSet<Node>();
		this.verifiedLockEvents = new AtlasHashSet<Node>();
		this.partiallyVerifiedLockEvents = new AtlasHashSet<Node>();
		this.danglingLockEvents = new AtlasHashSet<Node>();
		this.onlyDanglingLockEvents = new AtlasHashSet<Node>();
		this.deadlockedLockEvents = new AtlasHashSet<Node>();
		this.onlyDeadlockedLockEvents = new AtlasHashSet<Node>();
		this.interproceduralVerificationLockEvents = new AtlasHashSet<Node>();
		this.intraproceduralVerificationLockEvents = new AtlasHashSet<Node>();
	}
	
	public void done(){
	    this.analysisProcessingTime = (System.currentTimeMillis() - analysisStartTime)/(60*1000F);
	    LSAPUtils.log("Done in [" + this.analysisProcessingTime + " minutes]!");
	}
	
	public void aggregate(Reporter subReporter){
		this.lockEvents.addAll(subReporter.getLockEvents());
		this.unlockEvents.addAll(subReporter.getUnlockEvents());
		this.verifiedLockEvents.addAll(subReporter.getVerifiedLockEvents());
		this.partiallyVerifiedLockEvents.addAll(subReporter.getPartiallyVerifiedLockEvents());
		this.danglingLockEvents.addAll(subReporter.getDanglingLockEvents());
		this.deadlockedLockEvents.addAll(subReporter.getDeadlockedLockEvents());
		this.interproceduralVerificationLockEvents.addAll(subReporter.getInterproceduralVerificationLockEvents());
		this.intraproceduralVerificationLockEvents.addAll(subReporter.getIntraproceduralVerificationLockEvents());
		this.onlyDeadlockedLockEvents.addAll(subReporter.getOnlyDeadlockedLockEvents());
		this.onlyDanglingLockEvents.addAll(subReporter.getOnlyDanglingLockEvents());
	}
	
	public double getAnalysisProcessingTime(){
		return this.analysisProcessingTime;
	}
	
	public AtlasSet<Node> getLockEvents() {
		return lockEvents;
	}

	public void setLockEvents(AtlasSet<Node> lockEvents) {
		this.lockEvents = lockEvents;
	}

	public AtlasSet<Node> getUnlockEvents() {
		return unlockEvents;
	}

	public void setUnlockEvents(AtlasSet<Node> unlockEvents) {
		this.unlockEvents = unlockEvents;
	}

	public AtlasSet<Node> getPartiallyVerifiedLockEvents() {
		return partiallyVerifiedLockEvents;
	}

	public void setPartiallyVerifiedLockEvents(AtlasSet<Node> partiallyVerifiedLockEvents) {
		this.partiallyVerifiedLockEvents = partiallyVerifiedLockEvents;
	}
	
	public AtlasSet<Node> getVerifiedLockEvents() {
		return verifiedLockEvents;
	}

	public void setVerifiedLockEvents(AtlasSet<Node> verifiedLockEvents) {
		this.verifiedLockEvents = verifiedLockEvents;
	}

	public AtlasSet<Node> getDanglingLockEvents() {
		return danglingLockEvents;
	}

	public void setDanglingLockEvents(AtlasSet<Node> danglingLockEvents) {
		this.danglingLockEvents = danglingLockEvents;
	}

	public AtlasSet<Node> getInterproceduralVerificationLockEvents() {
		return interproceduralVerificationLockEvents;
	}

	public void setInterproceduralVerificationLockEvents(AtlasSet<Node> interproceduralVerificationLockEvents) {
		this.interproceduralVerificationLockEvents = interproceduralVerificationLockEvents;
	}

	public AtlasSet<Node> getIntraproceduralVerificationLockEvents() {
		return intraproceduralVerificationLockEvents;
	}

	public void setIntraproceduralVerificationLockEvents(AtlasSet<Node> intraproceduralVerificationLockEvents) {
		this.intraproceduralVerificationLockEvents = intraproceduralVerificationLockEvents;
	}
	
	public AtlasSet<Node> getDeadlockedLockEvents() {
		return deadlockedLockEvents;
	}

	public void setDeadlockedLockEvents(AtlasSet<Node> deadlockedLockEvents) {
		this.deadlockedLockEvents = deadlockedLockEvents;
	}
	
	public void setOnlyDeadlockedLockEvents(AtlasSet<Node> onlyDeadlockedLockEvents){
		this.onlyDeadlockedLockEvents = onlyDeadlockedLockEvents;
	}
	
	public AtlasSet<Node> getOnlyDeadlockedLockEvents() {
		return onlyDeadlockedLockEvents;
	}

	public void setOnlyDandlingLockEvents(AtlasSet<Node> onlyDanglingLockEvents) {
		this.onlyDanglingLockEvents = onlyDanglingLockEvents;
	}
	
	public AtlasSet<Node> getOnlyDanglingLockEvents(){
		return onlyDanglingLockEvents;
	}
	
	public void printResults(String title){
		LSAPUtils.log("******************************************");
	    LSAPUtils.log("*****************" + title + " STATISTICS***************");
	    LSAPUtils.log("******************************************");
	    LSAPUtils.log("Number of Lock Events: " + this.lockEvents.size());
	    LSAPUtils.log("Number of Unlock Events: " + this.unlockEvents.size());
	    
	    double verifiedPercentage = (((double)this.verifiedLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of Verified Lock Events: " + this.verifiedLockEvents.size() + "\t[" + verifiedPercentage + "%]");
	    LSAPUtils.log("Number of Intra-procedural Cases: " + this.intraproceduralVerificationLockEvents.size());
	    LSAPUtils.log("Number of Inter-procedural Cases: " + this.interproceduralVerificationLockEvents.size());
	    
	    double partiallyVerifiedPercentage = (((double)this.partiallyVerifiedLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of Partially Verified Lock Events: " + this.partiallyVerifiedLockEvents.size() + "\t[" + partiallyVerifiedPercentage + "%]");
	    
	    double notVerifiedPercentage = (((double)this.danglingLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of Dangling Lock Events: " + this.danglingLockEvents.size() + "\t[" + notVerifiedPercentage + "%]");
	    
	    double actualDanglingPercentage = (((double)this.onlyDanglingLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of ONLY Dangling Lock Events: " + this.onlyDanglingLockEvents.size() + "\t[" + actualDanglingPercentage + "%]");
	    
	    double racedPercentage = (((double)this.deadlockedLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of Deadlocked Lock Events: " + this.deadlockedLockEvents.size() + "\t[" + racedPercentage + "%]");
	    
	    double actualRacedPercentage = (((double)this.onlyDeadlockedLockEvents.size()) / ((double) this.lockEvents.size())) * 100.0;
	    LSAPUtils.log("Number of ONLY Deadlocked Lock Events: " + this.onlyDeadlockedLockEvents.size() + "\t[" + actualRacedPercentage + "%]");
	    
	    LSAPUtils.log("******************************************");
	    LSAPUtils.log("******************************************");
	    LSAPUtils.log("******************************************");
	}
}
