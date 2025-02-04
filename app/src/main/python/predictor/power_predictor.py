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
            
            # 创建时间序列数据
            series_a = df['phase_a'].values
            series_b = df['phase_b'].values
            series_c = df['phase_c'].values
            
            # 创建并训练模型
            model_a = ExponentialSmoothing(
                series_a,
                seasonal_periods=7,
                trend='add',
                seasonal='add'
            ).fit()
            
            model_b = ExponentialSmoothing(
                series_b,
                seasonal_periods=7,
                trend='add',
                seasonal='add'
            ).fit()
            
            model_c = ExponentialSmoothing(
                series_c,
                seasonal_periods=7,
                trend='add',
                seasonal='add'
            ).fit()
            
            # 预测下一天
            pred_a = model_a.forecast(1)[0]
            pred_b = model_b.forecast(1)[0]
            pred_c = model_c.forecast(1)[0]
            
            return {
                'success': True,
                'predictions': {
                    'phase_a': float(pred_a),
                    'phase_b': float(pred_b),
                    'phase_c': float(pred_c)
                }
            }
            
        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            } 