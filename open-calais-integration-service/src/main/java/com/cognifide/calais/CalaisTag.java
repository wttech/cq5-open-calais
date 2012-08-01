package com.cognifide.calais;

public class CalaisTag {
	
	public CalaisTag(String name, double relevance, int count) {
		this.name = name;
		this.relevance = relevance;
		this.count = count;
	}
	
	public String getName() {
		return name;
	}
	
	public int getCount() {
		return count;
	}
	
	public double getRelevance() {
		return relevance;
	}
	
	private double relevance;
	private String name;
	private int count;
}
