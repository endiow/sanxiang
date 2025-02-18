package com.example.sanxiang.phasebalance.model;

public class BranchGroup 
{
    private String routeNumber;
    private String branchNumber;
    
    public BranchGroup(String routeNumber, String branchNumber) 
    {
        this.routeNumber = routeNumber;
        this.branchNumber = branchNumber;
    }
    
    public String getRouteNumber() 
    {
        return routeNumber;
    }
    
    public String getBranchNumber() 
    {
        return branchNumber;
    }
    
    @Override
    public String toString() 
    {
        return String.format("回路%s支线%s", routeNumber, branchNumber);
    }
} 