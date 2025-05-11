package com.example.sanxiang.phasebalance.model;

import java.io.Serializable;

public class User implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String routeNumber;
    private String branchNumber;
    private double totalPower;
    private double phaseAPower;  // A相电量
    private double phaseBPower;  // B相电量
    private double phaseCPower;  // C相电量
    private byte currentPhase;
    private boolean isPowerPhase;
    
    public User(String userId, String userName, String routeNumber, String branchNumber,
                double totalPower, double phaseAPower, double phaseBPower, double phaseCPower,
                byte currentPhase, boolean isPowerPhase) 
    {
        this.userId = userId;
        this.userName = userName;
        this.routeNumber = routeNumber;
        this.branchNumber = branchNumber;
        this.totalPower = totalPower;
        this.phaseAPower = phaseAPower;
        this.phaseBPower = phaseBPower;
        this.phaseCPower = phaseCPower;
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
    
    public double getTotalPower() 
    {
        return totalPower;
    }
    
    public double getPhaseAPower() 
    {
        return phaseAPower;
    }
    
    public double getPhaseBPower() 
    {
        return phaseBPower;
    }
    
    public double getPhaseCPower() 
    {
        return phaseCPower;
    }
    
    public byte getCurrentPhase() 
    {
        return currentPhase;
    }
    
    public boolean isPowerPhase() 
    {
        return isPowerPhase;
    }
    
    public double getPowerByPhase(byte phase) 
    {
        switch(phase) 
        {
            case 1: return phaseAPower;
            case 2: return phaseBPower;
            case 3: return phaseCPower;
            default: return 0;
        }
    }
} 