package com.example.sanxiang;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONObject;

public class TestResultActivity extends AppCompatActivity 
{
    private TextView tvTestResult;
    private Button btnCopy;
    private String testResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_result);

        tvTestResult = findViewById(R.id.tvTestResult);
        btnCopy = findViewById(R.id.btnCopy);

        runTest();
        setupCopyButton();
    }

    private void runTest() 
    {
        try 
        {
            Python py = Python.getInstance();
            PyObject testModule = py.getModule("predictor.pytest");
            PyObject result = testModule.callAttr("test_environment");
            
            // 解析测试结果
            JSONObject jsonResult = new JSONObject(result.toString());
            StringBuilder sb = new StringBuilder();
            
            if (jsonResult.getBoolean("success")) 
            {
                JSONArray results = jsonResult.getJSONArray("results");
                sb.append("=== 测试成功 ===\n\n");
                
                for (int i = 0; i < results.length(); i++) 
                {
                    JSONObject info = results.getJSONObject(i);
                    sb.append(String.format("【%s】\n", info.getString("name")));
                    sb.append(String.format("版本: %s\n", info.getString("version")));
                    sb.append(String.format("测试: %s\n\n", info.getString("test_result")));
                }
                
                sb.append("=== 测试完成 ===");
            } 
            else 
            {
                sb.append("=== 测试失败 ===\n\n");
                sb.append(String.format("错误类型: %s\n", jsonResult.getString("error_type")));
                sb.append(String.format("错误信息: %s\n\n", jsonResult.getString("error_message")));
                sb.append("=== 错误详情结束 ===");
            }
            
            testResult = sb.toString();
            tvTestResult.setText(testResult);
        } 
        catch (Exception e) 
        {
            String errorMsg = String.format(
                "=== 测试执行错误 ===\n\n" +
                "错误类型: %s\n" +
                "错误信息: %s\n\n" +
                "=== 错误详情结束 ===",
                e.getClass().getSimpleName(),
                e.getMessage()
            );
            testResult = errorMsg;
            tvTestResult.setText(errorMsg);
        }
    }

    private void setupCopyButton() 
    {
        btnCopy.setOnClickListener(v -> 
        {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("测试结果", testResult);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
    }
} 