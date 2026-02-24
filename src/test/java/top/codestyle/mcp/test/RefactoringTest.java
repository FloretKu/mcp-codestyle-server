package top.codestyle.mcp.test;

import top.codestyle.mcp.model.template.TemplateMetaConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.util.CodestyleClient;
import top.codestyle.mcp.util.MetaInfoConvertUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * MCP 服务重构测试脚本
 * 测试所有核心功能
 */
public class RefactoringTest {

    private static final String CACHE_PATH = "C:\\Users\\artboy\\.codestyle\\cache\\codestyle-cache";
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("MCP 服务重构测试");
        System.out.println("=".repeat(80));
        System.out.println();

        // 测试 1: meta.json 解析
        testMetaJsonParsing();

        // 测试 2: 本地仓库搜索
        testLocalRepositorySearch();

        // 测试 3: 精确路径搜索
        testSearchByPath();

        // 测试 4: 模板文件存在性验证
        testTemplateFileExists();

        // 测试 5: 路径规范化
        testPathNormalization();

        // 测试 6: 版本排序
        testVersionSorting();

        // 汇总结果
        printSummary();
    }

    /**
     * 测试 1: meta.json 解析（单版本格式）
     */
    private static void testMetaJsonParsing() {
        System.out.println("【测试 1】meta.json 解析（单版本格式）");
        System.out.println("-".repeat(80));

        try {
            Path metaPath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0", "meta.json");
            
            if (!Files.exists(metaPath)) {
                fail("meta.json 文件不存在: " + metaPath);
                return;
            }

            // 解析 meta.json
            List<TemplateMetaInfo> metaInfos = MetaInfoConvertUtil.parseMetaJson(metaPath.toFile());

            if (metaInfos.isEmpty()) {
                fail("解析结果为空");
                return;
            }

            // 验证字段
            TemplateMetaInfo first = metaInfos.get(0);
            if (!"continew".equals(first.getGroupId())) {
                fail("groupId 不正确: " + first.getGroupId());
                return;
            }

            if (!"CRUD".equals(first.getArtifactId())) {
                fail("artifactId 不正确: " + first.getArtifactId());
                return;
            }

            if (!"1.0.0".equals(first.getVersion())) {
                fail("version 不正确: " + first.getVersion());
                return;
            }

            pass("成功解析 " + metaInfos.size() + " 个模板文件");
            System.out.println("  - groupId: " + first.getGroupId());
            System.out.println("  - artifactId: " + first.getArtifactId());
            System.out.println("  - version: " + first.getVersion());
            System.out.println("  - 文件数: " + metaInfos.size());

        } catch (Exception e) {
            fail("解析异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 2: 本地仓库搜索
     */
    private static void testLocalRepositorySearch() {
        System.out.println("【测试 2】本地仓库搜索");
        System.out.println("-".repeat(80));

        try {
            List<TemplateMetaInfo> results = CodestyleClient.searchLocalRepository(
                "continew", "CRUD", CACHE_PATH);

            if (results.isEmpty()) {
                fail("搜索结果为空");
                return;
            }

            TemplateMetaInfo first = results.get(0);
            if (!"1.0.0".equals(first.getVersion())) {
                fail("返回的版本不正确: " + first.getVersion());
                return;
            }

            pass("成功搜索到 " + results.size() + " 个模板文件");
            System.out.println("  - 版本: " + first.getVersion());
            System.out.println("  - 文件数: " + results.size());

        } catch (Exception e) {
            fail("搜索异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 3: 精确路径搜索
     */
    private static void testSearchByPath() {
        System.out.println("【测试 3】精确路径搜索");
        System.out.println("-".repeat(80));

        try {
            String testPath = "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl";
            
            TemplateMetaInfo result = CodestyleClient.searchByPath(testPath, CACHE_PATH);

            if (result == null) {
                fail("未找到模板: " + testPath);
                return;
            }

            if (!"Controller.ftl".equals(result.getFilename())) {
                fail("文件名不正确: " + result.getFilename());
                return;
            }

            pass("成功找到模板文件");
            System.out.println("  - 文件名: " + result.getFilename());
            System.out.println("  - 路径: " + result.getFilePath());
            System.out.println("  - 描述: " + result.getDescription());

        } catch (Exception e) {
            fail("搜索异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 4: 模板文件存在性验证
     */
    private static void testTemplateFileExists() {
        System.out.println("【测试 4】模板文件存在性验证");
        System.out.println("-".repeat(80));

        try {
            List<TemplateMetaInfo> results = CodestyleClient.searchLocalRepository(
                "continew", "CRUD", CACHE_PATH);

            if (results.isEmpty()) {
                fail("搜索结果为空");
                return;
            }

            int existsCount = 0;
            int notExistsCount = 0;

            for (TemplateMetaInfo info : results) {
                Path templatePath = Paths.get(CACHE_PATH,
                    info.getGroupId(),
                    info.getArtifactId(),
                    info.getVersion(),
                    info.getFilePath(),
                    info.getFilename());

                if (Files.exists(templatePath)) {
                    existsCount++;
                } else {
                    notExistsCount++;
                    System.out.println("  ⚠ 文件不存在: " + templatePath);
                }
            }

            if (notExistsCount > 0) {
                fail(notExistsCount + " 个文件不存在");
                return;
            }

            pass("所有 " + existsCount + " 个模板文件都存在");

        } catch (Exception e) {
            fail("验证异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 5: 路径规范化
     */
    private static void testPathNormalization() {
        System.out.println("【测试 5】路径规范化");
        System.out.println("-".repeat(80));

        try {
            // 测试不同格式的路径
            String path1 = "continew/CRUD/1.0.0";
            String path2 = "continew\\CRUD\\1.0.0";
            String path3 = "continew//CRUD//1.0.0";

            String normalized1 = CodestyleClient.normalizePath(path1);
            String normalized2 = CodestyleClient.normalizePath(path2);
            String normalized3 = CodestyleClient.normalizePath(path3);

            if (!normalized1.equals(normalized2) || !normalized2.equals(normalized3)) {
                fail("路径规范化不一致");
                System.out.println("  - path1: " + normalized1);
                System.out.println("  - path2: " + normalized2);
                System.out.println("  - path3: " + normalized3);
                return;
            }

            pass("路径规范化正常");
            System.out.println("  - 规范化结果: " + normalized1);

        } catch (Exception e) {
            fail("规范化异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 6: 版本排序（创建多版本测试）
     */
    private static void testVersionSorting() {
        System.out.println("【测试 6】版本排序");
        System.out.println("-".repeat(80));

        try {
            // 创建 2.0.0 版本用于测试
            Path v2Path = Paths.get(CACHE_PATH, "continew", "CRUD", "2.0.0");
            Path v1MetaPath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0", "meta.json");
            
            if (!Files.exists(v2Path)) {
                Files.createDirectories(v2Path);
                
                // 复制 meta.json 并修改版本号
                String metaContent = Files.readString(v1MetaPath);
                metaContent = metaContent.replace("\"version\": \"1.0.0\"", "\"version\": \"2.0.0\"");
                Files.writeString(v2Path.resolve("meta.json"), metaContent);
            }

            // 搜索应该返回最新版本（2.0.0）
            List<TemplateMetaInfo> results = CodestyleClient.searchLocalRepository(
                "continew", "CRUD", CACHE_PATH);

            if (results.isEmpty()) {
                fail("搜索结果为空");
                return;
            }

            String version = results.get(0).getVersion();
            if (!"2.0.0".equals(version)) {
                fail("未返回最新版本，当前版本: " + version);
                return;
            }

            pass("正确返回最新版本: " + version);

            // 清理测试数据
            deleteDirectory(v2Path.toFile());

        } catch (Exception e) {
            fail("版本排序测试异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 递归删除目录
     */
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }

    /**
     * 测试通过
     */
    private static void pass(String message) {
        System.out.println("✅ 通过: " + message);
        passedTests++;
    }

    /**
     * 测试失败
     */
    private static void fail(String message) {
        System.out.println("❌ 失败: " + message);
        failedTests++;
    }

    /**
     * 打印测试汇总
     */
    private static void printSummary() {
        System.out.println("=".repeat(80));
        System.out.println("测试汇总");
        System.out.println("=".repeat(80));
        System.out.println("总测试数: " + (passedTests + failedTests));
        System.out.println("通过: " + passedTests);
        System.out.println("失败: " + failedTests);
        System.out.println("通过率: " + String.format("%.1f%%", 
            (passedTests * 100.0 / (passedTests + failedTests))));
        System.out.println("=".repeat(80));

        if (failedTests == 0) {
            System.out.println("🎉 所有测试通过！");
        } else {
            System.out.println("⚠️  有 " + failedTests + " 个测试失败，请检查！");
        }
    }
}

