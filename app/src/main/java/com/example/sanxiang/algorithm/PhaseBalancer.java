package com.example.sanxiang.algorithm;

import java.util.*;

public class PhaseBalancer 
{
    private static final int POPULATION_SIZE = 100;
    private static final int MAX_GENERATIONS = 200;
    private static final double MUTATION_RATE = 0.05;  // 降低变异率
    private static final double CROSSOVER_RATE = 0.8;
    
    private List<User> users;
    private List<BranchGroup> branchGroups;
    private Map<String, Double> routeBranchCosts;
    private Map<String, List<Integer>> branchGroupUserIndices;  // 支线组中用户的索引
    
    public PhaseBalancer(List<User> users, List<BranchGroup> branchGroups) 
    {
        this.users = users;
        this.branchGroups = branchGroups;
        this.routeBranchCosts = new HashMap<>();
        this.branchGroupUserIndices = new HashMap<>();
        initializeRouteBranchCosts();
        initializeBranchGroupIndices();
    }
    
    private void initializeRouteBranchCosts() 
    {
        // 首先根据支线信息设置基础调相代价
        Map<String, List<User>> branchUsers = new HashMap<>();
        Map<String, List<User>> routeUsers = new HashMap<>();  // 按回路分组用户
        
        // 按支线和回路分组用户
        for (int i = 0; i < users.size(); i++) 
        {
            User user = users.get(i);
            String branchKey = user.getRouteNumber() + "-" + user.getBranchNumber();
            String routeKey = user.getRouteNumber();
            branchUsers.computeIfAbsent(branchKey, k -> new ArrayList<>()).add(user);
            routeUsers.computeIfAbsent(routeKey, k -> new ArrayList<>()).add(user);
        }
        
        // 设置调相代价：
        // 1. 同一支线上的用户：0.6
        // 2. 同一回路不同支线的用户：1.0
        // 3. 不同回路的用户：1.5
        for (User user : users) 
        {
            String branchKey = user.getRouteNumber() + "-" + user.getBranchNumber();
            String routeKey = user.getRouteNumber();
            int usersInBranch = branchUsers.get(branchKey).size();
            int usersInRoute = routeUsers.get(routeKey).size();
            
            if (usersInBranch > 1) 
            {
                // 同一支线上有多个用户，设置较小的调相代价
                routeBranchCosts.put(branchKey, 0.6);
            }
            else if (usersInRoute > 1) 
            {
                // 同一回路但不同支线，设置中等调相代价
                routeBranchCosts.put(branchKey, 1.0);
            }
            else 
            {
                // 不同回路，设置较高的调相代价
                routeBranchCosts.put(branchKey, 1.5);
            }
        }
    }
    
    private void initializeBranchGroupIndices() 
    {
        if (branchGroups != null && !branchGroups.isEmpty()) 
        {
            // 为每个支线组创建用户索引列表
            for (BranchGroup group : branchGroups) 
            {
                String groupKey = group.getRouteNumber() + "-" + group.getBranchNumber();
                List<Integer> indices = new ArrayList<>();
                
                // 找出属于该支线组的用户索引
                for (int i = 0; i < users.size(); i++) 
                {
                    User user = users.get(i);
                    if (user.getRouteNumber().equals(group.getRouteNumber()) && 
                        user.getBranchNumber().equals(group.getBranchNumber())) 
                    {
                        indices.add(i);
                    }
                }
                
                if (!indices.isEmpty()) 
                {
                    branchGroupUserIndices.put(groupKey, indices);
                }
            }
        }
    }
    
    public Solution optimize() 
    {
        // 初始化种群
        List<Solution> population = initializePopulation();
        
        // 进行遗传算法迭代
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) 
        {
            // 计算适应度
            calculateFitness(population);
            
            // 选择
            List<Solution> selected = selection(population);
            
            // 交叉
            List<Solution> offspring = crossover(selected);
            
            // 变异
            mutation(offspring);
            
            // 更新种群
            population = offspring;
        }
        
