package com.jobsearch.joblist.source;

import java.util.ArrayList;
import java.util.List;

/**
 * What to ask the sources for, in neutral terms: the profile's roles and regions and the pipeline's window.
 * Each source translates what it understands and ignores the rest.
 */
public class SourceQuery {

	private List<String> roles = new ArrayList<>();
	private List<Region> regions = new ArrayList<>();
	private int lookbackDays;
	private int maxItems;

	public List<String> roles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	public List<Region> regions() {
		return regions;
	}

	public void setRegions(List<Region> regions) {
		this.regions = regions;
	}

	public int lookbackDays() {
		return lookbackDays;
	}

	public int maxItems() {
		return maxItems;
	}

	public void setWindow(int lookbackDays, int maxItems) {
		this.lookbackDays = lookbackDays;
		this.maxItems = maxItems;
	}
}
