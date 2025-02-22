package com.example.sanxiang.phasebalance.algorithm;

import com.example.sanxiang.util.UnbalanceCalculator;
import com.example.sanxiang.phasebalance.model.User;
import com.example.sanxiang.phasebalance.model.BranchGroup;
import java.util.*;

public class PhaseBalancer 
{
    // 基础参数
    private static final int OPTIMIZATION_TIMES = 10;     // 优化次数
    private static final int MAX_RETRY_TIMES = 3;        // 最大重试次数
    private static final double MAX_ACCEPTABLE_UNBALANCE = 15.0;     // 最大可接受不平衡度15%
    
    // 遗传算法参数
    private static final double MUTATION_RATE = 0.01;    // 变异率1%
    private static final double CROSSOVER_RATE = 0.6;    // 交叉率60%
    private static final double BASE_MAX_CHANGE_RATIO = 0.15;  // 基础最大调整用户比例15%
    private static final double MAX_ACCEPTABLE_CHANGE_RATIO = 0.40;  // 最大可接受调整比例40%
    
    // 规模相关参数
    private static final int MIN_POPULATION_SIZE = 100;         // 最小种群大小
    private static final int MAX_POPULATION_SIZE = 400;         // 最大种群大小
    private static final int MIN_GENERATIONS = 500;             // 最小迭代轮数
    private static final int MAX_GENERATIONS = 2000;            // 最大迭代轮数
    private static final int SCALE_THRESHOLD = 1000;            // 规模阈值，超过此值开始调整参数
    
    // 成员变量
    private volatile boolean isTerminated = false;  // 终止标志
    private List<User> users;                      // 用户列表
    private List<BranchGroup> branchGroups;        // 支线组列表
    private Map<String, Double> routeBranchCosts;  // 支线调相代价
    private Map<String, List<Integer>> branchGroupUserIndices;  // 支线组中用户的索引
    private double totalPower;                     // 总功率
    
    public PhaseBalancer(List<User> users, List<BranchGroup> branchGroups) 
    {
        this.users = users;
        this.branchGroups = branchGroups;
        this.routeBranchCosts = new HashMap<>();
        this.branchGroupUserIndices = new HashMap<>();
        
        // 计算总功率
        this.totalPower = users.stream()
            .mapToDouble(User::getTotalPower)
            .sum();
            
        initializeRouteBranchCosts();
        initializeBranchGroupIndices();
    }
    
    // 主要优化方法
    public Solution optimize() 
    {
        try 
        {
            // 根据用户数量动态调整种群大小和迭代轮数
            int userCount = users.size();
            int populationSize = Math.min(MAX_POPULATION_SIZE, 
                Math.max(MIN_POPULATION_SIZE, 
                    MIN_POPULATION_SIZE * (1 + userCount / SCALE_THRESHOLD)));
            
            int generations = Math.min(MAX_GENERATIONS, 
                Math.max(MIN_GENERATIONS, 
                    MIN_GENERATIONS * (1 + userCount / SCALE_THRESHOLD)));
            
            List<Solution> validSolutions = new ArrayList<>();
            int totalAttempts = 0;  // 总尝试次数
            int maxTotalAttempts = OPTIMIZATION_TIMES * MAX_RETRY_TIMES;  // 最大总尝试次数
            
            while (validSolutions.size() < 10 && totalAttempts < maxTotalAttempts && !isTerminated) 
            {
                totalAttempts++;
                
                // 初始化种群
                List<Solution> population = initializePopulation(populationSize);
                Solution bestSolution = null;
                double bestFitness = Double.POSITIVE_INFINITY;
                
                // 迭代优化
                for (int i = 0; !isTerminated && i < generations; i++) 
                {
                    calculateFitness(population);
                    List<Solution> selected = selection(population);
                    List<Solution> offspring = crossover(selected);
                    mutation(offspring);
                    
                    // 对新解进行修复
                    for (Solution solution : offspring) 
                    {
                        repairSolution(solution);
                    }
                    
                    // 更新种群
                    population = offspring;
                    
                    // 获取当前最优解
                    Solution currentBest = getBestSolution(population);
                    if (currentBest.getFitness() < bestFitness) 
                    {
                        bestSolution = new Solution(currentBest);
                        bestFitness = currentBest.getFitness();
                        localSearch(bestSolution);
                    }
                }
                
                if (bestSolution != null) 
                {
                    double[] metrics = calculateSolutionMetrics(bestSolution);
                    double unbalanceRate = metrics[0];
                    
                    if (unbalanceRate < 15.0) 
                    {
                        validSolutions.add(bestSolution);
                    }
                }
            }
            
            return !validSolutions.isEmpty() ? 
                Collections.min(validSolutions, Comparator.comparingDouble(s -> s.fitness)) : null;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return null;
        }
    }
    
