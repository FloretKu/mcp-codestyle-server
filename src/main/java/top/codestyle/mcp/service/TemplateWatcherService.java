package top.codestyle.mcp.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模板监听服务
 * <p>
 * 利用 Java WatchService 实时监听指定目录下的文件修改事件。
 * 当开发者在 IDE 中编辑并保存代码文件时，自动捕获变更事件，
 * 为后续的 AI 意图分析和模板自动演进提供数据流。
 *
 * <p>
 * 借鉴 DeerFlow 的 Long-Term Memory 理念：
 * 不仅跟踪 Git 提交历史，还实时捕获每一次保存操作的增量变更。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateWatcherService {

    /**
     * 文件变更事件记录
     */
    public record FileChangeEvent(
            String filePath,
            WatchEvent.Kind<?> eventType,
            long timestamp) {
    }

    /** 变更事件队列，供外部消费 */
    private final BlockingQueue<FileChangeEvent> changeQueue = new LinkedBlockingQueue<>(1000);

    /** 当前是否正在监听 */
    private final AtomicBoolean watching = new AtomicBoolean(false);

    /** 监听线程执行器 */
    private ExecutorService watchExecutor;

    /** 当前正在监听的路径 */
    private volatile String watchedPath;

    /**
     * 开始监听指定目录
     *
     * @param directoryPath 要监听的目录路径
     */
    public void startWatching(String directoryPath) {
        if (watching.compareAndSet(false, true)) {
            this.watchedPath = directoryPath;
            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "template-watcher");
                t.setDaemon(true);
                return t;
            });
            watchExecutor.submit(() -> watchLoop(directoryPath));
            log.info("开始监听目录: {}", directoryPath);
        } else {
            log.warn("已有监听任务运行中，路径: {}", watchedPath);
        }
    }

    /**
     * 停止监听
     */
    public void stopWatching() {
        if (watching.compareAndSet(true, false)) {
            if (watchExecutor != null) {
                watchExecutor.shutdownNow();
            }
            log.info("停止监听目录: {}", watchedPath);
        }
    }

    /**
     * 获取待处理的变更事件（非阻塞）
     *
     * @param maxEvents 最多获取的事件数
     * @return 变更事件列表
     */
    public java.util.List<FileChangeEvent> drainEvents(int maxEvents) {
        java.util.List<FileChangeEvent> events = new java.util.ArrayList<>();
        changeQueue.drainTo(events, maxEvents);
        return events;
    }

    /**
     * 获取当前队列中的事件数量
     */
    public int pendingEventCount() {
        return changeQueue.size();
    }

    /**
     * 是否正在监听
     */
    public boolean isWatching() {
        return watching.get();
    }

    @PreDestroy
    public void destroy() {
        stopWatching();
    }

    /**
     * 核心监听循环
     */
    private void watchLoop(String directoryPath) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path dir = Paths.get(directoryPath);
            // 注册目录及子目录
            registerRecursive(dir, watchService);

            while (watching.get() && !Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.poll(500, TimeUnit.MILLISECONDS);
                if (key == null)
                    continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW)
                        continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = ((Path) key.watchable()).resolve(pathEvent.context());
                    String changedStr = changed.toString();

                    // 只关注源码文件
                    if (isSourceFile(changedStr)) {
                        FileChangeEvent changeEvent = new FileChangeEvent(
                                changedStr, event.kind(), System.currentTimeMillis());
                        if (!changeQueue.offer(changeEvent)) {
                            log.debug("变更事件队列已满，丢弃: {}", changedStr);
                        }
                    }

                    // 如果新建了子目录，也监听它
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                        registerRecursive(changed, watchService);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("WatchService 异常: {}", e.getMessage());
        }
    }

    private void registerRecursive(Path dir, WatchService watchService) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                String name = d.getFileName().toString();
                if (name.startsWith(".") || "node_modules".equals(name)
                        || "target".equals(name) || "build".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                d.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isSourceFile(String path) {
        return path.endsWith(".java") || path.endsWith(".py") || path.endsWith(".js")
                || path.endsWith(".ts") || path.endsWith(".tsx") || path.endsWith(".go")
                || path.endsWith(".vue") || path.endsWith(".kt");
    }
}
