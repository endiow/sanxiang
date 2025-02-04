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
            # 转换数据为 DataFrame
            df = pd.DataFrame(data)
            df['date'] = pd.to_datetime(df['date'])
            df = df.sort_values('date')
            
            # 获取时间序列数据
            series_a = df['phase_a'].values
            series_b = df['phase_b'].values
            series_c = df['phase_c'].values
            
            # 检查数据长度
            if len(series_a) < 7:
                return {
                    'success': False,
                    'error': '需要至少7天的历史数据'
                }
            
            if len(series_a) > 25:
                # 只使用最近25天的数据
                series_a = series_a[-25:]
                series_b = series_b[-25:]
                series_c = series_c[-25:]
            
            # 创建并训练模型
            # 使用加法模型，考虑趋势和季节性
            model_params = {
                'seasonal_periods': 7,  # 周期性为7天
                'trend': 'add',        # 加法趋势
                'seasonal': 'add',     # 加法季节性
                'initialization_method': 'estimated'  # 自动估计初始值
            }
            
            # A相预测
            model_a = ExponentialSmoothing(series_a, **model_params).fit()
            pred_a = model_a.forecast(1)[0]
            
            # B相预测
            model_b = ExponentialSmoothing(series_b, **model_params).fit()
            pred_b = model_b.forecast(1)[0]
            
            # C相预测
            model_c = ExponentialSmoothing(series_c, **model_params).fit()
            pred_c = model_c.forecast(1)[0]
            
            # 计算预测区间
            confidence = 0.95  # 95% 置信区间
            
            def calculate_confidence_interval(model, pred):
                resid = model.resid  # 获取残差
                std_err = np.std(resid)  # 计算标准误差
                z_value = 1.96  # 95% 置信区间的 z 值
                margin = z_value * std_err
                return {
                    'lower': float(max(0, pred - margin)),  # 确保下限不小于0
                    'upper': float(pred + margin)
                }
            
            # 返回预测结果
            return {
                'success': True,
                'predictions': {
                    'phase_a': {
                        'value': float(pred_a),
                        'interval': calculate_confidence_interval(model_a, pred_a)
                    },
                    'phase_b': {
                        'value': float(pred_b),
                        'interval': calculate_confidence_interval(model_b, pred_b)
                    },
                    'phase_c': {
                        'value': float(pred_c),
                        'interval': calculate_confidence_interval(model_c, pred_c)
                    }
                },
                'model_info': {
                    'data_points': len(series_a),
                    'last_date': df['date'].iloc[-1].strftime('%Y-%m-%d'),
                    'confidence_level': confidence
                }
            }
            
        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            } 