package top.codestyle.mcp.model.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元变量
 *
 * @author 小航love666, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaVariable {
    private String variableName;
    private String variableType;
    private String variableComment;
    private String example;
}