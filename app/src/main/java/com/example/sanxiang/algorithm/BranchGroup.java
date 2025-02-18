package com.example.sanxiang.algorithm;

import java.util.List;
import java.util.ArrayList;

public class BranchGroup 
{
    private String routeNumber;
    private String branchNumber;
    private List<String> userIds;
    
    public BranchGroup(String routeNumber, String branchNumber) 
    {
        this.routeNumber = routeNumber;
        this.branchNumber = branchNumber;
        this.userIds = new ArrayList<>();
    }
    
    public void addUser(String userId) 
    {
        if (!userIds.contains(userId)) 
        {
            userIds.add(userId);
        }
    }
    
    public boolean containsUser(String userId) 
    {
        return userIds.contains(userId);
    }
    
    public List<String> getUserIds() 
    {
        return new ArrayList<>(userIds);
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
        return String.format("回路%s支线%s（%d个用户）", routeNumber, branchNumber, userIds.size());
    }
} 