package com.example.demo;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.ServiceLocator;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.classpath.ClasspathTransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DependencyTreeDownloader {
    private static final String DEFAULT_REPO_LOCAL = String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final RemoteRepository DEFAULT_REPO_REMOTE = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();
    private static final Set<String> DEFAULT_SCOPES = Set.of(JavaScopes.RUNTIME);

    private static final RepositorySystem system;

    static {
        org.eclipse.aether.impl.DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, ClasspathTransporterFactory.class);


        system = locator.getService(RepositorySystem.class);
    }

    public static List<String> resolve(String... coords) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        return resolve(List.of(coords), DEFAULT_SCOPES, DEFAULT_REPO_LOCAL, List.of(DEFAULT_REPO_REMOTE));
    }

    public static List<String> resolve(List<String> coords, Set<String> scopes, String localRepo, List<RemoteRepository> remoteRepos) throws DependencyResolutionException, NoLocalRepositoryManagerException {
        if (coords.isEmpty()) return Collections.emptyList();
        if (scopes == null || scopes.isEmpty()) scopes = DEFAULT_SCOPES;
        if (localRepo == null) localRepo = DEFAULT_REPO_LOCAL;
        if (remoteRepos == null || remoteRepos.isEmpty()) remoteRepos = List.of(DEFAULT_REPO_REMOTE);

        RepositorySystemSession session = buildSession(localRepo);

//        List<Dependency> dependencies = coords.stream().map(DefaultArtifact::new).map(artifact -> new Dependency(artifact, null)).toList();
//        var collectRequest = new CollectRequest(dependencies, null, remoteRepos);
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRepositories(remoteRepos);


        Artifact artifact = new DefaultArtifact(coords.get(0));
        Dependency dependency = new Dependency(artifact, null);
        collectRequest.setRoot(dependency);


        var request = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(scopes));
//        DependencyResult result = system.resolveDependencies(session, request);

        // 解析依赖树
        DependencyResult dependencyResult;
        try {
            dependencyResult = system.resolveDependencies(session, request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve dependencies", e);
        }



        // 下载所有 Artifact
        List<String> jarPaths = new ArrayList<>();
        for (DependencyNode node : dependencyResult.getRoot().getChildren()) {
            Artifact nodeArtifact = node.getDependency().getArtifact();
            ArtifactRequest artifactRequest = new ArtifactRequest(nodeArtifact, remoteRepos, null);
            try {
                ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
                jarPaths.add(artifactResult.getArtifact().getFile().getAbsolutePath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to download artifact: " + nodeArtifact, e);
            }
        }

        return jarPaths;

    }

    private static RepositorySystemSession buildSession(String localRepo) {
        var session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setCache(new DefaultRepositoryCache());
        return session;
    }

    public static void test() throws DependencyResolutionException, NoLocalRepositoryManagerException {
        String localRepo = "out";
        RemoteRepository aliRepo = new RemoteRepository.Builder("aliyun", "default", "https://maven.aliyun.com/repository/central").build();
        List<RemoteRepository> remotes = List.of(DEFAULT_REPO_REMOTE, aliRepo);
        var coords = List.of("org.apache.logging.log4j:log4j-core:2.20.0");
        List<String> jars = resolve(coords, null, localRepo, remotes);
        System.out.printf(">>>>>> jars: %s%n", jars);
    }
    public static void main(String[] args) throws DependencyResolutionException, NoLocalRepositoryManagerException {

        test();
    }
}