        // 返回最优解
        return getBestSolution(population);
    }
    
    private List<Solution> initializePopulation() 
    {
        List<Solution> population = new ArrayList<>();
        
        // 第一个解保持所有用户的当前相位
        Solution keepCurrentSolution = new Solution(users.size());
        for (int j = 0; j < users.size(); j++) 
        {
            keepCurrentSolution.phases[j] = users.get(j).getCurrentPhase();
        }
        population.add(keepCurrentSolution);
        
        // 其他解有80%的概率保持当前相位
        for (int i = 1; i < POPULATION_SIZE; i++) 
        {
            Solution solution = new Solution(users.size());
            for (int j = 0; j < users.size(); j++) 
            {
                User user = users.get(j);
                if (Math.random() < 0.8)  // 80%概率保持当前相位
                {
                    solution.phases[j] = user.getCurrentPhase();
                }
                else 
                {
                    if (user.isPowerPhase()) 
                    {
                        solution.phases[j] = (byte)(1 + new Random().nextInt(3));
                    } 
                    else 
                    {
                        solution.phases[j] = (byte)(new Random().nextInt(4));
                    }
                }
            }
            population.add(solution);
        }
        return population;
    }
    
    private void calculateFitness(List<Solution> population) 
    {
        for (Solution solution : population) 
        {
            double[] phasePowers = new double[3];
            double adjustmentCost = 0.0;
            int changedUsersCount = 0;  // 记录调整的用户数量
            
            // 记录每个支线组是否需要调整
            Map<String, Boolean> groupNeedsAdjustment = new HashMap<>();
            
            // 第一遍：检查支线组是否需要调整
            for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
            {
                String groupKey = entry.getKey();
                List<Integer> indices = entry.getValue();
                boolean needsAdjustment = false;
                
                // 检查支线组中是否有任何用户需要调整
                for (int index : indices) 
                {
                    User user = users.get(index);
                    if (solution.phases[index] != user.getCurrentPhase()) 
                    {
                        needsAdjustment = true;
                        break;
                    }
                }
                
                groupNeedsAdjustment.put(groupKey, needsAdjustment);
                if (needsAdjustment) 
                {
                    changedUsersCount += indices.size();  // 如果支线组需要调整，计入所有用户
                }
            }
            
            // 第二遍：计算适应度
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                int phase = solution.phases[i];
                String key = user.getRouteNumber() + "-" + user.getBranchNumber();
                
                if (phase > 0) 
                {
                    phasePowers[phase - 1] += user.getPower();
                }
                
                // 计算调相代价
                if (phase != user.getCurrentPhase()) 
                {
                    // 如果用户属于支线组，且支线组需要调整
                    if (branchGroupUserIndices.containsKey(key) && groupNeedsAdjustment.get(key)) 
                    {
                        adjustmentCost += routeBranchCosts.get(key);
                    }
                    // 如果用户不属于支线组
                    else if (!branchGroupUserIndices.containsKey(key)) 
                    {
                        adjustmentCost += routeBranchCosts.get(key);
                        changedUsersCount++;  // 增加调整用户计数
                    }
                }
            }
            
            // 计算三相电量差
            double maxPower = Math.max(Math.max(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double minPower = Math.min(Math.min(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double powerDifference = maxPower - minPower;
            
            // 计算调整用户比例（0-1之间）
            double changeRatio = (double)changedUsersCount / users.size();
            
            // 增加对调整用户数量的惩罚
            double changePenalty = Math.pow(changeRatio * 2, 2) * 100;  // 二次方惩罚
            
            // 计算适应度（三相不平衡度权重0.4，调相代价权重0.1，调整用户数量权重0.5）
            solution.fitness = 1.0 / (0.4 * powerDifference + 0.1 * adjustmentCost + 0.5 * changePenalty);
        }
    }
    
    private List<Solution> selection(List<Solution> population) 
    {
        List<Solution> selected = new ArrayList<>();
        double totalFitness = population.stream().mapToDouble(s -> s.fitness).sum();
        
        // 轮盘赌选择
        for (int i = 0; i < POPULATION_SIZE; i++) 
        {
            double random = Math.random() * totalFitness;
            double sum = 0;
            for (Solution solution : population) 
            {
                sum += solution.fitness;
                if (sum >= random) 
                {
                    selected.add(new Solution(solution));
                    break;
                }
            }
        }
        return selected;
    }
    
    private List<Solution> crossover(List<Solution> selected) 
    {
        List<Solution> offspring = new ArrayList<>();
        
        for (int i = 0; i < selected.size() - 1; i += 2) 
        {
            Solution parent1 = selected.get(i);
            Solution parent2 = selected.get(i + 1);
            
            if (Math.random() < CROSSOVER_RATE) 
            {
                // 单点交叉
                int crossPoint = new Random().nextInt(users.size());
                Solution child1 = new Solution(parent1);
                Solution child2 = new Solution(parent2);
                
                // 记录已处理的支线组
                Set<String> processedGroups = new HashSet<>();
                
                for (int j = crossPoint; j < users.size(); j++) 
                {
                    User user = users.get(j);
                    String key = user.getRouteNumber() + "-" + user.getBranchNumber();
                    
                    if (branchGroupUserIndices.containsKey(key) && !processedGroups.contains(key)) 
                    {
                        // 对支线组内的所有用户进行相同的交叉操作
                        processedGroups.add(key);
                        byte phase1 = parent1.phases[j];
                        byte phase2 = parent2.phases[j];
                        
                        for (int index : branchGroupUserIndices.get(key)) 
                        {
                            child1.phases[index] = phase2;
                            child2.phases[index] = phase1;
                        }
                    } 
                    else if (!branchGroupUserIndices.containsKey(key)) 
                    {
                        // 不属于支线组的用户正常交叉
                        child1.phases[j] = parent2.phases[j];
                        child2.phases[j] = parent1.phases[j];
                    }
                }
                
                offspring.add(child1);
                offspring.add(child2);
            } 
            else 
            {
                offspring.add(parent1);
                offspring.add(parent2);
            }
        }
        return offspring;
    }
    
    private void mutation(List<Solution> offspring) 
    {
        for (Solution solution : offspring) 
        {
            // 记录每个支线组的相位变化
            Map<String, Byte> groupPhaseChanges = new HashMap<>();
            
            for (int i = 0; i < users.size(); i++) 
            {
                if (Math.random() < MUTATION_RATE) 
                {
                    User user = users.get(i);
                    String key = user.getRouteNumber() + "-" + user.getBranchNumber();
                    
                    // 检查是否属于支线组
                    if (branchGroupUserIndices.containsKey(key)) 
                    {
                        // 如果支线组还没有决定新相位
                        if (!groupPhaseChanges.containsKey(key)) 
                        {
                            byte newPhase;
                            if (user.isPowerPhase()) 
                            {
                                newPhase = (byte)(1 + new Random().nextInt(3));
                            } 
                            else 
                            {
                                newPhase = (byte)(new Random().nextInt(4));
                            }
                            groupPhaseChanges.put(key, newPhase);
                            
                            // 将同一支线组的所有用户改为相同相位
                            for (int index : branchGroupUserIndices.get(key)) 
                            {
                                solution.phases[index] = newPhase;
                            }
                        }
                    } 
                    else 
                    {
                        // 不属于支线组的用户正常变异
                        if (user.isPowerPhase()) 
                        {
                            solution.phases[i] = (byte)(1 + new Random().nextInt(3));
                        } 
                        else 
                        {
                            solution.phases[i] = (byte)(new Random().nextInt(4));
                        }
                    }
                }
            }
        }
    }
    
    private Solution getBestSolution(List<Solution> population) 
    {
        return Collections.max(population, Comparator.comparingDouble(s -> s.fitness));
    }
    
    // 内部类：解决方案
    public static class Solution 
    {
        private byte[] phases;  // 每个用户的相位
        private double fitness; // 适应度
        
        public Solution(int size) 
        {
            this.phases = new byte[size];
        }
        
        public Solution(Solution other) 
        {
            this.phases = Arrays.copyOf(other.phases, other.phases.length);
            this.fitness = other.fitness;
        }

        // 获取指定索引的相位
        public byte getPhase(int index) 
        {
            return phases[index];
        }

        // 获取适应度
        public double getFitness() 
        {
            return fitness;
        }
    }
} 