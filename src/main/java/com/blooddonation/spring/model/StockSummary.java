package com.blooddonation.spring.model;

public class StockSummary {
    private String bloodGroup;
    private String component;
    private String branch;
    private long count;
    private String status;

    public StockSummary(String bloodGroup, String component, String branch, long count, String status) {
        this.bloodGroup = bloodGroup;
        this.component = component;
        this.branch = branch;
        this.count = count;
        this.status = status;
    }

    public String getBloodGroup() { return bloodGroup; }
    public String getComponent() { return component; }
    public String getBranch() { return branch; }
    public long getCount() { return count; }
    public String getStatus() { return status; }
}
