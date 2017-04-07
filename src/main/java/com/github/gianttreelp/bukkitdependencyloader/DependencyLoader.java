package com.github.gianttreelp.bukkitdependencyloader;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * This class downloads an {@link Artifact} using Eclipse Aether and loads it into the classpath.
 * The Apache Maven repository is added by default, as is the local repository for storing
 * downloaded artifacts.
 *
 * Example usage:
 * <code>
 * // Loads the kotlin runtime into the classpath
 * DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
 * if(!loader.isArtifactLoaded("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1")) {
 *      loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
 * }
 * </code>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DependencyLoader {

    /**
     * A {@link Set} of {@link Artifact}s that are already loaded.
     */
    private static Set<Artifact> artifacts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * This is the {@link Plugin} that created this {@link DependencyLoader}
     * We track the plugin in order to write to its log.
     */
    private Plugin plugin;

    /**
     * A list of type {@link RemoteRepository}
     * This is unique for each instance to prevent malicious repositories
     * from resolving artifacts from other repositories.
     */
    private List<RemoteRepository> repositories = new ArrayList<>();

    /**
     * The {@link RepositorySystem} to use for resolving the artifacts.
     */
    private RepositorySystem system;

    /**
     * The {@link DefaultRepositorySystemSession} for resolving this {@link Plugin}'s artifacts.
     * The same session is reused for each artifact of a {@link Plugin}.
     */
    private DefaultRepositorySystemSession session;

    /**
     * The constructor.
     * This takes a {@link Plugin} as input to use its {@link java.util.logging.Logger}
     *
     * @param plugin the {@link Plugin} this dependency loader is meant to work for.
     */
    DependencyLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        system = locator.getService(RepositorySystem.class);
        session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(Bukkit.getPluginManager().getPlugin("BukkitDependencyLoader").getDataFolder().getAbsolutePath() + "/.m2");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        addRepository("central", "https://repo.maven.apache.org/maven2/");
    }

    /**
     * Checks whether a specific artifact has already been loaded.
     * This only checks artifacts provided by this plugin and not any externally loaded classes.
     *
     * @param groupId    the groupId of the artifact
     * @param artifactId the artifactId of the artifact
     * @param version    the version of the artifact to check
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String, String, String)
     * @see #loadArtifact(String)
     * @see #isArtifactLoaded(String)
     */
    public static boolean isArtifactLoaded(String groupId, String artifactId, String version) {
        return artifacts.contains(new DefaultArtifact(groupId, artifactId, "jar", version));
    }

    /**
     * Checks whether a specific artifact has already been loaded.
     * This only checks artifacts provided by this plugin and not any externally loaded classes.
     *
     * @param coordinates the concatenated coordinates of the artifact to check
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String, String, String)
     * @see #loadArtifact(String)
     * @see #isArtifactLoaded(String, String, String)
     */
    public static boolean isArtifactLoaded(String coordinates) {
        return artifacts.contains(new DefaultArtifact(coordinates));
    }

    /**
     * Adds a {@link RemoteRepository} that will be used to resolve {@link Artifact}s.
     *
     * @param id  the identifier to identify the repository with
     * @param url the base url to search artifacts from
     */
    public void addRepository(String id, String url) {
        repositories.add(new RemoteRepository.Builder(id, "default", url).build());
    }

    /**
     * Builds an {@link Artifact} and passes it off to be loaded into the classpath.
     * This method uses the Maven syntax.
     *
     * @param groupId    the groupId of the artifact
     * @param artifactId the artifactId of the artifact
     * @param version    the version of the artifact to load
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String)
     * @see #isArtifactLoaded(String, String, String)
     * @see #isArtifactLoaded(String)
     */
    public boolean loadArtifact(String groupId, String artifactId, String version) {
        plugin.getLogger().info(String.format("Loading artifact %s:%s:%s", groupId, artifactId, version));
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        return loadArtifact(artifact);
    }

    /**
     * Builds an {@link Artifact} and passes it off to be loaded into the classpath.
     * This method uses the concatenated (Gradle?) syntax.
     *
     * @param coordinates the concatenated coordinates of the artifact to load
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String, String, String)
     * @see #isArtifactLoaded(String, String, String)
     * @see #isArtifactLoaded(String)
     */
    public boolean loadArtifact(String coordinates) {
        plugin.getLogger().info(String.format("Loading artifact %s", coordinates));
        Artifact artifact = new DefaultArtifact(coordinates);
        return loadArtifact(artifact);
    }

    /**
     * Loads an artifact into the classpath.
     * <p>
     * This method builds an {@link ArtifactRequest} to resolve the artifact that has been passed in.
     * A {@link URLClassLoader} is used to download and load the artifact into the path.
     * <p>
     * Exceptions are caught and will be printed; in turn <code>false</code>
     * will be returned in case of an exception.
     *
     * @param artifact the artifact to download and load into the classpath
     * @return whether the artifact has been loaded successfully
     */
    public boolean loadArtifact(Artifact artifact) {
        ArtifactRequest request = new ArtifactRequest();
        repositories.forEach(request::addRepository);
        request.setArtifact(artifact);
        ArtifactResult artifactResult;
        try {
            artifactResult = system.resolveArtifact(session, request);
        } catch (ArtifactResolutionException e) {
            e.printStackTrace();
            return false;
        }
        artifact = artifactResult.getArtifact();
        try {
            new URLClassLoader(new URL[]{artifact.getFile().toURI().toURL()});
            plugin.getLogger().info(String.format("Successfully loaded %s", artifact));
            artifacts.add(artifact);
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
