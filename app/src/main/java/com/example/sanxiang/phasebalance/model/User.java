package com.example.sanxiang.phasebalance.model;

public class User 
{
    private String userId;
    private String userName;
    private String routeNumber;
    private String branchNumber;
    private double power;
    private byte currentPhase;
    private boolean isPowerPhase;
    
    public User(String userId, String userName, String routeNumber, String branchNumber, 
                double power, byte currentPhase, boolean isPowerPhase) 
    {
        this.userId = userId;
        this.userName = userName;
        this.routeNumber = routeNumber;
        this.branchNumber = branchNumber;
        this.power = power;
        this.currentPhase = currentPhase;
        this.isPowerPhase = isPowerPhase;
    }
    
    // Getters
    public String getUserId() 
    {
        return userId;
    }
    
    public String getUserName() 
    {
        return userName;
    }
    
    public String getRouteNumber() 
    {
        return routeNumber;
    }
    
    public String getBranchNumber() 
    {
        return branchNumber;
    }
    
    public double getPower() 
    {
        return power;
    }
    
    public byte getCurrentPhase() 
    {
        return currentPhase;
    }
    
    public boolean isPowerPhase() 
    {
        return isPowerPhase;
    }
} 