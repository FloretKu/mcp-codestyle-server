package top.codestyle.mcp.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * P4: 当 codestyle.tool-group 为该值或 "all" 时加载该 Bean。
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(CodestyleToolGroupCondition.class)
public @interface ConditionalOnCodestyleToolGroup {
    /** code-analysis 或 template */
    String value();
}