    // 初始化方法
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
    
    private List<Solution> initializePopulation(int populationSize) 
    {
        List<Solution> population = new ArrayList<>();
        
        // 第一个解保持所有用户的当前相位
        Solution initialSolution = new Solution(users.size());
        for (int j = 0; j < users.size(); j++) 
        {
            initialSolution.phases[j] = users.get(j).getCurrentPhase();
            initialSolution.moves[j] = 0;  // 初始解没有移动
        }
        population.add(initialSolution);
        
        // 计算初始解的三相电量和不平衡度
        double[] initialPhasePowers = new double[3];
        for (int i = 0; i < users.size(); i++) 
        {
            User user = users.get(i);
            byte phase = initialSolution.phases[i];
            if (phase > 0) 
            {
                if (user.isPowerPhase()) 
                {
                    initialPhasePowers[0] += user.getPhaseAPower();
                    initialPhasePowers[1] += user.getPhaseBPower();
                    initialPhasePowers[2] += user.getPhaseCPower();
                }
                else 
                {
                    initialPhasePowers[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
                }
            }
        }
        
        // 计算初始状态的不平衡度
        double initialUnbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
            initialPhasePowers[0], initialPhasePowers[1], initialPhasePowers[2]
        );
        
        // 计算最大可改变用户数（40%）
        int maxChangeUsers = (int)(users.size() * 0.4);
        
        // 生成其他解
        int attempts = 0;  // 尝试次数计数
        while (population.size() < populationSize && attempts < populationSize * 3) 
        {
            attempts++;
            Solution solution = new Solution(users.size());
            
            // 复制初始解
            for (int i = 0; i < users.size(); i++) 
            {
                solution.phases[i] = initialSolution.phases[i];
                solution.moves[i] = 0;  // 初始化移动次数为0
            }
            
            // 随机选择要改变的用户数量（不超过最大值）
            int changeCount = new Random().nextInt(maxChangeUsers) + 1;
            
            // 清空相位电量统计
            double[] phasePowers = new double[3];
            
            // 随机选择要改变的用户
            List<Integer> userIndices = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) 
            {
                userIndices.add(i);
            }
            Collections.shuffle(userIndices);
            
            // 优先选择功率较大的用户进行调整
            userIndices.sort((a, b) -> Double.compare(
                users.get(b).getTotalPower(),
                users.get(a).getTotalPower()
            ));
            
            for (int i = 0; i < changeCount; i++) 
            {
                int userIndex = userIndices.get(i);
                User user = users.get(userIndex);
                
                if (user.isPowerPhase()) 
                {
                    // 动力相用户可以移动多相（1-2次）
                    byte currentPhase = user.getCurrentPhase();
                    // 80%概率调整
                    if (Math.random() < 0.8) 
                    {
                        int moves = 1 + new Random().nextInt(2); // 随机移动1-2次
                        byte newPhase = currentPhase;
                        for (int move = 0; move < moves; move++) 
                        {
                            newPhase = (byte)(newPhase == 3 ? 1 : newPhase + 1);
                        }
                        solution.phases[userIndex] = newPhase;
                        solution.moves[userIndex] = (byte)moves;  // 记录移动次数
                    }
                } 
                else 
                {
                    // 非动力相用户随机选择相位（排除当前相位）
                    byte currentPhase = user.getCurrentPhase();
                    byte[] possiblePhases = new byte[2];
                    int idx = 0;
                    for (byte p = 1; p <= 3; p++) 
                    {
                        if (p != currentPhase) 
                        {
                            possiblePhases[idx++] = p;
                        }
                    }
                    solution.phases[userIndex] = possiblePhases[new Random().nextInt(2)];
                    solution.moves[userIndex] = 1;  // 普通用户移动次数为1
                }
            }
            
            // 计算相位电量
            for (int j = 0; j < users.size(); j++) 
            {
                User user = users.get(j);
                byte phase = solution.phases[j];
                if (phase > 0) 
                {
                    if (user.isPowerPhase()) 
                    {
                        phasePowers[0] += user.getPhaseAPower();
                        phasePowers[1] += user.getPhaseBPower();
                        phasePowers[2] += user.getPhaseCPower();
                    }
                    else 
                    {
                        phasePowers[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
                    }
                }
            }
            
            // 计算新解的不平衡度
            double unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
                phasePowers[0], phasePowers[1], phasePowers[2]
            );
            
            // 放宽接受条件：允许比初始解略差的解
            if (unbalanceRate <= initialUnbalanceRate * 1.2) 
            {
                population.add(solution);
            }
        }
        
