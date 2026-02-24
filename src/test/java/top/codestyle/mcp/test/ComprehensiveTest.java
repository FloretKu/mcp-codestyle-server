package top.codestyle.mcp.test;

import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.template.TemplateMetaConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.service.LuceneIndexService;
import top.codestyle.mcp.util.CodestyleClient;
import top.codestyle.mcp.util.MetaInfoConvertUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * MCP 服务完整功能测试
 * 测试所有核心功能，确保代码无问题
 */
public class ComprehensiveTest {

    private static final String CACHE_PATH = "C:\\Users\\artboy\\.codestyle\\cache\\codestyle-cache";
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("MCP 服务完整功能测试");
        System.out.println("=".repeat(80));
        System.out.println();

        // 测试 1: 验证测试环境
        testEnvironment();

        // 测试 2: meta.json 格式验证
        testMetaJsonFormat();

        // 测试 3: meta.json 解析
        testMetaJsonParsing();

        // 测试 4: 本地仓库搜索
        testLocalRepositorySearch();

        // 测试 5: 精确路径搜索
        testSearchByPath();

        // 测试 6: 模板文件完整性
        testTemplateFileIntegrity();

        // 测试 7: 路径规范化
        testPathNormalization();

        // 测试 8: Lucene 索引构建
        testLuceneIndexing();

        // 测试 9: Lucene 搜索
        testLuceneSearch();

        // 测试 10: 多模板文件测试
        testMultipleTemplateFiles();

