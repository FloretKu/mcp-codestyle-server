package top.codestyle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import top.codestyle.mcp.service.CodestyleService;

/**
 * MCP代码模板服务器应用程序
 * 提供代码模板搜索、下载、管理等MCP工具服务
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-03
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        if (args.length >= 2 && ("search".equals(args[0]) || "get".equals(args[0]))) {
            runCliMode(args);
        } else {
            SpringApplication.run(McpServerApplication.class, args);
        }
    }

    private static void runCliMode(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(McpServerApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            CodestyleService service = ctx.getBean(CodestyleService.class);
            String result = "search".equals(args[0])
                    ? service.codestyleSearch(args[1])
                    : service.getTemplateByPath(args[1]);
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}