        // 如果生成的解决方案不足，通过变异初始解来填充
        while (population.size() < populationSize) 
        {
            Solution solution = new Solution(initialSolution);
            // 随机选择1-3个用户进行相位调整
            int changeCount = 1 + new Random().nextInt(3);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) 
            {
                indices.add(i);
            }
            Collections.shuffle(indices);
            
            for (int i = 0; i < changeCount; i++) 
            {
                int idx = indices.get(i);
                User user = users.get(idx);
                if (user.isPowerPhase()) 
                {
                    byte moves = (byte)(1 + new Random().nextInt(2));
                    byte newPhase = user.getCurrentPhase();
                    for (int move = 0; move < moves; move++) 
                    {
                        newPhase = (byte)(newPhase == 3 ? 1 : newPhase + 1);
                    }
                    solution.phases[idx] = newPhase;
                    solution.moves[idx] = moves;
                }
                else 
                {
                    byte currentPhase = user.getCurrentPhase();
                    byte newPhase;
                    do 
                    {
                        newPhase = (byte)(1 + new Random().nextInt(3));
                    } while (newPhase == currentPhase);
                    solution.phases[idx] = newPhase;
                    solution.moves[idx] = 1;
                }
            }
            population.add(solution);
        }
        
        return population;
    }
    
    // 遗传算法核心方法
    private void calculateFitness(List<Solution> population) 
    {
        // 计算种群的多样性指标
        Map<String, Integer> solutionFrequency = new HashMap<>();
        for (Solution solution : population) 
        {
            String key = getSolutionKey(solution);
            solutionFrequency.merge(key, 1, Integer::sum);
        }
        
        for (Solution solution : population) 
        {
            double[] phasePowers = new double[3];
            int changedUsersCount = 0;
            
            // 计算三相功率和调整用户数
            for (int i = 0; i < users.size(); i++) 
            {
                User user = users.get(i);
                byte newPhase = solution.phases[i];
                
                // 统计调整用户数
                if (newPhase != user.getCurrentPhase()) 
                {
                    changedUsersCount++;
                }
                
                // 计算三相功率
                if (user.isPowerPhase()) 
                {
                    byte moves = solution.moves[i];
                    if (moves == 1) 
                    {
                        // A->B, B->C, C->A
                        phasePowers[0] += user.getPhaseCPower();
                        phasePowers[1] += user.getPhaseAPower();
                        phasePowers[2] += user.getPhaseBPower();
                    }
                    else if (moves == 2) 
                    {
                        // A->C, B->A, C->B
                        phasePowers[0] += user.getPhaseBPower();
                        phasePowers[1] += user.getPhaseCPower();
                        phasePowers[2] += user.getPhaseAPower();
                    }
                    else 
                    {
                        // 不移动时保持原电量
                        phasePowers[0] += user.getPhaseAPower();
                        phasePowers[1] += user.getPhaseBPower();
                        phasePowers[2] += user.getPhaseCPower();
                    }
                }
                else if (newPhase > 0) 
                {
                    // 非动力相用户直接将原电量转移到新相位
                    phasePowers[newPhase - 1] += user.getPowerByPhase(user.getCurrentPhase());
                }
            }
            
            // 计算不平衡度
            double maxPower = Math.max(Math.max(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double minPower = Math.min(Math.min(phasePowers[0], phasePowers[1]), phasePowers[2]);
            double unbalanceRate = maxPower > 0 ? ((maxPower - minPower) / maxPower) * 100 : 0;
            
            // 计算调整比例
            double changeRatio = (double) changedUsersCount / users.size() * 100;
            
            // 计算调相代价
            double adjustmentCost = calculateAdjustmentCost(solution);
            // 归一化调相代价到0-100范围，使其与其他指标在同一量级
            double normalizedAdjustmentCost = (adjustmentCost / totalPower) * 100;
            
            // 根据不平衡度分层计算适应度（值越小越好）
            if (unbalanceRate > 15.0) 
            {
                // 不平衡度>15%，完全由不平衡度决定
                solution.fitness = unbalanceRate * 1000;  // 乘以1000作为惩罚
            }
            else if (unbalanceRate > 10.0) 
            {
                // 不平衡度10-15%，不平衡度:调整用户数=7:3
                solution.fitness = 0.7 * unbalanceRate + 0.3 * changeRatio + 500;  // 加500作为基础惩罚
            }
            else if (unbalanceRate > 5.0) 
            {
                // 不平衡度5-10%，调整用户数:调相代价=7:3
                solution.fitness = 0.7 * changeRatio + 0.3 * normalizedAdjustmentCost + 200;  // 加200作为基础惩罚
            }
            else 
            {
                // 不平衡度<=5%，完全由调相代价决定
                solution.fitness = normalizedAdjustmentCost;
            }
            
            // 计算解的多样性奖励
            String solutionKey = getSolutionKey(solution);
            int frequency = solutionFrequency.get(solutionKey);
            double diversityReward = 1.0 / (1 + Math.log1p(frequency));  // 解越少见,奖励越大
            
            // 应用多样性奖励（减小适应度值）
            solution.fitness *= (1.0 - 0.1 * diversityReward);  // 最多减少10%的适应度值
        }
    }
    
    private List<Solution> selection(List<Solution> population) 
    {
        try 
        {
            List<Solution> selected = new ArrayList<>();
            
            // 按适应度排序（值越小越好）
            List<Solution> sortedPopulation = new ArrayList<>(population);
            Collections.sort(sortedPopulation, (s1, s2) -> Double.compare(s1.getFitness(), s2.getFitness()));
            
            // 保留最优的30%
            int eliteCount = Math.max(1, population.size() * 3 / 10);
            for (int i = 0; i < eliteCount && i < sortedPopulation.size(); i++) 
            {
                selected.add(new Solution(sortedPopulation.get(i)));
            }
            
            // 使用锦标赛选择填充剩余位置
            while (selected.size() < population.size()) 
            {
                // 随机选择4个解进行锦标赛
                List<Solution> tournament = new ArrayList<>();
                for (int i = 0; i < 4; i++) 
                {
                    tournament.add(sortedPopulation.get(
                        (int)(Math.random() * sortedPopulation.size())
                    ));
                }
                
                // 选择锦标赛中适应度最好的解
                Solution best = tournament.get(0);
                for (Solution s : tournament) 
                {
                    if (s.getFitness() < best.getFitness()) 
                    {
                        best = s;
                    }
                }
                
                selected.add(new Solution(best));
            }
            
            return selected;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return new ArrayList<>(population);
        }
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
                // 创建两个子代
                Solution child1 = new Solution(parent1);
                Solution child2 = new Solution(parent2);
                
                // 记录已处理的支线组
                Set<String> processedGroups = new HashSet<>();
                
                // 按支线组进行交叉
                for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
                {
                    String key = entry.getKey();
                    if (processedGroups.contains(key)) continue;
                    
                    List<Integer> groupIndices = entry.getValue();
                    if (Math.random() < 0.5) 
                    {
                        // 交换整个支线组的相位分配
                        for (int index : groupIndices) 
                        {
                            byte tempPhase = child1.phases[index];
                            byte tempMoves = child1.moves[index];
                            
                            child1.phases[index] = child2.phases[index];
                            child1.moves[index] = child2.moves[index];
                            
                            child2.phases[index] = tempPhase;
                            child2.moves[index] = tempMoves;
                        }
                    }
                    processedGroups.add(key);
                }
                
                // 对于超过调整比例的子代,尝试修复
                repairSolution(child1);
                repairSolution(child2);
                
                offspring.add(child1);
                offspring.add(child2);
            } 
            else 
            {
                offspring.add(new Solution(parent1));
                offspring.add(new Solution(parent2));
            }
        }
        
        // 如果population是奇数,保留最后一个
        if (selected.size() % 2 != 0) 
        {
            offspring.add(new Solution(selected.get(selected.size() - 1)));
        }
        
        return offspring;
    }
    
    private void mutation(List<Solution> offspring) 
    {
        for (Solution solution : offspring) 
        {
            if (Math.random() < MUTATION_RATE) 
            {
                // 记录每个支线组的相位变化
                Map<String, Byte> groupPhaseChanges = new HashMap<>();
                Map<String, Byte> groupMoveChanges = new HashMap<>();
                
                // 获取当前已调整的用户数
                int currentChangedCount = countChangedUsers(solution);
                double maxChangeRatio = getMaxChangeRatio();
                int maxAllowedChanges = (int)(users.size() * maxChangeRatio);
                
                // 如果已达到最大调整比例,执行交换式变异
                if (currentChangedCount >= maxAllowedChanges) 
                {
                    performSwapMutation(solution);
                }
                else 
                {
                    // 否则执行普通变异
                    performNormalMutation(solution, currentChangedCount, maxAllowedChanges);
                }
                
                // 对变异后的解进行局部优化
                localSearch(solution);
            }
        }
    }
    
    private void performSwapMutation(Solution solution) 
    {
        // 获取所有已调整的用户
        List<Integer> changedUsers = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) 
        {
            if (solution.phases[i] != users.get(i).getCurrentPhase()) 
            {
                changedUsers.add(i);
            }
        }
        
        if (changedUsers.size() >= 2) 
        {
            // 随机选择两个已调整的用户交换相位
            int idx1 = new Random().nextInt(changedUsers.size());
            int idx2;
            do 
            {
                idx2 = new Random().nextInt(changedUsers.size());
            } while (idx2 == idx1);
            
            int user1 = changedUsers.get(idx1);
            int user2 = changedUsers.get(idx2);
            
            // 交换相位和移动次数
            byte tempPhase = solution.phases[user1];
            byte tempMoves = solution.moves[user1];
            
            solution.phases[user1] = solution.phases[user2];
            solution.moves[user1] = solution.moves[user2];
            
            solution.phases[user2] = tempPhase;
            solution.moves[user2] = tempMoves;
        }
    }
    
    private void performNormalMutation(Solution solution, int currentChangedCount, int maxAllowedChanges) 
    {
        // 计算还可以调整的用户数
        int remainingChanges = maxAllowedChanges - currentChangedCount;
        
        // 随机选择要变异的支线组
        List<String> groupKeys = new ArrayList<>(branchGroupUserIndices.keySet());
        Collections.shuffle(groupKeys);
        
        for (String key : groupKeys) 
        {
            if (remainingChanges <= 0) break;
            
            List<Integer> indices = branchGroupUserIndices.get(key);
            // 检查该支线组是否已经被调整
            boolean groupChanged = false;
            for (int index : indices) 
            {
                if (solution.phases[index] != users.get(index).getCurrentPhase()) 
                {
                    groupChanged = true;
                    break;
                }
            }
            
            // 如果支线组未被调整,且随机数满足条件,则进行变异
            if (!groupChanged && Math.random() < 0.3) 
            {
                // 随机选择新相位
                byte newPhase = (byte)(1 + new Random().nextInt(3));
                byte moves = 1;
                
                // 检查是否有动力相用户
                boolean hasPowerUser = false;
                for (int index : indices) 
                {
                    if (users.get(index).isPowerPhase()) 
                    {
                        hasPowerUser = true;
                        break;
                    }
                }
                
                if (hasPowerUser) 
                {
                    moves = (byte)(1 + new Random().nextInt(2));
                }
                
                // 应用变异
                for (int index : indices) 
                {
                    if (remainingChanges <= 0) break;
                    
                    User user = users.get(index);
                    if (user.getCurrentPhase() != newPhase) 
                    {
                        solution.phases[index] = newPhase;
                        solution.moves[index] = moves;
                        remainingChanges--;
                    }
                }
            }
        }
    }
    
    private void localSearch(Solution solution) 
    {
        // 获取当前适应度
        double[] currentMetrics = calculateSolutionMetrics(solution);
        double currentUnbalance = currentMetrics[0];
        
        // 尝试改进解
        boolean improved;
        do 
        {
            improved = false;
            
            // 遍历所有已调整的用户
            for (int i = 0; i < users.size(); i++) 
            {
                if (solution.phases[i] != users.get(i).getCurrentPhase()) 
                {
                    // 保存原始相位和移动次数
                    byte originalPhase = solution.phases[i];
                    byte originalMoves = solution.moves[i];
                    
                    // 尝试其他可能的相位
                    for (byte newPhase = 1; newPhase <= 3; newPhase++) 
                    {
                        if (newPhase != originalPhase && newPhase != users.get(i).getCurrentPhase()) 
                        {
                            solution.phases[i] = newPhase;
                            solution.moves[i] = 1;
                            
                            // 计算新的不平衡度
                            double[] newMetrics = calculateSolutionMetrics(solution);
                            if (newMetrics[0] < currentUnbalance) 
                            {
                                currentUnbalance = newMetrics[0];
                                improved = true;
                                break;
                            }
                            else 
                            {
                                // 如果没有改进,恢复原始值
                                solution.phases[i] = originalPhase;
                                solution.moves[i] = originalMoves;
                            }
                        }
                    }
                }
            }
        } while (improved);
    }
    
    // 评估方法
    private double[] calculateSolutionMetrics(Solution solution) 
    {
        double[] phasePowers = new double[3];
        int changedUsersCount = 0;
        
        // 计算三相功率和调整用户数
        for (int i = 0; i < users.size(); i++) 
        {
            User user = users.get(i);
            byte newPhase = solution.phases[i];
            
            // 统计调整用户数
            if (newPhase != user.getCurrentPhase()) 
            {
                changedUsersCount++;
            }
            
            // 计算三相功率
            if (user.isPowerPhase()) 
            {
                byte moves = solution.moves[i];
                if (moves == 1) 
                {
                    // A->B, B->C, C->A
                    phasePowers[0] += user.getPhaseCPower();
                    phasePowers[1] += user.getPhaseAPower();
                    phasePowers[2] += user.getPhaseBPower();
                }
                else if (moves == 2) 
                {
                    // A->C, B->A, C->B
                    phasePowers[0] += user.getPhaseBPower();
                    phasePowers[1] += user.getPhaseCPower();
                    phasePowers[2] += user.getPhaseAPower();
                }
                else 
                {
                    // 不移动时保持原电量
                    phasePowers[0] += user.getPhaseAPower();
                    phasePowers[1] += user.getPhaseBPower();
                    phasePowers[2] += user.getPhaseCPower();
                }
            }
            else if (newPhase > 0) 
            {
                // 非动力相用户直接将原电量转移到新相位
                phasePowers[newPhase - 1] += user.getPowerByPhase(user.getCurrentPhase());
            }
        }
        
        // 计算不平衡度
        double maxPower = Math.max(Math.max(phasePowers[0], phasePowers[1]), phasePowers[2]);
        double minPower = Math.min(Math.min(phasePowers[0], phasePowers[1]), phasePowers[2]);
        double unbalanceRate = maxPower > 0 ? ((maxPower - minPower) / maxPower) * 100 : 0;
        
        // 计算调整比例
        double changeRatio = (double) changedUsersCount / users.size() * 100;
        
        return new double[]{unbalanceRate, changeRatio};
    }
    
    private double calculateAdjustmentCost(Solution solution) 
    {
        double totalCost = 0.0;
        
        // 如果没有支线组信息，返回0
        if (branchGroups == null || branchGroups.isEmpty()) 
        {
            return 0.0;
        }
        
        // 遍历每个支线组
        for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
        {
            String routeBranchKey = entry.getKey();
            List<Integer> userIndices = entry.getValue();
            
            // 检查该支线组是否需要调整
            boolean needsAdjustment = false;
            double totalPowerInBranch = 0.0;
            
            // 计算支线组中需要调整的用户的总功率
            for (int userIndex : userIndices) 
            {
                User user = users.get(userIndex);
                byte newPhase = solution.getPhase(userIndex);
                
                if (newPhase != user.getCurrentPhase()) 
                {
                    needsAdjustment = true;
                    totalPowerInBranch += user.getTotalPower();
                }
            }
            
            // 如果需要调整，计算该支线组的调整代价
            if (needsAdjustment) 
            {
                Double branchCost = routeBranchCosts.get(routeBranchKey);
                if (branchCost != null) 
                {
                    // 调整代价 = 支线代价系数 * 调整功率占比
                    double powerRatio = totalPowerInBranch / totalPower;
                    totalCost += branchCost * powerRatio;
                }
            }
        }
        
        return totalCost;
    }
    
    private double calculateUserContribution(Solution solution, int userIndex) 
    {
        double[] powersBefore = new double[3];
        double[] powersAfter = new double[3];
        
        // 计算移除该用户调整前的三相功率
        for (int i = 0; i < users.size(); i++) 
        {
            if (i == userIndex) continue;
            
            User user = users.get(i);
            byte phase = solution.phases[i];
            
            if (user.isPowerPhase()) 
            {
                byte moves = solution.moves[i];
                if (moves == 1) 
                {
                    powersBefore[0] += user.getPhaseCPower();
                    powersBefore[1] += user.getPhaseAPower();
                    powersBefore[2] += user.getPhaseBPower();
                }
                else if (moves == 2) 
                {
                    powersBefore[0] += user.getPhaseBPower();
                    powersBefore[1] += user.getPhaseCPower();
                    powersBefore[2] += user.getPhaseAPower();
                }
                else 
                {
                    powersBefore[0] += user.getPhaseAPower();
                    powersBefore[1] += user.getPhaseBPower();
                    powersBefore[2] += user.getPhaseCPower();
                }
            }
            else if (phase > 0) 
            {
                powersBefore[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
            }
        }
        
        // 复制到移除后的功率数组
        System.arraycopy(powersBefore, 0, powersAfter, 0, 3);
        
        // 添加该用户调整后的功率
        User user = users.get(userIndex);
        byte phase = solution.phases[userIndex];
        if (user.isPowerPhase()) 
        {
            byte moves = solution.moves[userIndex];
            if (moves == 1) 
            {
                powersAfter[0] += user.getPhaseCPower();
                powersAfter[1] += user.getPhaseAPower();
                powersAfter[2] += user.getPhaseBPower();
            }
            else if (moves == 2) 
            {
                powersAfter[0] += user.getPhaseBPower();
                powersAfter[1] += user.getPhaseCPower();
                powersAfter[2] += user.getPhaseAPower();
            }
        }
        else if (phase > 0) 
        {
            powersAfter[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
        }
        
        // 计算调整前后的不平衡度差异
        double unbalanceBefore = UnbalanceCalculator.calculateUnbalanceRate(
            powersBefore[0], powersBefore[1], powersBefore[2]
        );
        double unbalanceAfter = UnbalanceCalculator.calculateUnbalanceRate(
            powersAfter[0], powersAfter[1], powersAfter[2]
        );
        
        // 返回该用户调整对不平衡度的改善程度
        return unbalanceBefore - unbalanceAfter;
    }
    
    private double getCurrentUnbalanceRate() 
    {
        double[] currentPhasePowers = new double[3];
        for (User user : users) 
        {
            currentPhasePowers[0] += user.getPhaseAPower();
            currentPhasePowers[1] += user.getPhaseBPower();
            currentPhasePowers[2] += user.getPhaseCPower();
        }
        
        double maxPower = Math.max(Math.max(currentPhasePowers[0], currentPhasePowers[1]), currentPhasePowers[2]);
        double minPower = Math.min(Math.min(currentPhasePowers[0], currentPhasePowers[1]), currentPhasePowers[2]);
        return maxPower > 0 ? ((maxPower - minPower) / maxPower) * 100 : 0;
    }
    
    // 工具方法
    private void repairSolution(Solution solution) 
    {
        int changedCount = countChangedUsers(solution);
        int maxAllowedChanges = (int)(users.size() * getMaxChangeRatio());
        
        if (changedCount > maxAllowedChanges) 
        {
            // 计算每个改变用户的不平衡度贡献
            List<UserContribution> contributions = new ArrayList<>();
            
            for (int i = 0; i < users.size(); i++) 
            {
                if (solution.phases[i] != users.get(i).getCurrentPhase()) 
                {
                    double contribution = calculateUserContribution(solution, i);
                    contributions.add(new UserContribution(i, contribution));
                }
            }
            
            // 按贡献排序(贡献越大越应该保留)
            Collections.sort(contributions);
            
            // 恢复改变最少的用户,直到满足最大调整比例
            for (int i = contributions.size() - 1; i >= maxAllowedChanges; i--) 
            {
                int userIndex = contributions.get(i).userIndex;
                solution.phases[userIndex] = users.get(userIndex).getCurrentPhase();
                solution.moves[userIndex] = 0;
            }
        }
    }
    
    private Solution getBestSolution(List<Solution> population) 
    {
        return Collections.min(population, Comparator.comparingDouble(s -> s.fitness));
    }
    
    private String getSolutionKey(Solution solution) 
    {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < solution.phases.length; i++) 
        {
            if (solution.phases[i] != users.get(i).getCurrentPhase()) 
            {
                key.append(i).append(':').append(solution.phases[i]).append(';');
            }
        }
        return key.toString();
    }
    
    private int countChangedUsers(Solution solution) 
    {
        int count = 0;
        for (int i = 0; i < users.size(); i++) 
        {
            if (solution.phases[i] != users.get(i).getCurrentPhase()) 
            {
                count++;
            }
        }
        return count;
    }
    
    private double getMaxChangeRatio() 
    {
        double currentUnbalance = getCurrentUnbalanceRate();
        
        if (currentUnbalance <= 10.0) 
        {
            // 不平衡度较小时，使用基础调整比例
            return BASE_MAX_CHANGE_RATIO;
        } 
        else if (currentUnbalance >= 30.0) 
        {
            // 不平衡度很大时，使用最大可接受调整比例
            return MAX_ACCEPTABLE_CHANGE_RATIO;
        } 
        else 
        {
            // 不平衡度在10%-30%之间时，使用指数增长
            // 将不平衡度映射到[0,1]区间
            double x = (currentUnbalance - 10.0) / (30.0 - 10.0);
            // 使用指数函数：y = a * (e^(bx) - 1) / (e^b - 1)
            // 其中a是最大增长量(0.35-0.15=0.2)，b是增长系数(取2.5)
            double b = 2.5;  // 控制指数增长的快慢
            double growthRange = MAX_ACCEPTABLE_CHANGE_RATIO - BASE_MAX_CHANGE_RATIO;
            double ratio = (Math.exp(b * x) - 1) / (Math.exp(b) - 1);
            return BASE_MAX_CHANGE_RATIO + growthRange * ratio;
        }
    }
    
    // 控制方法
    public void terminate() 
    {
        isTerminated = true;
    }
    
    public void reset() 
    {
        isTerminated = false;
    }
    
    // 内部类定义
    public static class Solution 
    {
        private byte[] phases;  // 每个用户的相位
        private byte[] moves;   // 每个用户的移动次数
        private double fitness; // 适应度
        
        public Solution(int size) 
        {
            this.phases = new byte[size];
            this.moves = new byte[size];
        }
        
        public Solution(Solution other) 
        {
            this.phases = Arrays.copyOf(other.phases, other.phases.length);
            this.moves = Arrays.copyOf(other.moves, other.moves.length);
            this.fitness = other.fitness;
        }

        // 获取指定索引的相位
        public byte getPhase(int index) 
        {
            return phases[index];
        }

        // 获取指定索引的移动次数
        public byte getMoves(int index) 
        {
            return moves[index];
        }

        // 获取适应度
        public double getFitness() 
        {
            return fitness;
        }
    }
    
    private static class UserContribution implements Comparable<UserContribution> 
    {
        int userIndex;
        double contribution;
        
        UserContribution(int userIndex, double contribution) 
        {
            this.userIndex = userIndex;
            this.contribution = contribution;
        }
        
        @Override
        public int compareTo(UserContribution other) 
        {
            return Double.compare(this.contribution, other.contribution);
        }
    }
    
    private static class SolutionWithMetrics 
    {
        Solution solution;
        double unbalanceRate;
        double changeRatio;
        
        SolutionWithMetrics(Solution solution, double unbalanceRate, double changeRatio) 
        {
            this.solution = solution;
            this.unbalanceRate = unbalanceRate;
            this.changeRatio = changeRatio;
        }
    }
} 