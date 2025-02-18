package com.example.sanxiang.prediction.model;

public class PredictionResult
{
    private String userId;
    private String userName;
    private String routeNumber;
    private String routeName;
    private String phase;
    private double predictedPhaseAPower;
    private double predictedPhaseBPower;
    private double predictedPhaseCPower;

    // Getters and Setters
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

    public String getRouteName()
    {
        return routeName;
    }

    public void setRouteName(String routeName)
    {
        this.routeName = routeName;
    }

    public String getPhase()
    {
        return phase;
    }

    public void setPhase(String phase)
    {
        this.phase = phase;
    }

    public double getPredictedPhaseAPower()
    {
        return predictedPhaseAPower;
    }

    public void setPredictedPhaseAPower(double predictedPhaseAPower)
    {
        this.predictedPhaseAPower = predictedPhaseAPower;
    }

    public double getPredictedPhaseBPower()
    {
        return predictedPhaseBPower;
    }

    public void setPredictedPhaseBPower(double predictedPhaseBPower)
    {
        this.predictedPhaseBPower = predictedPhaseBPower;
    }

    public double getPredictedPhaseCPower()
    {
        return predictedPhaseCPower;
    }

    public void setPredictedPhaseCPower(double predictedPhaseCPower)
    {
        this.predictedPhaseCPower = predictedPhaseCPower;
    }
} 