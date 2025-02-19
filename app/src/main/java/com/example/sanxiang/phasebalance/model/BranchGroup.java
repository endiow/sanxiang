package com.example.sanxiang.phasebalance.model;

public class BranchGroup 
{
    private String routeNumber;
    private String branchNumber;
    private int userCount;
    
    public BranchGroup(String routeNumber, String branchNumber) 
    {
        this.routeNumber = routeNumber;
        this.branchNumber = branchNumber;
        this.userCount = 0;
    }
    
    public String getRouteNumber() 
    {
        return routeNumber;
    }
    
    public String getBranchNumber() 
    {
        return branchNumber;
    }
    
    public void setUserCount(int userCount) 
    {
        this.userCount = userCount;
    }
    
    public int getUserCount() 
    {
        return userCount;
    }
    
    @Override
    public String toString() 
    {
        if (userCount > 0) 
        {
            return String.format("回路%s支线%s（%d个用户调整）", routeNumber, branchNumber, userCount);
        } 
        else 
        {
            return String.format("回路%s支线%s", routeNumber, branchNumber);
        }
    }
} 