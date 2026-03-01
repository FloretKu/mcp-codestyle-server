package top.codestyle.mcp.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.model.template.TemplateContent;
import top.codestyle.mcp.model.template.TemplateMetaConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 元信息转换工具类
 * <p>提供 {@link TemplateMetaInfo}、{@link TemplateMetaConfig} 之间的转换
 *
 * @author Kanttha, movclantian
 * @since 2025-10-17
 */
public class MetaInfoConvertUtil {

    /**
     * 将 {@link TemplateMetaInfo} 转换为 {@link TemplateContent}
     *
     * @param source 源对象
     * @return 转换后的对象，source为null时返回null
     */
    public static TemplateContent convert(TemplateMetaInfo source) {
        if (source == null) {
            return null;
        }
        TemplateContent target = new TemplateContent();
        BeanUtil.copyProperties(source, target);
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
