package top.codestyle.mcp.test;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.ProjectSkeleton;
import top.codestyle.mcp.service.DependencyGraphBuilder;
import top.codestyle.mcp.service.RepoMasterScoringService;
import top.codestyle.mcp.service.SkeletonCacheService;
import top.codestyle.mcp.service.SkeletonFormatterService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 项目骨架、依赖图、剪枝、格式化与缓存相关测试
 */
class SkeletonAndExploreTest {

    private static ProjectSkeleton minimalSkeleton() {
        AstNode fileA = AstNode.builder()
                .nodeType("file")
                .name("src/main/java/ServiceA.java")
                .filePath("src/main/java/ServiceA.java")
                .children(new ArrayList<>(List.of(
                        AstNode.builder().nodeType("class").name("ServiceA").filePath("src/main/java/ServiceA.java").children(new ArrayList<>()).build()
                )))
                .build();
        AstNode fileB = AstNode.builder()
                .nodeType("file")
                .name("src/main/java/ServiceB.java")
                .filePath("src/main/java/ServiceB.java")
                .children(new ArrayList<>(List.of(
                        AstNode.builder().nodeType("class").name("ServiceB").filePath("src/main/java/ServiceB.java").children(new ArrayList<>()).build()
                )))
                .build();
        List<ProjectSkeleton.DependencyEdge> deps = new ArrayList<>();
        deps.add(ProjectSkeleton.DependencyEdge.builder().source("src/main/java/ServiceA.java").target("pkg.ServiceB").type("import").build());
        return ProjectSkeleton.builder()
                .projectPath("/tmp/proj")
                .totalFiles(2)
                .totalClasses(2)
                .totalMethods(0)
                .fileNodes(List.of(fileA, fileB))
                .dependencies(deps)
                .directoryNodes(new ArrayList<>())
                .fileSummaries(java.util.Map.of("src/main/java/ServiceA.java", "class ServiceA", "src/main/java/ServiceB.java", "class ServiceB"))
                .build();
    }

    @Test
    void dependencyGraphBuilderBuildsGraph() {
        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        ProjectSkeleton skeleton = minimalSkeleton();
        var graph = builder.build(skeleton);
        assertNotNull(graph);
        assertNotNull(graph.getGraph());
        assertTrue(graph.getGraph().vertexSet().size() >= 2);
        assertNotNull(graph.getNodeIndex());
    }

    @Test
    void scoringServiceScoresAndPrunes() {
        RepoMasterScoringService scoring = new RepoMasterScoringService();
        ReflectionTestUtils.setField(scoring, "alpha", 0.45);
        ReflectionTestUtils.setField(scoring, "beta", 0.25);
        ReflectionTestUtils.setField(scoring, "gamma", 0.15);
        ReflectionTestUtils.setField(scoring, "delta", 0.15);
        ProjectSkeleton skeleton = minimalSkeleton();
        var scores = scoring.scoreNodes(skeleton, null);
        assertNotNull(scores);
        assertFalse(scores.isEmpty());
        ProjectSkeleton pruned = scoring.prune(skeleton, 2, null);
        assertNotNull(pruned);
        assertTrue(pruned.getTotalFiles() <= skeleton.getTotalFiles());
    }

    @Test
    void formatterProducesMarkdownAndXml() {
        SkeletonFormatterService formatter = new SkeletonFormatterService();
        ProjectSkeleton skeleton = minimalSkeleton();
        String out = formatter.format(skeleton, 2);
        assertNotNull(out);
        assertTrue(out.contains("# Project Skeleton"));
        assertTrue(out.contains("<directory_tree>"));
        assertTrue(out.contains("</directory_tree>"));
        assertTrue(out.contains("<ast_skeleton"));
        assertTrue(out.contains("<dependency_graph"));
    }

    @Test
    void cachePutAndGet() {
        SkeletonCacheService cache = new SkeletonCacheService();
        ReflectionTestUtils.setField(cache, "skeletonTtlMs", 3600000L);
        ProjectSkeleton skeleton = minimalSkeleton();
        cache.put("/tmp/proj", skeleton, null);
        var got = cache.get("/tmp/proj");
        assertNotNull(got);
        assertEquals(skeleton.getProjectPath(), got.skeleton().getProjectPath());
        cache.invalidate("/tmp/proj");
        assertNull(cache.get("/tmp/proj"));
    }
}
