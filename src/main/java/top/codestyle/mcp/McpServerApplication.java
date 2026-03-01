package top.codestyle.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import top.codestyle.mcp.service.CodestyleService;

/**
 * MCP 代码模板服务器应用程序
 * <p>提供代码模板搜索、下载、管理等 MCP 工具服务。
 * <p>支持两种运行模式：
 * <ul>
 *   <li>MCP 模式（默认）：作为 MCP 服务器供 AI 大模型调用</li>
 *   <li>CLI 模式：通过命令行参数直接操作模板</li>
 * </ul>
 *
 * <p><b>注意：</b>MCP 模式下 stdout 用于 JSON-RPC 协议通信，
 * 所有日志输出均通过 SLF4J 写入 stderr/日志文件，避免污染协议通道。
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-03
 */
@Slf4j
@SpringBootApplication
public class McpServerApplication {

    /**
     * 应用程序入口
     * <p>根据命令行参数自动判断运行模式：
     * CLI 命令模式或 MCP 服务器模式。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args.length >= 1 && isCliCommand(args[0])) {
            runCliMode(args);
        } else {
            SpringApplication.run(McpServerApplication.class, args);
        }
    }

    /**
     * 检查命令是否为有效的 CLI 命令
     *
     * @param command 命令字符串
     * @return 是否为已注册的 CLI 命令
     */
    private static boolean isCliCommand(String command) {
        return "search".equals(command) || "get".equals(command) || 
               "upload".equals(command) || "delete".equals(command) || 
               "help".equals(command);
    }

    /**
     * CLI 模式入口
     * <p>以非 Web 方式启动 Spring 容器，执行完毕后自动关闭。
     * 所有输出通过 SLF4J 日志记录，不使用 stdout 以避免干扰 MCP 协议。
     *
     * @param args 命令行参数，args[0] 为命令名
     */
    private static void runCliMode(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(McpServerApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            CodestyleService service = ctx.getBean(CodestyleService.class);
            String result;
            
            switch (args[0]) {
                case "search":
                    if (args.length < 2) {
                        log.error("用法: search <关键词>");
                        return;
                    }
                    result = service.codestyleSearch(args[1]);
                    break;
                    
                case "get":
                    if (args.length < 2) {
                        log.error("用法: get <模板路径>");
                        return;
                    }
                    result = service.getTemplateByPath(args[1]);
                    break;
                    
                case "upload":
                    result = handleUploadCommand(args, service);
                    if (result == null) return;
                    break;
                    
                case "delete":
                    if (args.length < 2) {
                        log.error("用法: delete <模板路径>");
                        log.error("示例: delete continew/CRUD/1.0.0");
                        return;
                    }
                    result = service.deleteTemplate(args[1]);
                    break;
                    
                case "help":
                    result = getHelpMessage();
                    break;
                    
                default:
                    log.error("未知命令: {}，使用 'help' 查看帮助信息", args[0]);
                    return;
            }
            
            log.info("\n{}", result);
        } catch (Exception e) {
            log.error("执行失败: {}", e.getMessage());
        }
    }

    /**
     * 处理 upload 命令的参数解析与执行
     *
     * @param args    命令行参数
     * @param service 模板服务实例
     * @return 执行结果字符串，参数不合法时返回 null
     */
    private static String handleUploadCommand(String[] args, CodestyleService service) {
        String sourcePath = null;
        String groupId = null;
        String artifactId = null;
        String version = null;
        boolean overwrite = false;
        
        // 解析命名参数
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
            log.error("用法:");
            log.error("  1. 从文件系统上传:");
            log.error("     upload --path <文件系统路径> --group <groupId> --artifact <artifactId> --version <version> [--overwrite]");
            log.error("     示例: upload --path E:/templates/CRUD --group continew --artifact CRUD --version 1.0.0");
            log.error("  2. 从仓库路径上传（已在仓库中）:");
            log.error("     upload --path <仓库路径> [--overwrite]");
            log.error("     示例: upload --path continew/CRUD/1.0.0");
            return null;
        }
        
        // 判断是文件系统路径还是仓库路径
        if (groupId != null || artifactId != null || version != null) {
            if (groupId == null || artifactId == null || version == null) {
                log.error("错误: 使用文件系统路径时，必须同时指定 --group, --artifact, --version");
                return null;
            }
            return service.uploadTemplateFromFileSystem(sourcePath, groupId, artifactId, version, overwrite);
        } else {
            return service.uploadTemplate(sourcePath, overwrite);
        }
    }
    
    /**
     * 获取 CLI 帮助信息
     *
     * @return 格式化的帮助信息字符串
     */
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