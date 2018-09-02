package com.kcsl.lsap.reporting;

import java.util.Date;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.kcsl.lsap.utils.LSAPUtils;

/**
 * A class for reporting verification statistics.
 */
public class Reporter {

	/**
	 * The start time for the analysis.
	 */
	private long analysisStartTime;
	
	/**
	 * The total processing time for the whole analysis.
	 */
	private double analysisProcessingTime;
	
	/**
	 * A list of {@link Node}s calling lock.
	 */
	private AtlasSet<Node> lockEvents;
	
	/**
	 * A list of {@link Node}s calling unlock.
	 */
	private AtlasSet<Node> unlockEvents;
	
	/**
	 * A list of verified lock {@link Node}s.
	 */
	private AtlasSet<Node> verifiedLockEvents;
	
	/**
	 * A list of partially verified lock {@link Node}s.
	 */
	private AtlasSet<Node> partiallyVerifiedLockEvents;
	
	/**
	 * A list of dangling lock {@link Node}s.
	 */
	private AtlasSet<Node> danglingLockEvents;
	
	/**
	 * A list of only dangling lock {@link Node}s.
	 */
	private AtlasSet<Node> onlyDanglingLockEvents;
	
	/**
	 * A list of deadlocked lock {@link Node}s.
	 */
	private AtlasSet<Node> deadlockedLockEvents;
	
	/**
	 * A list of only deadlocked lock {@link Node}s.
	 */
	private AtlasSet<Node> onlyDeadlockedLockEvents;
	
	/**
	 * A list of verified lock {@link Node}s that is performed interprocedually.
	 */
	private AtlasSet<Node> interproceduralVerificationLockEvents;
	
	/**
	 * A list of verified lock {@link Node}s that is performed intraprocedually.
	 */
	private AtlasSet<Node> intraproceduralVerificationLockEvents;
	
	/**
	 * A {@link String} to be used for the title of this statistics.
	 */
	private String reportTitle;
	
	/**
	 * Constructs a new instance of {@link Reporter}.
	 */
	public Reporter(String reportTile) {
		LSAPUtils.log("Started at [" + new Date().toString() + "]");
		this.reportTitle = reportTile;
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
	
	/**
	 * Terminates the analysis and logs the results.
	 */
	public void done(){
	    this.analysisProcessingTime = (System.currentTimeMillis() - analysisStartTime)/(60*1000F);
		LSAPUtils.log("******************************************");
	    LSAPUtils.log("*****************" + this.reportTitle + " Statistics***************");
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
	    LSAPUtils.log("Done in [" + this.analysisProcessingTime + " minutes]!");
	}
	
	/**
	 * Aggregates the analysis results from <code>subReporter</code> to this instance of {@link Reporter}.
	 * 
	 * @param subReporter An instance of {@link Reporter} to be appended.
	 */
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
	
}
