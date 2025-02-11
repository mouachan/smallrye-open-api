package io.smallrye.openapi.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.Result;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Component(role = MavenDependencyIndexCreator.class, instantiationStrategy = "singleton")
public class MavenDependencyIndexCreator {

    private final Cache<String, IndexView> indexCache = CacheBuilder.newBuilder().build();

    private final Set<String> ignoredArtifacts = new HashSet<>();

    @Requirement
    private Logger logger;

    public MavenDependencyIndexCreator() {
        ignoredArtifacts.add("org.graalvm.sdk:graal-sdk");
        ignoredArtifacts.add("org.yaml:snakeyaml");
        ignoredArtifacts.add("org.wildfly.common:wildfly-common");
        ignoredArtifacts.add("com.fasterxml.jackson.core:jackson-core");
        ignoredArtifacts.add("com.fasterxml.jackson.core:jackson-databind");
        ignoredArtifacts.add("io.smallrye.reactive:smallrye-mutiny-vertx-core");
        ignoredArtifacts.add("commons-io:commons-io");
        ignoredArtifacts.add("io.smallrye.reactive:mutiny");
        ignoredArtifacts.add("org.jboss.narayana.jta:narayana-jta");
        ignoredArtifacts.add("org.glassfish.jaxb:jaxb-runtime");
        ignoredArtifacts.add("com.github.ben-manes.caffeine:caffeine");
        ignoredArtifacts.add("org.hibernate.validator:hibernate-validator");
        ignoredArtifacts.add("io.smallrye.config:smallrye-config-core");
        ignoredArtifacts.add("com.thoughtworks.xstream:xstream");
        ignoredArtifacts.add("com.github.javaparser:javaparser-core");
        ignoredArtifacts.add("org.jboss:jandex");

        ignoredArtifacts.add("antlr");
        ignoredArtifacts.add("io.netty");
        ignoredArtifacts.add("org.drools");
        ignoredArtifacts.add("net.bytebuddy");
        ignoredArtifacts.add("org.hibernate");
        ignoredArtifacts.add("org.kie");
        ignoredArtifacts.add("org.postgresql");
        ignoredArtifacts.add("org.apache.httpcomponents");
    }

    public IndexView createIndex(MavenProject mavenProject, boolean scanDependenciesDisable,
            List<String> includeDependenciesScopes, List<String> includeDependenciesTypes) throws Exception {

        List<Map.Entry<Artifact, Duration>> indexDurations = new ArrayList<>();

        // Don't' cache the moduleindex. Incremental compilation in IDE's would otherwise use the cached index instead of new one.
        // Right now, support for incremental compilation inside eclipse is blocked by: https://github.com/eclipse-m2e/m2e-core/issues/364#issuecomment-939987848
        IndexView moduleIndex;
        try {
            moduleIndex = indexModuleClasses(mavenProject);
        } catch (IOException e) {
            throw new MojoExecutionException("Can't compute index", e);
        }

        if (scanDependenciesDisable) {
            return moduleIndex;
        }

        List<IndexView> indexes = new ArrayList<>();
        indexes.add(moduleIndex);
        for (Artifact artifact : mavenProject.getArtifacts()) {
            if (isIgnored(artifact, includeDependenciesScopes, includeDependenciesTypes)) {
                continue;
            }

            IndexView artifactIndex = timeAndCache(indexDurations, artifact, () -> {
                try {
                    Result result = JarIndexer.createJarIndex(artifact.getFile(), new Indexer(),
                            false, false, false);
                    return result.getIndex();
                } catch (Exception e) {
                    logger.error("Can't compute index of " + artifact.getFile().getAbsolutePath() + ", skipping", e);
                    return null;
                }
            });

            if (artifactIndex != null) {
                indexes.add(artifactIndex);
            }
        }

        printIndexDurations(indexDurations);

        return CompositeIndex.create(indexes);
    }

    private void printIndexDurations(List<Map.Entry<Artifact, Duration>> indexDurations) {
        if (logger.isDebugEnabled()) {
            indexDurations.sort(Map.Entry.comparingByValue());

            indexDurations.forEach(e -> {
                if (e.getValue().toMillis() > 25) {
                    logger.debug(buildGAVCTString(e.getKey()) + " " + e.getValue());
                }
            });
        }
    }

    private boolean isIgnored(Artifact artifact, List<String> includeDependenciesScopes,
            List<String> includeDependenciesTypes) {
        return !includeDependenciesScopes.contains(artifact.getScope())
                || !includeDependenciesTypes.contains(artifact.getType())
                || ignoredArtifacts.contains(artifact.getGroupId())
                || ignoredArtifacts.contains(artifact.getGroupId() + ":" + artifact.getArtifactId());
    }

    private IndexView timeAndCache(List<Map.Entry<Artifact, Duration>> indexDurations, Artifact artifact,
            Callable<IndexView> callable) throws Exception {
        LocalDateTime start = LocalDateTime.now();
        IndexView result = indexCache.get(buildGAVCTString(artifact), callable);
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(start, end);
        indexDurations.add(new AbstractMap.SimpleEntry<>(artifact, duration));

        return result;
    }

    // index the classes of this Maven module
    private Index indexModuleClasses(MavenProject mavenProject) throws IOException {

        Indexer indexer = new Indexer();

        // Check first if the classes directory exists, before attempting to create an index for the classes
        File outputDirectory = new File(mavenProject.getBuild().getOutputDirectory());
        if (outputDirectory.exists()) {
            try (Stream<Path> stream = Files.walk(outputDirectory.toPath())) {

                List<Path> classFiles = stream
                        .filter(path -> path.toString().endsWith(".class"))
                        .collect(Collectors.toList());
                for (Path path : classFiles) {
                    indexer.index(Files.newInputStream(path));
                }
            }
        }
        return indexer.complete();
    }

    private String buildGAVCTString(Artifact artifact) {
        return artifact.getGroupId() +
                ":" +
                artifact.getArtifactId() +
                ":" +
                artifact.getVersion() +
                ":" +
                artifact.getClassifier() +
                ":" +
                artifact.getType();
    }
}
