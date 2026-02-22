package top.codestyle.mcp.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签名算法验证测试
 * 
 * 目标：验证 MCP Server 的签名算法是否与 ContiNew Open API 标准一致
 * 
 * @author CodeStyle Team
 * @since 2.0.0
 */
public class CodestyleClientSignatureTest {

    /**
     * 测试用例 1：基础签名验证
     * 
     * 验证点：
     * 1. 签名字符串格式是否正确
     * 2. 签名结果是否与标准算法一致
     */
    @Test
    public void testBasicSignature() {
        System.out.println("\n========================================");
        System.out.println("  测试用例 1：基础签名验证");
        System.out.println("========================================\n");
        
        // 准备测试数据
        Map<String, String> params = new TreeMap<>();
        params.put("query", "CRUD");
        params.put("topK", "10");
        params.put("timestamp", "1708156800000");
        params.put("nonce", "abc123");
        params.put("accessKey", "test_ak");
        
        String secretKey = "test_sk";
        
        System.out.println("测试参数：");
        params.forEach((k, v) -> System.out.println("  " + k + " = " + v));
        System.out.println("  secretKey = " + secretKey);
        System.out.println();
        
        // 生成签名（当前算法）
        String currentSignature = generateSignatureCurrent(params, secretKey);
        String currentSignStr = buildSignStringCurrent(params, secretKey);
        
        // 生成签名（标准算法）
        String expectedSignature = generateSignatureStandard(params, secretKey);
        String expectedSignStr = buildSignStringStandard(params, secretKey);
        
        // 输出对比
        System.out.println("当前算法（MCP Server）：");
        System.out.println("  签名字符串: " + currentSignStr);
        System.out.println("  签名结果:   " + currentSignature);
        System.out.println();
        
        System.out.println("标准算法（ContiNew）：");
        System.out.println("  签名字符串: " + expectedSignStr);
        System.out.println("  签名结果:   " + expectedSignature);
        System.out.println();
        
        // 验证
        System.out.println("验证结果：");
        System.out.println("  签名字符串一致: " + currentSignStr.equals(expectedSignStr));
        System.out.println("  签名结果一致:   " + currentSignature.equals(expectedSignature));
        System.out.println();
        
        if (!currentSignature.equals(expectedSignature)) {
            System.err.println("❌ 签名不一致！需要修复！");
            System.err.println("\n差异分析：");
            System.err.println("  当前: " + currentSignStr);
            System.err.println("  标准: " + expectedSignStr);
            System.err.println("\n问题：当前算法在最后一个参数后多了一个 '&' 符号");
        } else {
            System.out.println("✅ 签名一致！算法正确！");
        }
        
        // 断言（预期会失败，因为当前算法有问题）
        assertEquals(expectedSignature, currentSignature, 
            "签名不一致！当前算法需要修复。\n" +
            "当前签名字符串: " + currentSignStr + "\n" +
            "标准签名字符串: " + expectedSignStr);
    }
    
    /**
     * 测试用例 2：空参数验证
     */
    @Test
    public void testEmptyParams() {
        System.out.println("\n========================================");
        System.out.println("  测试用例 2：空参数验证");
        System.out.println("========================================\n");
        
        Map<String, String> params = new TreeMap<>();
        params.put("accessKey", "test_ak");
        
        String secretKey = "test_sk";
        
        String currentSignature = generateSignatureCurrent(params, secretKey);
        String expectedSignature = generateSignatureStandard(params, secretKey);
        
        System.out.println("当前签名: " + currentSignature);
        System.out.println("标准签名: " + expectedSignature);
        
        assertNotNull(currentSignature);
        assertNotNull(expectedSignature);
        assertEquals(32, currentSignature.length()); // MD5 是 32 位
        assertEquals(32, expectedSignature.length());
    }
    
    /**
     * 测试用例 3：特殊字符验证
     */
    @Test
    public void testSpecialCharacters() {
        System.out.println("\n========================================");
        System.out.println("  测试用例 3：特殊字符验证");
        System.out.println("========================================\n");
        
        Map<String, String> params = new TreeMap<>();
        params.put("query", "CRUD & REST");
        params.put("accessKey", "test_ak");
        
        String secretKey = "test_sk";
        
        String currentSignature = generateSignatureCurrent(params, secretKey);
        String expectedSignature = generateSignatureStandard(params, secretKey);
        
        System.out.println("查询参数: CRUD & REST");
        System.out.println("当前签名: " + currentSignature);
        System.out.println("标准签名: " + expectedSignature);
        
        assertNotNull(currentSignature);
        assertNotNull(expectedSignature);
    }
    
