package com.kcsl.lsap.verifier;

public enum VerificationResult {
	
	/**
	 * The lock instance is matched with an unlock on every path.
	 */
	PAIRED("PAIRED"),
	
	/**
	 * The lock instance is matched with an unlock on one path and not matched with unlock on other paths.
	 */
	PARTIALLY_PAIRED("PARTIALLY"),
	
	/**
	 * The lock instance is matched with another lock instance on a single path.
	 */
	DEADLOCK("DEADLOCK"),
	
	/**
	 * The lock instance is not matched with any other lock instance of unlock instance.
	 */
	UNPAIRED("UNPAIRED"),
	
	/**
	 * The verification is no conclusive on this lock instance.
	 */
	ERROR("ERROR");
	
	private String statusString;
	
	VerificationResult(String statusString){
		this.statusString = statusString;
	}
	
	public String toString(){
		return this.statusString;
	}
}