package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup;

import java.io.IOException;

/**
 * P4: 仅当 codestyle.tool-group=template 或 all 时注册，暴露 7 个模板/意图 MCP 工具。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnCodestyleToolGroup("template")
public class TemplateToolService {

    private final CodestyleService codestyleService;

    @McpTool(name = "codestyleSearch", description = "搜索代码模板库.")
    public String codestyleSearch(
            @McpToolParam(description = "模板提示词，如: CRUD, bankend, frontend等") String templateKeyword) {
        return codestyleService.codestyleSearch(templateKeyword);
    }

    @McpTool(name = "getTemplateByPath", description = "获取模板文件内容.")
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {
        return codestyleService.getTemplateByPath(templatePath);
    }

    @McpTool(name = "uploadTemplateFromFileSystem", description = "从文件系统上传模板并重建索引.")
    public String uploadTemplateFromFileSystem(
            @McpToolParam(description = "文件系统路径，如: E:/templates/CRUD") String sourcePath,
            @McpToolParam(description = "组ID，如: continew") String groupId,
            @McpToolParam(description = "项目ID，如: CRUD") String artifactId,
            @McpToolParam(description = "版本号，如: 1.0.0") String version,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        return codestyleService.uploadTemplateFromFileSystem(sourcePath, groupId, artifactId, version, overwrite);
    }

    @McpTool(name = "uploadTemplate", description = "上传模板并重建索引.")
    public String uploadTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        return codestyleService.uploadTemplate(templatePath, overwrite);
    }

    @McpTool(name = "deleteTemplate", description = "删除指定版本模板并重建索引.")
    public String deleteTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath) {
        return codestyleService.deleteTemplate(templatePath);
    }

    @McpTool(name = "analyzeStyleAndIntent", description = "文件监听器: start/stop/drain. 捕获保存事件供意图分析.")
    public String analyzeStyleAndIntent(
            @McpToolParam(description = "目录路径（start 时必填）") String directoryPath,
            @McpToolParam(description = "操作类型: start, stop, drain") String action) {
        return codestyleService.analyzeStyleAndIntent(directoryPath, action);
    }

    @McpTool(name = "materializeAndVerifyTemplate", description = "沙盒编译验证; 通过后可uploadTemplate持久化.")
    public String materializeAndVerifyTemplate(
            @McpToolParam(description = "沙盒工作区名称，如: verify-crud") String workspaceName,
            @McpToolParam(description = "沙盒内文件路径，如: src/Main.java") String filePath,
            @McpToolParam(description = "文件内容") String fileContent,
            @McpToolParam(description = "验证命令，如: mvn clean compile") String verifyCommand) {
        return codestyleService.materializeAndVerifyTemplate(workspaceName, filePath, fileContent, verifyCommand);
    }
}
