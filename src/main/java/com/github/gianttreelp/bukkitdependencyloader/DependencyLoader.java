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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class downloads an {@link Artifact} using Eclipse Aether and loads it
 * into the classpath.
 * The Apache Maven repository is added by default, as is the local
 * repository for storing downloaded artifacts.
 * <p>
 * Example usage:
 * <code>
 * // Loads the kotlin runtime into the classpath.
 * DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
 * loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
 * </code>
 */
@SuppressWarnings({"WeakerAccess", "unused", "SameParameterValue"})
public final class DependencyLoader {

    /**
     * This is the {@link Plugin} that created this {@link DependencyLoader}
     * We track the plugin in order to write to its log and load the
     * artifacts into its classloader.
     */
    private final Plugin plugin;

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
     * The {@link DefaultRepositorySystemSession} for resolving this
     * {@link Plugin}'s artifacts.
     * The same session is reused for each artifact of a {@link Plugin}.
     */
    private DefaultRepositorySystemSession session;

    /**
     * The constructor.
     * This takes a {@link Plugin} as input to use its
     * {@link java.util.logging.Logger}
     *
     * @param javaPlugin the {@link Plugin} this dependency loader is meant to
     *                   work for.
     */
    DependencyLoader(final JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        DefaultServiceLocator locator =
                MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class,
                BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class,
                FileTransporterFactory.class);
        locator.addService(TransporterFactory.class,
                HttpTransporterFactory.class);
        system = locator.getService(RepositorySystem.class);
        session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(
                Bukkit.getPluginManager()
                        .getPlugin("BukkitDependencyLoader")
                        .getDataFolder().getAbsolutePath() + "/.m2");
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, localRepo));
        addRepository(
                "central", "https://repo.maven.apache.org/maven2/");
    }

    /**
     * Adds a {@link RemoteRepository} that will be used to resolve
     * {@link Artifact}s.
     *
     * @param id  the identifier to identify the repository with
     * @param url the base url to search artifacts from
     */
    public void addRepository(final String id, final String url) {
        repositories.add(
                new RemoteRepository.Builder(id, "default", url).build());
    }

    /**
     * Builds an {@link Artifact} and passes it off to be loaded into the
     * classpath.
     * This method uses the Maven syntax.
     *
     * @param groupId    the groupId of the artifact
     * @param artifactId the artifactId of the artifact
     * @param version    the version of the artifact to load
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String)
     */
    public boolean loadArtifact(final String groupId, final String artifactId,
                                final String version) {
        plugin.getLogger().info(
                String.format(
                        "Loading artifact %s:%s:%s",
                        groupId, artifactId, version));
        Artifact artifact =
                new DefaultArtifact(
                        groupId, artifactId, "jar", version);
        return loadArtifact(artifact);
    }

    /**
     * Builds an {@link Artifact} and passes it off to be loaded into the
     * classpath.
     * This method uses the concatenated (Gradle?) syntax.
     *
     * @param coordinates the concatenated coordinates of the artifact to load
     * @return whether the artifact has been successfully loaded
     * @see #loadArtifact(String, String, String)
     */
    public boolean loadArtifact(final String coordinates) {
        plugin.getLogger().info(
                String.format("Loading artifact %s", coordinates));
        Artifact artifact = new DefaultArtifact(coordinates);
        return loadArtifact(artifact);
    }

    /**
     * Loads an {@link Artifact} into the classpath.
     * <p>
     * This method builds an {@link ArtifactRequest} to resolve the artifact
     * that has been passed in.
     * It then loads the artifact into the {@link Plugin}'s classpath.
     * <p>
     * Returns early, if an artifact is already loaded.
     *
     * @param artifact the artifact to download and load into the classpath
     * @return whether the artifact has been loaded successfully
     */
    public boolean loadArtifact(final Artifact artifact) {
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
        return loadArtifactIntoClassPath(artifactResult.getArtifact());
    }

    /**
     * This loads an {@link Artifact} into the {@link #plugin}'s
     * <p>
     * A {@link URLClassLoader} is used to download and load the artifact
     * into the path.
     * <p>
     * Exceptions are caught and will be printed; in turn <code>false</code>
     * will be returned in case of an exception.
     * <p>
     *
     * @param artifact the artifact to load
     * @return whether the artifact has been successfully loaded into the
     * plugin's classpath
     */
    private boolean loadArtifactIntoClassPath(final Artifact artifact) {
        try {
            Field fClassloader = JavaPlugin.class.getDeclaredField(
                    "classLoader");
            fClassloader.setAccessible(true);
            URLClassLoader ucl = (URLClassLoader) fClassloader.get(plugin);

            Method mAddUrl = URLClassLoader.class.getDeclaredMethod(
                    "addURL", URL.class);
            mAddUrl.setAccessible(true);
            mAddUrl.invoke(ucl, artifact.getFile().toURI().toURL());

            plugin.getLogger().info(String.format(
                    "Successfully loaded %s", artifact));
            return true;
        } catch (MalformedURLException | NoSuchFieldException
                | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException e) {
            e.printStackTrace();
            return false;
        }
    }

}
