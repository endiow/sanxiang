import numpy as np
import pandas as pd
import statsmodels.api as sm
from statsmodels.tsa.holtwinters import ExponentialSmoothing

def test_environment():
    try:
        # 测试 numpy
        arr = np.array([1, 2, 3, 4, 5])
        mean_value = np.mean(arr)
        numpy_info = {
            "name": "NumPy",
            "version": np.__version__,
            "test_result": f"mean([1,2,3,4,5]) = {mean_value}"
        }
        
        # 测试 pandas
        dates = pd.date_range(start='2024-01-01', periods=5)
        values = np.array([1, 2, 3, 4, 5])
        df = pd.DataFrame({'value': values}, index=dates)
        pandas_info = {
            "name": "Pandas",
            "version": pd.__version__,
            "test_result": f"DataFrame.shape = {df.shape}"
        }
        
        # 测试 statsmodels
        model = ExponentialSmoothing(
            values,
            seasonal_periods=2,
            trend=None,
            seasonal=None
        ).fit()
        pred = model.forecast(1)[0]
        statsmodels_info = {
            "name": "Statsmodels",
            "version": sm.__version__,
            "test_result": f"forecast(1) = {pred}"
        }
        
        return {
            "success": True,
            "results": [numpy_info, pandas_info, statsmodels_info]
        }
        
    except Exception as e:
        return {
            "success": False,
            "error_type": type(e).__name__,
            "error_message": str(e)
        }

def main():
    return test_environment() 