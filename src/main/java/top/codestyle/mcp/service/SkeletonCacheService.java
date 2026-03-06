package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.DependencyGraph;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骨架与依赖图缓存
 * <p>
 * 以 projectPath 为 key 缓存 ProjectSkeleton 与 DependencyGraph，
 * 支持过期时间，供 extractProjectSkeleton 写入、exploreCodeContext 读取。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class SkeletonCacheService {

    @Value("${codestyle.cache.skeleton-ttl-ms:3600000}")
    private long skeletonTtlMs;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record CachedSkeleton(ProjectSkeleton skeleton, DependencyGraph graph, NameIndex nameIndex, long buildTime) {}

    private record CacheEntry(CachedSkeleton data, long expireAt) {}

    private static String normalizeKey(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) return "";
        try {
            return Paths.get(projectPath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return projectPath;
        }
    }

    public void put(String projectPath, ProjectSkeleton skeleton, DependencyGraph graph) {
        put(projectPath, skeleton, graph, null);
    }

    public void put(String projectPath, ProjectSkeleton skeleton, DependencyGraph graph, NameIndex nameIndex) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty()) return;
        long now = System.currentTimeMillis();
        NameIndex idx = nameIndex != null ? nameIndex : (graph != null ? NameIndex.from(graph.getNodeIndex()) : null);
        CachedSkeleton data = new CachedSkeleton(skeleton, graph, idx, now);
        cache.put(key, new CacheEntry(data, now + skeletonTtlMs));
        log.debug("骨架缓存已写入: {} (ttl={}ms)", key, skeletonTtlMs);
    }

    public CachedSkeleton get(String projectPath) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty()) return null;
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expireAt()) {
            cache.remove(key);
            return null;
        }
        return entry.data();
    }

    public void invalidate(String projectPath) {
        cache.remove(normalizeKey(projectPath));
    }

    public void invalidateAll() {
        cache.clear();
    }
}
