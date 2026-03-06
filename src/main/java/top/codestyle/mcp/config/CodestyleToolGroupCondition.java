package top.codestyle.mcp.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * P4: 根据 codestyle.tool-group 决定是否注册某组 MCP 工具。
 * code-analysis → 仅注册 CodeAnalysisService（2 工具）
 * template → 仅注册 TemplateToolService（7 工具）
 * all → 两者都注册
 */
public class CodestyleToolGroupCondition implements Condition {

    private static final String PROP = "codestyle.tool-group";
    private static final String DEFAULT_GROUP = "all";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String group = env.getProperty(PROP, DEFAULT_GROUP);
        if (group == null || group.isBlank()) group = DEFAULT_GROUP;

        Map<String, Object> attrs = metadata.getAnnotationAttributes(
                "top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup");
        if (attrs == null) return false;
        String value = (String) attrs.get("value");
        if (value == null || value.isBlank()) return false;

        if ("all".equalsIgnoreCase(group)) return true;
        return value.equalsIgnoreCase(group);
    }
}
