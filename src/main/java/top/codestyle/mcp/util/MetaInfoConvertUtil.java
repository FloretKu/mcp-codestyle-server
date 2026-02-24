package top.codestyle.mcp.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.model.template.TemplateContent;
import top.codestyle.mcp.model.template.TemplateMetaConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.model.template.TemplateVariable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 元信息转换工具类
 * 提供 TemplateMetaInfo、TemplateMetaConfig 之间的转换
 *
 * @author Kanttha, movclantian
 * @since 2025-10-17
 */
public class MetaInfoConvertUtil {

    /**
     * 转换 TemplateMetaInfo 为 TemplateContent
     *
     * @param source 源 TemplateMetaInfo 对象
     * @return 转换后的 TemplateContent 对象，source为null时返回null
     */
    public static TemplateContent convert(TemplateMetaInfo source) {
        if (source == null) {
            return null;
        }
        TemplateContent target = new TemplateContent();

        // 复制基础字段
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setGroupId(source.getGroupId());
        target.setArtifactId(source.getArtifactId());
        target.setFilePath(source.getFilePath());
        target.setDescription(source.getDescription());
        target.setFilename(source.getFilename());
        target.setSha256(source.getSha256());
        target.setPath(source.getPath());

        // 复制变量列表
        List<TemplateVariable> vars = source.getInputVariables();
        if (CollUtil.isNotEmpty(vars)) {
            target.setInputVariables(vars);
        }
        return target;
    }

    /**
     * 解析单版本格式的 meta.json 文件为模板元信息列表
     * 
     * @param metaFile meta.json 文件
     * @return 模板元信息列表
     * @throws IOException 文件读取异常
     */
    public static List<TemplateMetaInfo> parseMetaJson(File metaFile) throws IOException {
        List<TemplateMetaInfo> result = new ArrayList<>();

        // 解析单版本格式
        TemplateMetaConfig config = JSONUtil.toBean(FileUtil.readUtf8String(metaFile), TemplateMetaConfig.class);

        String groupId = config.getGroupId();
        String artifactId = config.getArtifactId();
        String version = config.getVersion();

        if (config.getFiles() != null) {
            for (TemplateMetaConfig.FileInfo fileInfo : config.getFiles()) {
                TemplateMetaInfo metaInfo = new TemplateMetaInfo();
                metaInfo.setGroupId(groupId);
                metaInfo.setArtifactId(artifactId);
                metaInfo.setVersion(version);
                metaInfo.setFilename(fileInfo.getFilename());
                metaInfo.setFilePath(fileInfo.getFilePath());
                metaInfo.setDescription(fileInfo.getDescription());
                metaInfo.setSha256(fileInfo.getSha256());
                metaInfo.setInputVariables(fileInfo.getInputVariables());

                String fullPath = groupId + File.separator + artifactId + File.separator + version +
                        fileInfo.getFilePath() + File.separator + fileInfo.getFilename();
                metaInfo.setPath(fullPath);

                result.add(metaInfo);
            }
        }

        return result;
    }
}
