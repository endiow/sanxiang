package com.example.sanxiang.phasebalance.algorithm;

import android.util.Log;
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
    private static final double GROUP_EXCHANGE_THRESHOLD = 0.7; // 支线组交换阈值：70%
    
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

    // 初始化支线调相代价
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

    //初始化支线组索引
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


    //---------------------------------优化主函数---------------------------------
    public Solution optimize() 
    {
        try 
        {
            // 增加最大重试次数，确保能找到满足条件的解
            final int MAX_TOTAL_ATTEMPTS = 3; // 最大总尝试次数
            
            for (int attempt = 0; attempt < MAX_TOTAL_ATTEMPTS && !isTerminated; attempt++) 
            {
                Log.d("PhaseBalancer", String.format("开始第%d次优化尝试", attempt + 1));
                
                // 根据用户数量动态调整种群大小和迭代轮数
                int userCount = users.size();
                int populationSize = Math.min(MAX_POPULATION_SIZE, 
                    Math.max(MIN_POPULATION_SIZE, 
                        MIN_POPULATION_SIZE * (1 + userCount / SCALE_THRESHOLD)));
                
                // 固定迭代次数为1000
                int generations = 1000;
                
                // 初始化全局最优解
                Solution globalBestSolution = null;
                double globalBestFitness = Double.POSITIVE_INFINITY;
                
                // 跟踪遇到的所有不平衡度小于15%的解
                List<Solution> validSolutions = new ArrayList<>();
                
                // 初始化种群
                List<Solution> population = initializePopulation(populationSize);
                
                // 检查初始化是否成功
                if (population == null) 
                {
                    Log.d("PhaseBalancer", "种群初始化失败，尝试重新初始化");
                    continue; // 尝试重新初始化种群
                }
                
                // 设置提前终止的目标适应度值
                double targetFitness = 115;  
                
                // 迭代优化
                for (int i = 0; !isTerminated && i < generations; i++) 
                {
                    calculateFitness(population);    //计算适应度
                    List<Solution> selected = selection(population);  //选择
                    List<Solution> offspring = crossover(selected);  //交叉
                    mutation(offspring);  //变异
                    
                    // 对新解进行修复
                    for (Solution solution : offspring) 
                    {
                        repairSolution(solution);
                    }
                    
                    // 更新种群
                    population = offspring;
                    
                    // 获取当前最优解
                    Solution currentBest = getBestSolution(population);
                    
                    // 局部搜索
                    localSearch(currentBest);
                    
                    // 重新计算适应度
                    calculateFitness(Arrays.asList(currentBest));
                    
                    // 检查是否为有效解（不平衡度小于15%）
                    if (currentBest.getUnbalanceRate() < MAX_ACCEPTABLE_UNBALANCE) 
                    {
                        // 将不平衡度小于15%的解加入有效解列表
                        validSolutions.add(new Solution(currentBest));
                        
                        // 更新全局最优解
                        if (currentBest.getFitness() < globalBestFitness) 
                        {
                            globalBestSolution = new Solution(currentBest);
                            globalBestFitness = currentBest.getFitness();
                            
                            // 如果适应度已达到目标值，提前终止迭代
                            if (globalBestFitness <= targetFitness) 
                            {
                                Log.d("PhaseBalancer", String.format(
                                    "第%d代找到满足条件的解，适应度: %.2f, 不平衡度: %.2f%%",
                                    i, globalBestFitness, globalBestSolution.getUnbalanceRate()
                                ));
                                break;
                            }
                        }
                    }
                    
                    // 每100代输出日志
                    if (i % 100 == 0) 
                    {
                        Log.d("PhaseBalancer", String.format(
                            "第%d代 - 当前最优适应度: %.2f, 不平衡度: %.2f%%",
                            i, currentBest.getFitness(), currentBest.getUnbalanceRate()
                        ));
                    }
                }
                
                // 检查是否找到了有效解
                if (!validSolutions.isEmpty()) 
                {
                    // 从有效解中选择适应度最小的解
                    Solution bestSolution = Collections.min(validSolutions, 
                        Comparator.comparingDouble(s -> s.fitness));
                    
                    Log.d("PhaseBalancer", String.format(
                        "优化完成，找到满足条件的解，适应度: %.2f, 不平衡度: %.2f%%",
                        bestSolution.getFitness(), bestSolution.getUnbalanceRate()
                    ));
                    
                    return bestSolution;
                }
                
                Log.d("PhaseBalancer", String.format(
                    "第%d次尝试未找到满足条件的解，尝试重新优化", attempt + 1
                ));
            }
            
            Log.d("PhaseBalancer", "所有优化尝试都失败，未找到满足条件的解");
            return null;  // 所有尝试都失败，返回null
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return null;
        }
    }

    // 获取最佳解
    private Solution getBestSolution(List<Solution> population)
    {
        return Collections.min(population, Comparator.comparingDouble(s -> s.fitness));
    }

    //---------------------------------初始化种群---------------------------------
    private List<Solution> initializePopulation(int populationSize) 
    {
        // 创建种群池，存储所有重试中的有效解
        List<Solution> solutionPool = new ArrayList<>();
        int requiredValidCount = Math.max(1, (int)(populationSize * 0.3)); // 期望有30%的有效解
        int maxRetries = 5; // 最大重试次数
        List<Solution> lastPopulation = null; // 保存最后一次的种群
        
        // 进行多次重试，累积有效解
        for(int retryCount = 0; retryCount < maxRetries; retryCount++) 
        {
            List<Solution> currentPopulation = new ArrayList<>();
            
            // 第一个解保持所有用户的当前相位
            Solution initialSolution = new Solution(users.size());
            for (int j = 0; j < users.size(); j++) 
            {
                initialSolution.phases[j] = users.get(j).getCurrentPhase();
                initialSolution.moves[j] = 0;  // 初始解没有移动
            }
            
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

            // 根据初始不平衡度设置接受阈值
            double acceptanceThreshold;
            if (initialUnbalanceRate > 40.0) 
            {
                // 初始不平衡度很高，要求新解必须显著改善
                acceptanceThreshold = initialUnbalanceRate * 0.7;  // 要求至少降低30%
            } 
            else if (initialUnbalanceRate > 25.0) 
            {
                // 初始不平衡度高，要求新解有明显改善
                acceptanceThreshold = initialUnbalanceRate * 0.8;  // 要求至少降低20%
            }
            else if (initialUnbalanceRate > 15.0) 
            {
                // 初始不平衡度中等，要求新解有一定改善
                acceptanceThreshold = initialUnbalanceRate * 0.9;  // 要求至少降低10%
            }
            else 
            {
                // 初始不平衡度已经较低，允许适当波动以保持多样性
                acceptanceThreshold = Math.min(initialUnbalanceRate * 1.5, 15.0);  // 允许最多增加50%，但不超过15%
            }
            
            // 计算最大可改变用户数（根据初始不平衡度动态调整）
            int maxChangeUsers;
            if (initialUnbalanceRate <= 15.0) 
            {
                // 不平衡度较小时，使用较小的调整范围
                maxChangeUsers = (int)(users.size() * 0.2);
            } 
            else if (initialUnbalanceRate <= 25.0) 
            {
                // 不平衡度中等时，使用中等的调整范围
                maxChangeUsers = (int)(users.size() * 0.3);
            } 
            else 
            {
                // 不平衡度较大时，使用较大的调整范围
                maxChangeUsers = (int)(users.size() * 0.4);
            }
            
            // 添加初始解并检查是否为有效解
            if(initialUnbalanceRate < MAX_ACCEPTABLE_UNBALANCE) 
            {
                // 初始解有效，加入种群池
                solutionPool.add(new Solution(initialSolution));
            }
            currentPopulation.add(initialSolution);
            
            // 继续生成解直到达到种群大小
            while(currentPopulation.size() < populationSize) 
            {
                Solution additionalSolution = new Solution(users.size());
                
                // 复制初始解
                for (int i = 0; i < users.size(); i++) 
                {
                    additionalSolution.phases[i] = initialSolution.phases[i];
                    additionalSolution.moves[i] = 0;  // 初始化移动次数为0
                }
                
                // 随机选择要改变的用户数量（在最小值和最大值之间）
                int minChangeUsers = (int)(users.size() * (initialUnbalanceRate > 25.0 ? 0.1 : 0.05));
                int changeCount = minChangeUsers + new Random().nextInt(maxChangeUsers - minChangeUsers + 1);
                
                // 清空相位电量统计
                Arrays.fill(additionalSolution.phasePowers, 0.0);
                additionalSolution.changedUsersCount = 0;
                
                // 构建选择单元：支线组作为整体和独立用户
                List<Object> selectionUnits = new ArrayList<>();
                Set<Integer> branchGroupUserSet = new HashSet<>();
                
                // 添加支线组作为整体单元
                for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
                {
                    selectionUnits.add(entry);
                    branchGroupUserSet.addAll(entry.getValue());
                }
                
                // 添加独立用户
                for (int i = 0; i < users.size(); i++) 
                {
                    if (!branchGroupUserSet.contains(i)) 
                    {
                        selectionUnits.add(i);
                    }
                }
                
                // 随机打乱选择单元
                Collections.shuffle(selectionUnits);
                
                // 进行选择和调整
                int remainingChanges = changeCount;
                int currentIndex = 0;
                
                while (remainingChanges > 0 && currentIndex < selectionUnits.size()) 
                {
                    Object unit = selectionUnits.get(currentIndex++);
                    
                    if (unit instanceof Map.Entry) 
                    {
                        // 处理支线组
                        @SuppressWarnings("unchecked")
                        Map.Entry<String, List<Integer>> entry = (Map.Entry<String, List<Integer>>) unit;
                        List<Integer> groupIndices = entry.getValue();
                        
                        // 如果剩余配额不足以调整整个支线组，跳过
                        if (groupIndices.size() > remainingChanges) 
                        {
                            continue;
                        }
                        
                        // 为整个支线组选择新相位
                        byte newPhase = (byte)(1 + new Random().nextInt(3));
                        
                        // 应用相位调整
                        for (int index : groupIndices) 
                        {
                            User user = users.get(index);
                            if (user.getCurrentPhase() != newPhase) 
                            {
                                additionalSolution.phases[index] = newPhase;
                                additionalSolution.moves[index] = 1;
                                additionalSolution.changedUsersCount++;
                                remainingChanges--;
                            }
                        }
                    }
                    else 
                    {
                        // 处理独立用户
                        int idx = (Integer) unit;
                        User user = users.get(idx);
                        
                        if (user.isPowerPhase()) 
                        {
                            byte moves = (byte)(1 + new Random().nextInt(2));
                            additionalSolution.phases[idx] = user.getCurrentPhase();  // 保持原相位不变
                            additionalSolution.moves[idx] = moves;
                            additionalSolution.changedUsersCount++;
                        }
                        else 
                        {
                            byte currentPhase = user.getCurrentPhase();
                            byte newPhase;
                            do 
                            {
                                newPhase = (byte)(1 + new Random().nextInt(3));
                            } while (newPhase == currentPhase);
                            additionalSolution.phases[idx] = newPhase;
                            additionalSolution.moves[idx] = 1;
                            additionalSolution.changedUsersCount++;
                        }
                        remainingChanges--;
                    }
                }
                
                // 计算相位电量
                for (int j = 0; j < users.size(); j++) 
                {
                    User user = users.get(j);
                    byte phase = additionalSolution.phases[j];
                    if (phase > 0) 
                    {
                        if (user.isPowerPhase()) 
                        {
                            byte moves = additionalSolution.moves[j];
                            if (moves == 1) 
                            {
                                // A->B, B->C, C->A
                                additionalSolution.phasePowers[0] += user.getPhaseCPower();
                                additionalSolution.phasePowers[1] += user.getPhaseAPower();
                                additionalSolution.phasePowers[2] += user.getPhaseBPower();
                            }
                            else if (moves == 2) 
                            {
                                // A->C, B->A, C->B
                                additionalSolution.phasePowers[0] += user.getPhaseBPower();
                                additionalSolution.phasePowers[1] += user.getPhaseCPower();
                                additionalSolution.phasePowers[2] += user.getPhaseAPower();
                            }
                            else 
                            {
                                // 不移动时保持原电量
                                additionalSolution.phasePowers[0] += user.getPhaseAPower();
                                additionalSolution.phasePowers[1] += user.getPhaseBPower();
                                additionalSolution.phasePowers[2] += user.getPhaseCPower();
                            }
                        }
                        else 
                        {
                            additionalSolution.phasePowers[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
                        }
                    }
                }
                
                // 计算不平衡度
                additionalSolution.unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
                    additionalSolution.phasePowers[0], 
                    additionalSolution.phasePowers[1], 
                    additionalSolution.phasePowers[2]
                );
                
                // 计算调整比例
                additionalSolution.changeRatio = (double) additionalSolution.changedUsersCount / users.size() * 100;
                
                // 计算调相代价
                additionalSolution.adjustmentCost = calculateAdjustmentCost(additionalSolution);
                
                // 标记为已计算
                additionalSolution.isCalculated = true;
                
                // 根据不平衡度限制接受解
                if (additionalSolution.unbalanceRate <= acceptanceThreshold) 
                {
                    currentPopulation.add(additionalSolution);
                    
                    // 如果是有效解，加入种群池
                    if (additionalSolution.unbalanceRate < MAX_ACCEPTABLE_UNBALANCE) 
                    {
                        solutionPool.add(new Solution(additionalSolution));
                        
                        // 检查种群池是否已经达到30%
                        if (solutionPool.size() >= requiredValidCount) 
                        {
                            // 已达到30%的要求，保存当前种群并跳出循环
                            lastPopulation = currentPopulation;
                            break;
                        }
                    }
                }
            }
            
            // 保存最后一次的种群
            lastPopulation = currentPopulation;
            
            // 如果种群池已达到30%，提前结束循环
            if (solutionPool.size() >= requiredValidCount) 
            {
                break;
            }
        }
        
        // 如果最终种群池中的解不足，说明5次尝试都未能满足30%的要求
        if (solutionPool.size() < requiredValidCount) 
        {
            // 如果种群池中至少有1个有效解，则仍然返回解
            if (solutionPool.size() > 0) 
            {
                // 填充剩余种群
                List<Solution> resultPopulation = new ArrayList<>(solutionPool);
                
                // 将最后一次种群中的非有效解添加到结果种群中
                for (Solution solution : lastPopulation) 
                {
                    if (solution.getUnbalanceRate() >= MAX_ACCEPTABLE_UNBALANCE) 
                    {
                        resultPopulation.add(solution);
                        if (resultPopulation.size() >= populationSize) 
                        {
                            break;
                        }
                    }
                }
                
                return resultPopulation;
            }
            
            // 一个有效解都没有，初始化失败
            return null;
        }
        
        // 构建最终返回的种群
        List<Solution> resultPopulation = new ArrayList<>(solutionPool);
        
        // 如果有效解数量已经够了，但种群大小不够，需要从最后一次的种群中添加解
        if (resultPopulation.size() < populationSize) 
        {
            // 从最后一次种群中添加非有效解，直到达到种群大小
            for (Solution solution : lastPopulation) 
            {
                if (solution.getUnbalanceRate() >= MAX_ACCEPTABLE_UNBALANCE) 
                {
                    // 不是有效解，检查是否已经在结果种群中
                    boolean exists = false;
                    for (Solution poolSolution : resultPopulation) 
                    {
                        if (Arrays.equals(poolSolution.phases, solution.phases) && 
                            Arrays.equals(poolSolution.moves, solution.moves)) 
                        {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) 
                    {
                        resultPopulation.add(solution);
                        if (resultPopulation.size() >= populationSize) 
                        {
                            break;
                        }
                    }
                }
            }
        }
        
        // 如果种群大小仍然不够，通过复制有效解来填充
        while (resultPopulation.size() < populationSize) 
        {
            // 随机选择一个有效解进行复制
            Solution baseSolution = solutionPool.get(new Random().nextInt(solutionPool.size()));
            resultPopulation.add(new Solution(baseSolution));
        }
        
        return resultPopulation;
    }

    // 轻微变异
    private void performLightMutation(Solution solution) 
    {
        // 随机选择1-2个用户进行调整
        int mutationCount = 1 + new Random().nextInt(2);
        Set<Integer> mutatedIndices = new HashSet<>();
        
        for(int i = 0; i < mutationCount; i++) 
        {
            // 随机选择一个未变异过的用户
            int userIndex;
            do 
            {
                userIndex = new Random().nextInt(users.size());
            } while(mutatedIndices.contains(userIndex));
            
            mutatedIndices.add(userIndex);
            User user = users.get(userIndex);
            
            if(user.isPowerPhase()) 
            {
                // 动力用户：改变移动次数
                byte currentMoves = solution.moves[userIndex];
                byte newMoves;
                do 
                {
                    newMoves = (byte)(1 + new Random().nextInt(2));
                } while(newMoves == currentMoves);
                solution.moves[userIndex] = newMoves;
            } 
            else 
            {
                // 普通用户：改变相位
                byte currentPhase = solution.phases[userIndex];
                byte newPhase;
                do 
                {
                    newPhase = (byte)(1 + new Random().nextInt(3));
                } while(newPhase == currentPhase);
                solution.phases[userIndex] = newPhase;
                solution.moves[userIndex] = 1;
            }
        }
        
        // 重新计算相位电量
        Arrays.fill(solution.phasePowers, 0.0);
        for(int i = 0; i < users.size(); i++) 
        {
            User user = users.get(i);
            byte phase = solution.phases[i];
            if(phase > 0) 
            {
                if(user.isPowerPhase()) 
                {
                    byte moves = solution.moves[i];
                    if(moves == 1) 
                    {
                        solution.phasePowers[0] += user.getPhaseCPower();
                        solution.phasePowers[1] += user.getPhaseAPower();
                        solution.phasePowers[2] += user.getPhaseBPower();
                    } 
                    else if(moves == 2) 
                    {
                        solution.phasePowers[0] += user.getPhaseBPower();
                        solution.phasePowers[1] += user.getPhaseCPower();
                        solution.phasePowers[2] += user.getPhaseAPower();
                    } 
                    else 
                    {
                        solution.phasePowers[0] += user.getPhaseAPower();
                        solution.phasePowers[1] += user.getPhaseBPower();
                        solution.phasePowers[2] += user.getPhaseCPower();
                    }
                } 
                else 
                {
                    solution.phasePowers[phase - 1] += user.getPowerByPhase(user.getCurrentPhase());
                }
            }
        }
        
        // 计算不平衡度
        solution.unbalanceRate = UnbalanceCalculator.calculateUnbalanceRate(
            solution.phasePowers[0], solution.phasePowers[1], solution.phasePowers[2]
        );
        
        // 计算调整比例
        solution.changedUsersCount = 0;
        for(int i = 0; i < users.size(); i++) 
        {
            if(solution.moves[i] > 0) 
            {
                solution.changedUsersCount++;
            }
        }
        solution.changeRatio = (double) solution.changedUsersCount / users.size() * 100;
        
        // 计算调相代价
        solution.adjustmentCost = calculateAdjustmentCost(solution);
        
        // 标记为已计算
        solution.isCalculated = true;
    }

    //计算调相代价
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

    //---------------------------------计算适应度---------------------------------
    private void calculateFitness(List<Solution> population) 
    {
        // 计算初始不平衡度
        double[] initialPhasePowers = new double[3];
        for (User user : users) 
        {
            if (user.isPowerPhase()) 
            {
                initialPhasePowers[0] += user.getPhaseAPower();
                initialPhasePowers[1] += user.getPhaseBPower();
                initialPhasePowers[2] += user.getPhaseCPower();
            }
            else 
            {
                initialPhasePowers[user.getCurrentPhase() - 1] += user.getPowerByPhase(user.getCurrentPhase());
            }
        }
        
        double initialUnbalance = UnbalanceCalculator.calculateUnbalanceRate(
            initialPhasePowers[0], initialPhasePowers[1], initialPhasePowers[2]
        );
        
        // 计算用户总数
        int totalUsers = users.size();

        // 计算适应度
        for (Solution solution : population) 
        {
            double improvementRate = (initialUnbalance - solution.unbalanceRate) / initialUnbalance * 100;
            double normalizedChangeRatio = solution.changeRatio;  // 调整用户比例
            double normalizedAdjustmentCost = (solution.adjustmentCost / totalPower) * 100;  // 调相代价
            
            // 首先根据不平衡度设置基础分值，使用更大的基础值差异确保分层优先级
            double baseFitness;
            if (solution.unbalanceRate > MAX_ACCEPTABLE_UNBALANCE) // > 15%
            {
                baseFitness = 10000;  // 基础分值显著提高，确保不可接受的解被明确拒绝
            }
            else if (solution.unbalanceRate > 10.0) // 10-15%
            {
                baseFitness = 5000;   // 可接受但不理想
            }
            else if (solution.unbalanceRate >= 5.0 && solution.unbalanceRate <= 10.0) // 5-10%，目标区间
            {
                baseFitness = 100;   // 最优基础分值，明显低于其他区间
            }
            else // 0-5%
            {
                baseFitness = 2000;   // 过度优化，不如5-10%区间
            }
            
            // 在基础分值的基础上，根据区间使用不同的权重计算适应度
            if (solution.unbalanceRate > MAX_ACCEPTABLE_UNBALANCE) 
            {
                // >15%的解：不可接受，使用改善率鼓励向可接受方向发展
                solution.fitness = baseFitness + (300 - 3 * improvementRate);
            }
            else if (solution.unbalanceRate > 10.0) 
            {
                // 10-15%的解：调整权重以反映优先级
                // 不平衡度接近10%的解得分更低(更好)
                solution.fitness = baseFitness + (
                    0.5 * (solution.unbalanceRate - 10.0) * 30 +  // 更重视接近10%
                    0.4 * normalizedChangeRatio +  // 调整用户数为次要考虑因素
                    0.1 * normalizedAdjustmentCost  // 调相代价为最后考虑因素
                );
            }
            else if (solution.unbalanceRate >= 5.0 && solution.unbalanceRate <= 10.0) 
            {
                // 5-10%的解：理想区间，主要考虑调整用户数，其次是调相代价
                // 不平衡度接近中间值(7.5%)的解更优
                double unbalanceDeviation = Math.abs(solution.unbalanceRate - 7.5);
                solution.fitness = baseFitness + (
                    0.15 * unbalanceDeviation * 10 +        // 15%权重给不平衡度与理想值的偏差
                    0.7 * normalizedChangeRatio +          // 70%权重给调整用户比例
                    0.15 * normalizedAdjustmentCost        // 15%权重给调相代价
                );
            }
            else 
            {
                // 0-5%的解：过度优化，主要根据调整用户数与调相代价计算
                solution.fitness = baseFitness + (
                    0.8 * normalizedChangeRatio +          // 80%权重给调整用户比例
                    0.2 * normalizedAdjustmentCost         // 20%权重给调相代价
                );
            }
            
            // 记录日志用于调试
            if (solution.fitness < 200) {
                Log.d("PhaseBalancer", String.format(
                    "高质量解 - 不平衡度: %.2f%%, 调整用户比例: %.2f%%, 调相代价: %.2f, 适应度: %.2f",
                    solution.unbalanceRate, normalizedChangeRatio, normalizedAdjustmentCost, solution.fitness
                ));
            }
        }
    }

    // 获取解的唯一标识，用以记录解的重复性
    private String getSolutionKey(Solution solution)
    {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < solution.phases.length; i++)
        {
            User user = users.get(i);
            if (user.isPowerPhase())
            {
                if (solution.moves[i] > 0)
                {
                    key.append(i).append(':').append("m").append(solution.moves[i]).append(';');
                }
            }
            else if (solution.phases[i] != user.getCurrentPhase())
            {
                key.append(i).append(':').append(solution.phases[i]).append(';');
            }
        }
        return key.toString();
    }
    

    //---------------------------------选择出适应度最高的30%，并使用锦标赛选择填充剩余位置---------------------------------
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
    
    //---------------------------------交叉---------------------------------
    private List<Solution> crossover(List<Solution> selected) 
    {
        List<Solution> offspring = new ArrayList<>();
        
        for (int i = 0; i < selected.size() - 1; i += 2) 
        {
            Solution parent1 = selected.get(i);
            Solution parent2 = selected.get(i + 1);
            
            if (Math.random() < CROSSOVER_RATE) 
            {
                Solution child1 = new Solution(parent1);
                Solution child2 = new Solution(parent2);
                
                // 随机选择交叉点
                int crossPoint = new Random().nextInt(users.size());
                
                // 创建已处理的支线组集合
                Set<String> processedGroups = new HashSet<>();
                
                // 首先处理所有支线组
                for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
                {
                    String groupKey = entry.getKey();
                    List<Integer> groupIndices = entry.getValue();
                    
                    // 计算交叉点后的用户比例
                    int usersAfterCrossPoint = 0;
                    for (int idx : groupIndices) 
                    {
                        if (idx >= crossPoint) 
                        {
                            usersAfterCrossPoint++;
                        }
                    }
                    double ratioAfterCrossPoint = (double) usersAfterCrossPoint / groupIndices.size();
                    
                    if (ratioAfterCrossPoint >= GROUP_EXCHANGE_THRESHOLD) 
                    {
                        // 如果超过阈值，交换整个组
                        for (int groupIndex : groupIndices) 
                        {
                            byte tempPhase = child1.phases[groupIndex];
                            byte tempMoves = child1.moves[groupIndex];
                            
                            child1.phases[groupIndex] = child2.phases[groupIndex];
                            child1.moves[groupIndex] = child2.moves[groupIndex];
                            
                            child2.phases[groupIndex] = tempPhase;
                            child2.moves[groupIndex] = tempMoves;
                        }
                    }
                    processedGroups.add(groupKey);
                }
                
                // 然后处理交叉点后的非支线组用户
                for (int j = crossPoint; j < users.size(); j++) 
                {
                    // 检查是否为非支线组用户
                    boolean isGroupUser = false;
                    for (List<Integer> groupIndices : branchGroupUserIndices.values()) 
                    {
                        if (groupIndices.contains(j)) 
                        {
                            isGroupUser = true;
                            break;
                        }
                    }
                    
                    // 只交换非支线组用户
                    if (!isGroupUser) 
                    {
                        byte tempPhase = child1.phases[j];
                        byte tempMoves = child1.moves[j];
                        
                        child1.phases[j] = child2.phases[j];
                        child1.moves[j] = child2.moves[j];
                        
                        child2.phases[j] = tempPhase;
                        child2.moves[j] = tempMoves;
                    }
                }
                
                // 修复解
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
        
        if (selected.size() % 2 != 0) 
        {
            offspring.add(new Solution(selected.get(selected.size() - 1)));
        }
        
        return offspring;
    }

    //---------------------------------变异---------------------------------
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

    //计算当前已经调整的用户数
    private int countChangedUsers(Solution solution)
    {
        int count = 0;
        for (int i = 0; i < users.size(); i++)
        {
            if (solution.moves[i] > 0)
            {
                count++;
            }
        }
        return count;
    }

    //计算已经修改的用户的比率
    private double getMaxChangeRatio()
    {
        // 计算当前三相功率
        double[] currentPhasePowers = new double[3];
        for (User user : users)
        {
            if (user.isPowerPhase())
            {
                currentPhasePowers[0] += user.getPhaseAPower();
                currentPhasePowers[1] += user.getPhaseBPower();
                currentPhasePowers[2] += user.getPhaseCPower();
            }
            else
            {
                currentPhasePowers[user.getCurrentPhase() - 1] += user.getPowerByPhase(user.getCurrentPhase());
            }
        }
        
        // 使用UnbalanceCalculator计算不平衡度
        double currentUnbalance = UnbalanceCalculator.calculateUnbalanceRate(
            currentPhasePowers[0], 
            currentPhasePowers[1], 
            currentPhasePowers[2]
        );
        
        if (currentUnbalance <= 15.0) 
        {
            // 不平衡度较小时，使用较小的调整范围
            return 0.2;  // 20%
        } 
        else if (currentUnbalance <= 25.0) 
        {
            // 不平衡度中等时，使用中等的调整范围
            return 0.3;  // 30%
        } 
        else 
        {
            // 不平衡度较大时，使用较大的调整范围
            return 0.4;  // 40%
        }
    }
    
    //交换变异
    private void performSwapMutation(Solution solution) 
    {
        // 分别获取已调整的普通用户和动力用户
        List<Integer> changedNormalUsers = new ArrayList<>();
        List<Integer> changedPowerUsers = new ArrayList<>();
        
        for (int i = 0; i < users.size(); i++) 
        {
            // 跳过支线组中的用户
            boolean isGroupUser = false;
            for (List<Integer> groupIndices : branchGroupUserIndices.values()) 
            {
                if (groupIndices.contains(i)) 
                {
                    isGroupUser = true;
                    break;
                }
            }
            if (isGroupUser) continue;
            
            // 如果是已调整的用户，根据类型分别加入对应列表
            if (solution.phases[i] != users.get(i).getCurrentPhase()) 
            {
                if (users.get(i).isPowerPhase()) 
                {
                    changedPowerUsers.add(i);
                } 
                else 
                {
                    changedNormalUsers.add(i);
                }
            }
        }
        
        // 随机决定是变异普通用户还是动力用户
        boolean mutatePowerUsers = new Random().nextBoolean();
        
        if (mutatePowerUsers && changedPowerUsers.size() >= 2) 
        {
            // 变异动力用户
            int idx1 = new Random().nextInt(changedPowerUsers.size());
            int idx2;
            do 
            {
                idx2 = new Random().nextInt(changedPowerUsers.size());
            } while (idx2 == idx1);
            
            int user1 = changedPowerUsers.get(idx1);
            int user2 = changedPowerUsers.get(idx2);
            
            // 交换相位和移动次数
            byte tempPhase = solution.phases[user1];
            byte tempMoves = solution.moves[user1];
            
            solution.phases[user1] = solution.phases[user2];
            solution.moves[user1] = solution.moves[user2];
            
            solution.phases[user2] = tempPhase;
            solution.moves[user2] = tempMoves;
        }
        else if (!mutatePowerUsers && changedNormalUsers.size() >= 2) 
        {
            // 变异普通用户
            int idx1 = new Random().nextInt(changedNormalUsers.size());
            int idx2;
            do 
            {
                idx2 = new Random().nextInt(changedNormalUsers.size());
            } while (idx2 == idx1);
            
            int user1 = changedNormalUsers.get(idx1);
            int user2 = changedNormalUsers.get(idx2);
            
            // 交换相位和移动次数
            byte tempPhase = solution.phases[user1];
            byte tempMoves = solution.moves[user1];
            
            solution.phases[user1] = solution.phases[user2];
            solution.moves[user1] = solution.moves[user2];
            
            solution.phases[user2] = tempPhase;
            solution.moves[user2] = tempMoves;
        }
    }
    
    //普通变异
    private void performNormalMutation(Solution solution, int currentChangedCount, int maxAllowedChanges) 
    {
        // 计算还可以调整的用户数
        int remainingChanges = maxAllowedChanges - currentChangedCount;
        if (remainingChanges <= 0) return;
        
        // 构建变异单元：支线组作为整体和独立用户
        List<Object> mutationUnits = new ArrayList<>();
        Set<Integer> branchGroupUserSet = new HashSet<>();
        
        // 添加支线组作为整体单元
        for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
        {
            mutationUnits.add(entry);
            branchGroupUserSet.addAll(entry.getValue());
        }
        
        // 添加独立用户
        for (int i = 0; i < users.size(); i++) 
        {
            if (!branchGroupUserSet.contains(i)) 
            {
                mutationUnits.add(i);
            }
        }
        
        // 随机选择一个变异单元
        int selectedIndex = new Random().nextInt(mutationUnits.size());
        Object selectedUnit = mutationUnits.get(selectedIndex);
        
        if (selectedUnit instanceof Map.Entry) 
        {
            // 处理支线组
            @SuppressWarnings("unchecked")
            Map.Entry<String, List<Integer>> entry = (Map.Entry<String, List<Integer>>) selectedUnit;
            List<Integer> groupIndices = entry.getValue();
            
            // 检查该支线组是否已经被调整
            boolean groupChanged = false;
            for (int index : groupIndices) 
            {
                if (solution.phases[index] != users.get(index).getCurrentPhase()) 
                {
                    groupChanged = true;
                    break;
                }
            }
            
            // 如果支线组未被调整，则进行变异
            if (!groupChanged && groupIndices.size() <= remainingChanges) 
            {
                // 随机选择新相位
                byte newPhase = (byte)(1 + new Random().nextInt(3));
                
                // 应用变异到整个支线组
                for (int index : groupIndices) 
                {
                    User user = users.get(index);
                    if (user.getCurrentPhase() != newPhase) 
                    {
                        solution.phases[index] = newPhase;
                        solution.moves[index] = 1;
                    }
                }
            }
        }
        else 
        {
            // 处理独立用户
            int userIndex = (Integer) selectedUnit;
            User user = users.get(userIndex);
            
            if (user.isPowerPhase()) 
            {
                // 动力用户只能改变移动次数
                byte originalMoves = solution.moves[userIndex];
                byte newMoves = (byte)(originalMoves == 1 ? 2 : 1);
                solution.moves[userIndex] = newMoves;
            }
            else 
            {
                // 普通用户尝试其他可能的相位
                for (byte newPhase = 1; newPhase <= 3; newPhase++) 
                {
                    if (newPhase != solution.phases[userIndex] && newPhase != users.get(userIndex).getCurrentPhase()) 
                    {
                        solution.phases[userIndex] = newPhase;
                        solution.moves[userIndex] = 1;
                        break;
                    }
                }
            }
        }
    }
    
    //---------------------------------修复解---------------------------------
    private void repairSolution(Solution solution) 
    {
        // 检查是否超过最大调整用户数限制
        int changedCount = countChangedUsers(solution);
        int maxAllowedChanges = (int)(users.size() * getMaxChangeRatio());
        
        if (changedCount > maxAllowedChanges) 
        {
            // 计算当前解的适应度
            calculateFitness(Arrays.asList(solution));
            double baseFitness = solution.getFitness();
            
            // 计算每个改变用户的贡献度（基于适应度）
            List<UserContribution> contributions = new ArrayList<>();
            
            for (int i = 0; i < users.size(); i++) 
            {
                if (solution.phases[i] != users.get(i).getCurrentPhase()) 
                {
                    // 临时保存原始值
                    byte originalPhase = solution.phases[i];
                    byte originalMoves = solution.moves[i];
                    
                    // 尝试恢复该用户
                    solution.phases[i] = users.get(i).getCurrentPhase();
                    solution.moves[i] = 0;
                    
                    // 计算恢复后的适应度
                    calculateFitness(Arrays.asList(solution));
                    double newFitness = solution.getFitness();
                    
                    // 计算贡献度（适应度的改变量）
                    // 贡献度 = 原适应度 - 恢复后适应度
                    // 贡献度为正说明该用户的调整是有益的
                    double contribution = baseFitness - newFitness;
                    contributions.add(new UserContribution(i, contribution));
                    
                    // 恢复原值，继续评估下一个用户
                    solution.phases[i] = originalPhase;
                    solution.moves[i] = originalMoves;
                }
            }
            
            // 按贡献排序(贡献越小的越应该被恢复)
            Collections.sort(contributions);
            
            // 恢复贡献最小的用户，直到满足最大调整比例
            for (int i = 0; i < contributions.size() - maxAllowedChanges; i++) 
            {
                int userIndex = contributions.get(i).userIndex;
                solution.phases[userIndex] = users.get(userIndex).getCurrentPhase();
                solution.moves[userIndex] = 0;
            }
            
            // 重新计算最终的适应度
            calculateFitness(Arrays.asList(solution));
        }
    }

    //---------------------------------局部搜索---------------------------------
    private void localSearch(Solution solution) 
    {
        // 计算当前解的适应度
        calculateFitness(Arrays.asList(solution));
        double currentFitness = solution.getFitness();
        
        boolean improved;
        do 
        {
            improved = false;
            
            // 先处理支线组
            for (Map.Entry<String, List<Integer>> entry : branchGroupUserIndices.entrySet()) 
            {
                List<Integer> groupIndices = entry.getValue();
                byte originalPhase = solution.phases[groupIndices.get(0)];
                
                // 尝试其他相位
                for (byte newPhase = 1; newPhase <= 3; newPhase++) 
                {
                    if (newPhase != originalPhase) 
                    {
                        // 临时保存原始值
                        byte[] originalPhases = new byte[groupIndices.size()];
                        byte[] originalMoves = new byte[groupIndices.size()];
                        
                        // 保存原始值并设置新值
                        for (int i = 0; i < groupIndices.size(); i++) 
                        {
                            int userIndex = groupIndices.get(i);
                            originalPhases[i] = solution.phases[userIndex];
                            originalMoves[i] = solution.moves[userIndex];
                            
                            // 设置新相位和moves
                            solution.phases[userIndex] = newPhase;
                            solution.moves[userIndex] = (byte)(newPhase != users.get(userIndex).getCurrentPhase() ? 1 : 0);
                        }
                        
                        // 重新计算适应度
                        calculateFitness(Arrays.asList(solution));
                        double newFitness = solution.getFitness();
                        
                        // 如果适应度变好（变小）则接受改变
                        if (newFitness < currentFitness) 
                        {
                            currentFitness = newFitness;
                            improved = true;
                        }
                        else 
                        {
                            // 恢复原值
                            for (int i = 0; i < groupIndices.size(); i++) 
                            {
                                int userIndex = groupIndices.get(i);
                                solution.phases[userIndex] = originalPhases[i];
                                solution.moves[userIndex] = originalMoves[i];
                            }
                        }
                    }
                }
            }
            
            // 处理独立用户
            for (int i = 0; i < users.size(); i++) 
            {
                // 跳过支线组用户
                boolean isGroupUser = false;
                for (List<Integer> groupIndices : branchGroupUserIndices.values()) 
                {
                    if (groupIndices.contains(i)) 
                    {
                        isGroupUser = true;
                        break;
                    }
                }
                if (isGroupUser) continue;
                
                if (users.get(i).isPowerPhase()) 
                {
                    // 动力用户只能改变移动次数
                    byte originalMoves = solution.moves[i];
                    byte newMoves = (byte)(originalMoves == 1 ? 2 : 1);
                    solution.moves[i] = newMoves;
                    
                    // 重新计算适应度
                    calculateFitness(Arrays.asList(solution));
                    double newFitness = solution.getFitness();
                    
                    if (newFitness < currentFitness) 
                    {
                        currentFitness = newFitness;
                        improved = true;
                    }
                    else 
                    {
                        solution.moves[i] = originalMoves;
                    }
                }
                else 
                {
                    // 普通用户尝试其他相位
                    byte originalPhase = solution.phases[i];
                    byte originalMoves = solution.moves[i];
                    
                    for (byte newPhase = 1; newPhase <= 3; newPhase++) 
                    {
                        if (newPhase != originalPhase && newPhase != users.get(i).getCurrentPhase()) 
                        {
                            solution.phases[i] = newPhase;
                            solution.moves[i] = 1;  // 设置moves为1表示发生改变
                            
                            // 重新计算适应度
                            calculateFitness(Arrays.asList(solution));
                            double newFitness = solution.getFitness();
                            
                            if (newFitness < currentFitness) 
                            {
                                currentFitness = newFitness;
                                improved = true;
                                break;
                            }
                            else 
                            {
                                solution.phases[i] = originalPhase;
                                solution.moves[i] = originalMoves;
                            }
                        }
                    }
                }
            }
        } while (improved);
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
        
        // 存储计算结果
        private double[] phasePowers;     // 三相功率
        private double unbalanceRate;     // 不平衡度
        private int changedUsersCount;    // 调整用户数
        private double changeRatio;       // 调整比例
        private double adjustmentCost;    // 调相代价
        private boolean isCalculated;     // 是否已计算
        
        public Solution(int size) 
        {
            this.phases = new byte[size];
            this.moves = new byte[size];
            this.phasePowers = new double[3];
            this.isCalculated = false;
        }
        
        public Solution(Solution other) 
        {
            this.phases = Arrays.copyOf(other.phases, other.phases.length);
            this.moves = Arrays.copyOf(other.moves, other.moves.length);
            this.fitness = other.fitness;
            this.phasePowers = Arrays.copyOf(other.phasePowers, other.phasePowers.length);
            this.unbalanceRate = other.unbalanceRate;
            this.changedUsersCount = other.changedUsersCount;
            this.changeRatio = other.changeRatio;
            this.adjustmentCost = other.adjustmentCost;
            this.isCalculated = other.isCalculated;
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
        
        // 获取不平衡度
        public double getUnbalanceRate() 
        {
            return unbalanceRate;
        }
        
        // 获取调整比例
        public double getChangeRatio() 
        {
            return changeRatio;
        }
        
        // 获取调相代价
        public double getAdjustmentCost() 
        {
            return adjustmentCost;
        }
        
        // 获取三相功率
        public double[] getPhasePowers() 
        {
            return Arrays.copyOf(phasePowers, phasePowers.length);
        }
        
        // 重置计算标志
        public void resetCalculation() 
        {
            this.isCalculated = false;
        }
        
        // 是否已计算
        public boolean isCalculated() 
        {
            return isCalculated;
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