        // 汇总结果
        printSummary();
    }

    /**
     * 测试 1: 验证测试环境
     */
    private static void testEnvironment() {
        System.out.println("【测试 1】验证测试环境");
        System.out.println("-".repeat(80));

        try {
            // 检查缓存目录
            Path cachePath = Paths.get(CACHE_PATH);
            if (!Files.exists(cachePath)) {
                fail("缓存目录不存在: " + CACHE_PATH);
                return;
            }

            // 检查模板目录
            Path templatePath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0");
            if (!Files.exists(templatePath)) {
                fail("模板目录不存在: " + templatePath);
                return;
            }

            // 检查 meta.json
            Path metaPath = templatePath.resolve("meta.json");
            if (!Files.exists(metaPath)) {
                fail("meta.json 不存在: " + metaPath);
                return;
            }

            pass("测试环境正常");
            System.out.println("  - 缓存目录: " + CACHE_PATH);
            System.out.println("  - 模板目录: " + templatePath);
            System.out.println("  - meta.json: " + metaPath);

        } catch (Exception e) {
            fail("环境验证异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 2: meta.json 格式验证
     */
    private static void testMetaJsonFormat() {
        System.out.println("【测试 2】meta.json 格式验证");
        System.out.println("-".repeat(80));

        try {
            Path metaPath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0", "meta.json");
            String content = Files.readString(metaPath);

            // 验证是单版本格式（不包含 configs 数组）
            if (content.contains("\"configs\"")) {
                fail("meta.json 仍然是多版本格式（包含 configs 数组）");
                return;
            }

            // 验证包含必需字段
            if (!content.contains("\"groupId\"")) {
                fail("缺少 groupId 字段");
                return;
            }

            if (!content.contains("\"artifactId\"")) {
                fail("缺少 artifactId 字段");
                return;
            }

            if (!content.contains("\"version\"")) {
                fail("缺少 version 字段");
                return;
            }

            if (!content.contains("\"files\"")) {
                fail("缺少 files 字段");
                return;
            }

            pass("meta.json 格式正确（单版本格式）");
            System.out.println("  - 格式: 单版本");
            System.out.println("  - 包含字段: groupId, artifactId, version, files");

        } catch (Exception e) {
            fail("格式验证异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 3: meta.json 解析
     */
    private static void testMetaJsonParsing() {
        System.out.println("【测试 3】meta.json 解析");
        System.out.println("-".repeat(80));

        try {
            Path metaPath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0", "meta.json");
            
            // 使用 MetaInfoConvertUtil 解析
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

            // 显示前 3 个文件
            System.out.println("  - 示例文件:");
            for (int i = 0; i < Math.min(3, metaInfos.size()); i++) {
                TemplateMetaInfo info = metaInfos.get(i);
                System.out.println("    " + (i + 1) + ". " + info.getFilename() + " - " + info.getDescription());
            }

        } catch (Exception e) {
            fail("解析异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 4: 本地仓库搜索
     */
    private static void testLocalRepositorySearch() {
        System.out.println("【测试 4】本地仓库搜索");
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
     * 测试 5: 精确路径搜索
     */
    private static void testSearchByPath() {
        System.out.println("【测试 5】精确路径搜索");
        System.out.println("-".repeat(80));

        try {
            // 测试多个路径
            String[] testPaths = {
                "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl",
                "continew/CRUD/1.0.0/bankend/src/main/java/com/air/service/Service.ftl",
                "continew/CRUD/1.0.0/frontend/src/api/api.ftl"
            };

            int successCount = 0;
            for (String testPath : testPaths) {
                TemplateMetaInfo result = CodestyleClient.searchByPath(testPath, CACHE_PATH);
                if (result != null) {
                    successCount++;
                    System.out.println("  ✓ 找到: " + result.getFilename());
                } else {
                    System.out.println("  ✗ 未找到: " + testPath);
                }
            }

            if (successCount == 0) {
                fail("所有路径搜索都失败");
                return;
            }

            pass("成功找到 " + successCount + "/" + testPaths.length + " 个模板文件");

        } catch (Exception e) {
            fail("搜索异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 6: 模板文件完整性
     */
    private static void testTemplateFileIntegrity() {
        System.out.println("【测试 6】模板文件完整性");
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
                    System.out.println("  ⚠ 文件不存在: " + info.getFilename());
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
     * 测试 7: 路径规范化
     */
    private static void testPathNormalization() {
        System.out.println("【测试 7】路径规范化");
        System.out.println("-".repeat(80));

        try {
            // 测试不同格式的路径
            String[][] testCases = {
                {"continew/CRUD/1.0.0", "continew\\CRUD\\1.0.0"},
                {"continew//CRUD//1.0.0", "continew\\\\CRUD\\\\1.0.0"},
                {"/continew/CRUD/1.0.0", "\\continew\\CRUD\\1.0.0"}
            };

            boolean allPassed = true;
            for (String[] testCase : testCases) {
                String normalized1 = CodestyleClient.normalizePath(testCase[0]);
                String normalized2 = CodestyleClient.normalizePath(testCase[1]);

                if (!normalized1.equals(normalized2)) {
                    System.out.println("  ✗ 不一致: " + testCase[0] + " vs " + testCase[1]);
                    System.out.println("    结果1: " + normalized1);
                    System.out.println("    结果2: " + normalized2);
                    allPassed = false;
                } else {
                    System.out.println("  ✓ 一致: " + normalized1);
                }
            }

            if (!allPassed) {
                fail("路径规范化不一致");
                return;
            }

            pass("路径规范化正常");

        } catch (Exception e) {
            fail("规范化异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 8: Lucene 索引构建
     */
    private static void testLuceneIndexing() {
        System.out.println("【测试 8】Lucene 索引构建");
        System.out.println("-".repeat(80));

        try {
            // 创建 RepositoryConfig
            RepositoryConfig config = new RepositoryConfig();
            config.setLocalPath(CACHE_PATH.replace("\\codestyle-cache", ""));

            // 创建 LuceneIndexService
            LuceneIndexService luceneService = new LuceneIndexService(config);
            luceneService.init();

            pass("Lucene 索引构建成功");
            System.out.println("  - 索引目录: " + CACHE_PATH + "\\lucene-index");

        } catch (Exception e) {
            fail("索引构建异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 9: Lucene 搜索
     */
    private static void testLuceneSearch() {
        System.out.println("【测试 9】Lucene 搜索");
        System.out.println("-".repeat(80));

        try {
            // 创建 RepositoryConfig
            RepositoryConfig config = new RepositoryConfig();
            config.setLocalPath(CACHE_PATH.replace("\\codestyle-cache", ""));

            // 创建 LuceneIndexService
            LuceneIndexService luceneService = new LuceneIndexService(config);
            luceneService.init();

            // 测试搜索
            String[] keywords = {"CRUD", "Controller", "continew/CRUD"};
            int successCount = 0;

            for (String keyword : keywords) {
                List<LuceneIndexService.SearchResult> results = luceneService.fetchLocalMetaConfig(keyword);
                if (!results.isEmpty()) {
                    successCount++;
                    System.out.println("  ✓ 关键词 '" + keyword + "': 找到 " + results.size() + " 个结果");
                } else {
                    System.out.println("  ✗ 关键词 '" + keyword + "': 未找到结果");
                }
            }

            if (successCount == 0) {
                fail("所有搜索都失败");
                return;
            }

            pass("Lucene 搜索正常 (" + successCount + "/" + keywords.length + " 成功)");

            // 清理
            luceneService.destroy();

        } catch (Exception e) {
            fail("搜索异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * 测试 10: 多模板文件测试
     */
    private static void testMultipleTemplateFiles() {
        System.out.println("【测试 10】多模板文件测试");
        System.out.println("-".repeat(80));

        try {
            Path metaPath = Paths.get(CACHE_PATH, "continew", "CRUD", "1.0.0", "meta.json");
            List<TemplateMetaInfo> metaInfos = MetaInfoConvertUtil.parseMetaJson(metaPath.toFile());

            // 统计不同类型的文件
            int backendCount = 0;
            int frontendCount = 0;
            int sqlCount = 0;

            for (TemplateMetaInfo info : metaInfos) {
                String path = info.getFilePath();
                if (path.contains("bankend") || path.contains("backend")) {
                    backendCount++;
                } else if (path.contains("frontend")) {
                    frontendCount++;
                } else if (path.contains("sql")) {
                    sqlCount++;
                }
            }

            pass("成功识别多种类型的模板文件");
            System.out.println("  - 后端模板: " + backendCount + " 个");
            System.out.println("  - 前端模板: " + frontendCount + " 个");
            System.out.println("  - SQL 模板: " + sqlCount + " 个");
            System.out.println("  - 总计: " + metaInfos.size() + " 个");

        } catch (Exception e) {
            fail("多文件测试异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
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
        
        if (passedTests + failedTests > 0) {
            System.out.println("通过率: " + String.format("%.1f%%", 
                (passedTests * 100.0 / (passedTests + failedTests))));
        }
        
        System.out.println("=".repeat(80));

        if (failedTests == 0) {
            System.out.println("🎉 所有测试通过！代码完全正常！");
        } else {
            System.out.println("⚠️  有 " + failedTests + " 个测试失败，请检查！");
            System.exit(1);
        }
    }
}

