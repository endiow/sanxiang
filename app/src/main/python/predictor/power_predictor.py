import pandas as pd
import numpy as np
from statsmodels.tsa.holtwinters import ExponentialSmoothing

class PowerPredictor:
    def __init__(self):
        """
        初始化预测器
        """
        self.model_a = None
        self.model_b = None
        self.model_c = None
    
    def predict(self, data):
        """
        预测下一天的三相电量
        
        Args:
            data: 包含历史数据的字典列表
                格式: [{'date': '2024-03-20', 'phase_a': 100.0, 'phase_b': 200.0, 'phase_c': 300.0}, ...]
                
        Returns:
            dict: 包含预测结果的字典
        """
        try:
            # 将输入数据转换为正确的格式
            dates = []
            phase_a = []
            phase_b = []
            phase_c = []
            
            # 使用range(data.size())遍历Java ArrayList
            size = data.size()
            for i in range(size):
                item = data.get(i)
                # 获取日期
                date_val = item.get("date")
                if date_val is None:
                    continue
                dates.append(str(date_val))
                
                # 获取电量数据，如果为空则使用0.0
                try:
                    phase_a_val = item.get("phase_a")
                    phase_a.append(float(0.0 if phase_a_val is None else phase_a_val))
                    
                    phase_b_val = item.get("phase_b")
                    phase_b.append(float(0.0 if phase_b_val is None else phase_b_val))
                    
                    phase_c_val = item.get("phase_c")
                    phase_c.append(float(0.0 if phase_c_val is None else phase_c_val))
                except (ValueError, TypeError):
                    continue
            
            # 检查是否有足够的数据
            if len(dates) < 3:  # 降低最小数据要求到3天
                return {
                    'success': False,
                    'error': f'数据量不足，至少需要3天的数据，当前只有{len(dates)}天'
                }
            
            # 创建DataFrame
            df = pd.DataFrame({
                'date': pd.to_datetime(dates),
                'phase_a': phase_a,
                'phase_b': phase_b,
                'phase_c': phase_c
            })
            
            # 确保数据不为空
            if df.empty:
                return {
                    'success': False,
                    'error': '没有有效的输入数据'
                }
            
            # 设置日期为索引并排序
            df = df.set_index('date')
            df = df.sort_index()
            
            # 提取时间序列数据
            series_a = df['phase_a']
            series_b = df['phase_b']
            series_c = df['phase_c']

            def predict_series(series):
                # 如果数据全为0，返回0
                if np.all(series == 0):
                    return 0.0, {'lower': 0.0, 'upper': 0.0}
                
                # 根据数据量选择不同的预测方法
                if len(series) >= 7:
                    # 使用Holt-Winters方法（有足够数据支持季节性）
                    model_params = {
                        'seasonal': 'add',
                        'seasonal_periods': 7,  # 周期性为7天
                        'trend': 'add',
                        'initialization_method': 'estimated',
                        'damped_trend': True  # 使用阻尼趋势
                    }
                    try:
                        model = ExponentialSmoothing(series, **model_params).fit(
                            optimized=True,
                            use_boxcox=True,  # 使用Box-Cox转换
                            remove_bias=True   # 移除偏差
                        )
                        pred = model.forecast(1)[0]
                    except:
                        # 如果Holt-Winters失败，使用简单指数平滑
                        model = ExponentialSmoothing(series, trend=None, seasonal=None).fit()
                        pred = model.forecast(1)[0]
                else:
                    # 数据量不足时使用加权移动平均
                    weights = np.exp(np.linspace(-1, 0, len(series)))
                    pred = np.average(series, weights=weights)

                # 计算预测区间
                std_err = np.std(series) if len(series) > 1 else 0
                margin = 1.96 * std_err  # 95% 置信区间
                
                # 确保预测值和区间都是非负的
                pred = max(0, pred)
                interval = {
                    'lower': float(max(0, pred - margin)),
                    'upper': float(pred + margin)
                }
                
                return float(pred), interval

            # 预测三相电量
            pred_a, interval_a = predict_series(series_a)
            pred_b, interval_b = predict_series(series_b)
            pred_c, interval_c = predict_series(series_c)
            
            # 返回预测结果
            return {
                'success': True,
                'predictions': {
                    'phase_a': {
                        'value': pred_a,
                        'interval': interval_a
                    },
                    'phase_b': {
                        'value': pred_b,
                        'interval': interval_b
                    },
                    'phase_c': {
                        'value': pred_c,
                        'interval': interval_c
                    }
                },
                'model_info': {
                    'data_points': len(series_a),
                    'last_date': df.index[-1].strftime('%Y-%m-%d'),
                    'confidence_level': 0.95,
                    'method': 'Holt-Winters' if len(series_a) >= 7 else 'Weighted Moving Average'
                }
            }
        except Exception as e:
            import traceback
            error_msg = str(e) + "\n" + traceback.format_exc()
            return {
                'success': False,
                'error': error_msg
            } 