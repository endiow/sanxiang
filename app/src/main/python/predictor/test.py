import numpy as np
import pandas as pd
from darts import TimeSeries
from power_predictor import PowerPredictor

def test_imports():
    """
    测试导入是否成功
    """
    try:
        # 测试 numpy
        arr = np.array([1, 2, 3])
        print("NumPy 测试成功:", arr)
        
        # 测试 pandas
        df = pd.DataFrame({'A': [1, 2, 3]})
        print("Pandas 测试成功:", df)
        
        # 测试 darts
        ts = TimeSeries.from_values(np.array([1, 2, 3]))
        print("Darts 测试成功:", ts)
        
        return "所有导入测试成功！"
    except Exception as e:
        return f"测试失败: {str(e)}"

def test_predictor():
    # 创建测试数据
    test_data = [
        {'date': '2024-01-01', 'phase_a': 100.0, 'phase_b': 200.0, 'phase_c': 300.0},
        {'date': '2024-01-02', 'phase_a': 110.0, 'phase_b': 210.0, 'phase_c': 310.0},
        {'date': '2024-01-03', 'phase_a': 120.0, 'phase_b': 220.0, 'phase_c': 320.0},
        {'date': '2024-01-04', 'phase_a': 115.0, 'phase_b': 215.0, 'phase_c': 315.0},
        {'date': '2024-01-05', 'phase_a': 105.0, 'phase_b': 205.0, 'phase_c': 305.0},
        {'date': '2024-01-06', 'phase_a': 95.0, 'phase_b': 195.0, 'phase_c': 295.0},
        {'date': '2024-01-07', 'phase_a': 90.0, 'phase_b': 190.0, 'phase_c': 290.0},
    ]
    
    # 创建预测器实例
    predictor = PowerPredictor()
    
    # 进行预测
    result = predictor.predict(test_data)
    
    # 打印结果
    if result['success']:
        print("预测成功！")
        print("预测结果：")
        print(f"A相：{result['predictions']['phase_a']:.2f}")
        print(f"B相：{result['predictions']['phase_b']:.2f}")
        print(f"C相：{result['predictions']['phase_c']:.2f}")
    else:
        print("预测失败：", result['error'])

if __name__ == '__main__':
    print(test_imports())
    test_predictor() 