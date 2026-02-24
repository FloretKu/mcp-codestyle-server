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
        if (args.length >= 1 && isCliCommand(args[0])) {
            runCliMode(args);
        } else {
            SpringApplication.run(McpServerApplication.class, args);
        }
    }

    private static boolean isCliCommand(String command) {
        return "search".equals(command) || "get".equals(command) || 
               "upload".equals(command) || "delete".equals(command) || 
               "help".equals(command);
    }

    private static void runCliMode(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(McpServerApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            CodestyleService service = ctx.getBean(CodestyleService.class);
            String result;
            
            switch (args[0]) {
                case "search":
                    if (args.length < 2) {
                        System.err.println("用法: search <关键词>");
                        System.exit(1);
                    }
                    result = service.codestyleSearch(args[1]);
                    break;
                    
                case "get":
                    if (args.length < 2) {
                        System.err.println("用法: get <模板路径>");
                        System.exit(1);
                    }
                    result = service.getTemplateByPath(args[1]);
                    break;
                    
                case "upload":
                    result = handleUploadCommand(args, service);
                    break;
                    
                case "delete":
                    if (args.length < 2) {
                        System.err.println("用法: delete <模板路径>");
                        System.err.println("示例: delete continew/CRUD/1.0.0");
                        System.exit(1);
                    }
                    result = service.deleteTemplate(args[1]);
                    break;
                    
                case "help":
                    result = getHelpMessage();
                    break;
                    
                default:
                    System.err.println("未知命令: " + args[0]);
                    System.err.println("使用 'help' 查看帮助信息");
                    System.exit(1);
                    return;
            }
            
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String handleUploadCommand(String[] args, CodestyleService service) {
        String sourcePath = null;
        String groupId = null;
        String artifactId = null;
        String version = null;
        boolean overwrite = false;
        
        // 解析参数
        for (int i = 1; i < args.length; i++) {
            if ("--path".equals(args[i]) && i + 1 < args.length) {
                sourcePath = args[++i];
            } else if ("--group".equals(args[i]) && i + 1 < args.length) {
                groupId = args[++i];
            } else if ("--artifact".equals(args[i]) && i + 1 < args.length) {
                artifactId = args[++i];
            } else if ("--version".equals(args[i]) && i + 1 < args.length) {
                version = args[++i];
            } else if ("--overwrite".equals(args[i])) {
                overwrite = true;
            }
        }
        
        if (sourcePath == null) {
            System.err.println("用法:");
            System.err.println("  1. 从文件系统上传:");
            System.err.println("     upload --path <文件系统路径> --group <groupId> --artifact <artifactId> --version <version> [--overwrite]");
            System.err.println("     示例: upload --path E:/templates/CRUD --group continew --artifact CRUD --version 1.0.0");
            System.err.println();
            System.err.println("  2. 从仓库路径上传（已在仓库中）:");
            System.err.println("     upload --path <仓库路径> [--overwrite]");
            System.err.println("     示例: upload --path continew/CRUD/1.0.0");
            System.exit(1);
        }
        
        // 判断是文件系统路径还是仓库路径
        if (groupId != null || artifactId != null || version != null) {
            // 文件系统路径模式
            if (groupId == null || artifactId == null || version == null) {
                System.err.println("错误: 使用文件系统路径时，必须同时指定 --group, --artifact, --version");
                System.exit(1);
            }
            return service.uploadTemplateFromFileSystem(sourcePath, groupId, artifactId, version, overwrite);
        } else {
            // 仓库路径模式
            return service.uploadTemplate(sourcePath, overwrite);
        }
    }
    
    private static String getHelpMessage() {
        return """
                Codestyle Server - 代码模板检索与生成工具
                
                用法:
                  java -jar codestyle-server.jar <命令> [参数]
                
                命令:
                  search <关键词>     搜索代码模板
                                     示例: search "CRUD"
                                     示例: search "continew/CRUD"
                  
                  get <模板路径>      获取模板文件内容
                                     示例: get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
                  
                  upload              上传模板到本地/远程
                    方式1 - 从文件系统上传:
                      upload --path <文件系统路径> --group <groupId> --artifact <artifactId> --version <version> [--overwrite]
                      示例: upload --path E:/templates/CRUD --group continew --artifact CRUD --version 1.0.0
                    
                    方式2 - 从仓库路径上传（已在仓库中）:
                      upload --path <仓库路径> [--overwrite]
                      示例: upload --path continew/CRUD/1.0.0
                  
                  delete <模板路径>   删除指定版本的模板
                                     示例: delete continew/CRUD/1.0.0
                  
                  help               显示此帮助信息
                
                配置:
                  通过环境变量配置:
                    CODESTYLE_CACHE_PATH       - 本地缓存路径（默认: ~/.codestyle/cache）
                    CODESTYLE_REMOTE_ENABLED   - 是否启用远程检索（默认: false）
                
                  通过 cfg.json 配置:
                    repository.remote.base-url    - 远程服务地址
                    repository.remote.access-key  - 访问密钥
                    repository.remote.secret-key  - 密钥
                    repository.remote.timeout-ms  - 超时时间
                
                模式说明:
                  本地模式 (remote.enabled=false):
                    - upload: 复制到本地缓存并重建索引
                    - delete: 删除本地缓存并重建索引
                  
                  远程模式 (remote.enabled=true):
                    - upload: 复制到本地 + 上传到远程服务器
                    - delete: 删除本地 + 删除远程服务器
                """;
    }
}