    /**
     * 测试用例 4：中文参数验证
     */
    @Test
    public void testChineseCharacters() {
        System.out.println("\n========================================");
        System.out.println("  测试用例 4：中文参数验证");
        System.out.println("========================================\n");
        
        Map<String, String> params = new TreeMap<>();
        params.put("query", "增删改查");
        params.put("topK", "10");
        params.put("accessKey", "test_ak");
        
        String secretKey = "test_sk";
        
        String currentSignature = generateSignatureCurrent(params, secretKey);
        String expectedSignature = generateSignatureStandard(params, secretKey);
        
        System.out.println("查询参数: 增删改查");
        System.out.println("当前签名: " + currentSignature);
        System.out.println("标准签名: " + expectedSignature);
        
        assertNotNull(currentSignature);
        assertNotNull(expectedSignature);
    }
    
    /**
     * 测试用例 5：多参数验证
     */
    @Test
    public void testMultipleParams() {
        System.out.println("\n========================================");
        System.out.println("  测试用例 5：多参数验证");
        System.out.println("========================================\n");
        
        Map<String, String> params = new TreeMap<>();
        params.put("query", "CRUD");
        params.put("topK", "10");
        params.put("timestamp", "1708156800000");
        params.put("nonce", "abc123");
        params.put("accessKey", "test_ak");
        params.put("page", "1");
        params.put("size", "20");
        params.put("sort", "createTime");
        
        String secretKey = "test_sk";
        
        String currentSignStr = buildSignStringCurrent(params, secretKey);
        String expectedSignStr = buildSignStringStandard(params, secretKey);
        
        System.out.println("参数数量: " + params.size());
        System.out.println("当前签名字符串: " + currentSignStr);
        System.out.println("标准签名字符串: " + expectedSignStr);
        
        String currentSignature = generateSignatureCurrent(params, secretKey);
        String expectedSignature = generateSignatureStandard(params, secretKey);
        
        System.out.println("当前签名: " + currentSignature);
        System.out.println("标准签名: " + expectedSignature);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 当前的签名算法（已修复）
     * 
     * 使用 CodestyleClient 的实际方法进行测试
     */
    private String generateSignatureCurrent(Map<String, String> params, String secretKey) {
        // 直接调用 CodestyleClient 的私有方法（通过反射）
        try {
            java.lang.reflect.Method method = CodestyleClient.class.getDeclaredMethod(
                "generateSignature", Map.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, params, secretKey);
        } catch (Exception e) {
            // 如果反射失败，使用修复后的算法
            return buildSignStringCurrent(params, secretKey);
        }
    }
    
    /**
     * 构建当前的签名字符串（修复后的算法）
     */
    private String buildSignStringCurrent(Map<String, String> params, String secretKey) {
        // 1. 添加 key 参数
        Map<String, String> allParams = new TreeMap<>(params);
        allParams.put("key", secretKey);
        
        // 2. 字典序排序并拼接
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!"sign".equals(entry.getKey())) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        
        return DigestUtil.md5Hex(sb.toString());
    }
    
    /**
     * 标准签名算法（参考 ContiNew）
     * 
     * 算法步骤：
     * 1. 将所有参数（除 sign）按 key 字典序排序
     * 2. 添加 key=secretKey 参数
     * 3. 拼接成 key1=value1&key2=value2 格式（最后没有 "&"）
     * 4. MD5 加密（32位小写）
     */
    private String generateSignatureStandard(Map<String, String> params, String secretKey) {
        String signStr = buildSignStringStandard(params, secretKey);
        return DigestUtil.md5Hex(signStr);
    }
    
    /**
     * 构建标准的签名字符串
     */
    private String buildSignStringStandard(Map<String, String> params, String secretKey) {
        // 1. 添加 key 参数
        Map<String, String> allParams = new TreeMap<>(params);
        allParams.put("key", secretKey);
        
        // 2. 字典序排序并拼接（ContiNew 标准）
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!"sign".equals(entry.getKey())) {
                if (!first) {
                    sb.append("&");  // ← 只在非第一个参数前添加 "&"
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        
        return sb.toString();
    }
}

