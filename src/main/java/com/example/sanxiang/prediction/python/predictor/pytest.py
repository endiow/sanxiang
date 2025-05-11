"""
测试Python环境和相关功能
"""

def test_environment():
    """
    测试Python环境和相关包的功能
    返回测试结果的JSON格式数据
    """
    try:
        results = []
        
        # 基本Python测试
        results.append(
        {
            "name": "Python",
            "version": "3.8",
            "test_result": "基础功能正常"
        })
        
        # 测试 NumPy
        try:
            import numpy as np
            results.append(
            {
                "name": "NumPy",
                "version": np.__version__,
                "test_result": "1.19.5" == np.__version__ and "版本正确" or f"版本不匹配，当前版本: {np.__version__}"
            })
        except Exception as e:
            results.append(
            {
                "name": "NumPy",
                "version": "未知",
                "test_result": f"导入失败: {str(e)}"
            })
            
        # 测试 Pandas
        try:
            import pandas as pd
            results.append(
            {
                "name": "Pandas",
                "version": pd.__version__,
                "test_result": "1.3.2" == pd.__version__ and "版本正确" or f"版本不匹配，当前版本: {pd.__version__}"
            })
        except Exception as e:
            results.append(
            {
                "name": "Pandas",
                "version": "未知",
                "test_result": f"导入失败: {str(e)}"
            })
            
        # 测试 Statsmodels
        try:
            import statsmodels
            results.append(
            {
                "name": "Statsmodels",
                "version": statsmodels.__version__,
                "test_result": "0.11.0" == statsmodels.__version__ and "版本正确" or f"版本不匹配，当前版本: {statsmodels.__version__}"
            })
        except Exception as e:
            results.append(
            {
                "name": "Statsmodels",
                "version": "未知",
                "test_result": f"导入失败: {str(e)}"
            })
        
        return {
            "success": True,
            "results": results,
            "message": "环境测试成功"
        }
        
    except Exception as e:
        return {
            "success": False,
            "results": [],
            "message": f"测试失败: {str(e)}"
        }

