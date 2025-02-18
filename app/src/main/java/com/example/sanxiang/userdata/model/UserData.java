package com.example.sanxiang.userdata.model;

public class UserData
{
    private String date;
    private String userId;
    private String userName;
    private String routeNumber;
    private String branchNumber;  // 支线编号，0表示主干线，其他表示支线编号
    private String phase;
    private double phaseAPower;
    private double phaseBPower;
    private double phaseCPower;

    // Getters and Setters
    public String getDate()
    {
        return date;
    }

    public void setDate(String date)
    {
        this.date = date;
    }

    public String getUserId()
    {
        return userId;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getRouteNumber()
    {
        return routeNumber;
    }

    public void setRouteNumber(String routeNumber)
    {
        this.routeNumber = routeNumber;
    }

    public String getBranchNumber()
    {
        return branchNumber;
    }

    public void setBranchNumber(String branchNumber)
    {
        this.branchNumber = branchNumber;
    }

    public String getPhase()
    {
        return phase;
    }

    public void setPhase(String phase)
    {
        this.phase = phase;
    }

    public double getPhaseAPower()
    {
        return phaseAPower;
    }

    public void setPhaseAPower(double phaseAPower)
    {
        this.phaseAPower = phaseAPower;
    }

    public double getPhaseBPower()
    {
        return phaseBPower;
    }

    public void setPhaseBPower(double phaseBPower)
    {
        this.phaseBPower = phaseBPower;
    }

    public double getPhaseCPower()
    {
        return phaseCPower;
    }

    public void setPhaseCPower(double phaseCPower)
    {
        this.phaseCPower = phaseCPower;
    }
} 