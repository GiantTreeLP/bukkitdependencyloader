package com.github.gianttreelp.bukkitdependencyloader;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This {@link org.bukkit.plugin.Plugin} offers a way to get a
 * {@link DependencyLoader} for your plugin and use it to dynamically load
 * {@link org.eclipse.aether.artifact.Artifact}s at runtime  without adding
 * them to the jar.
 * <p>
 * This allows other plugins to reuse artifacts and in turn reduces the file
 * size
 * of many plugins.
 * <p>
 * Example usage:
 * <p>
 * <code>
 * // Loads the kotlin runtime into the classpath
 * DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
 * loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
 * </code>
 */
@SuppressWarnings("WeakerAccess")
public final class DependencyLoaderPlugin extends JavaPlugin {

    /**
     * The name of the file we search for in the plugin's jar file.
     */
    private static final String DEPENDENCIES_CONF = "dependencies.conf";

    /**
     * The identifier to identify a repository line.
     */
    private static final String REPOSITORY_IDENTIFIER = "repository=";

    /**
     * The identifier to identify an artifact line.
     */
    private static final String ARTIFACT_IDENTIFIER = "artifact=";

    /**
     * The {@link DependencyLoader} we use to load artifacts.
     * Subject for removal.
     */
    private DependencyLoader dependencyLoader;

    /**
     * Empty constructor, we don't need to do anything here.
     */
    public DependencyLoaderPlugin() {
    }

    @Override
    public void onLoad() {
        dependencyLoader = new DependencyLoader();
        scanPluginsAndLoadArtifacts();
    }

    /**
     * Scans the `plugins` directory for Plugins and loads the artifacts
     * present in the `dependencies.conf` file in the root of the plugin's
     * `.jar`file.
     */
    private void scanPluginsAndLoadArtifacts() {
        File pluginsDirectory = new File(getDataFolder().getParentFile()
                .getAbsolutePath());
        File[] plugins = pluginsDirectory.listFiles((dir, name) -> name
                .endsWith(".jar"));
        if (plugins == null) {
            getLogger().severe("No plugins found! Please report this error "
                    + "to the author of this plugin (BukkitDependencyLoader)");
            throw new NullPointerException("plugins should never be null");
        }
        Arrays.stream(plugins).map(file -> {
            try {
                return new JarFile(file);
            } catch (IOException e) {
                getLogger().severe(String.format("Error reading %s, please "
                        + "fix this and restart the server", file));
                getLogger().throwing(this.getClass().getName(),
                        "scanPluginsAndLoadArtifacts", e);
            }
            return null;
        }).forEach(pluginJar -> {
            JarEntry dependenciesEntry = pluginJar.getJarEntry(
                    DEPENDENCIES_CONF);
            if (dependenciesEntry != null) {
                getLogger().info(String.format("Loading artifacts for %s",
                        pluginJar.getName()));
                StringBuilder dependenciesStringBuilder = new StringBuilder();
                try (InputStream dependenciesStream = pluginJar
                        .getInputStream(dependenciesEntry)) {
                    while (dependenciesStream.available() > 0) {
                        dependenciesStringBuilder.appendCodePoint(
                                dependenciesStream.read());
                    }
                } catch (IOException e) {
                    getLogger().severe("Error reading dependencies, please "
                            + "fix this and restart the server");
                    getLogger().throwing(this.getClass().getName(),
                            "scanPluginsAndLoadArtifacts", e);
                }
                String dependenciesString = dependenciesStringBuilder
                        .toString();

                for (String line : dependenciesString.split("\r?\n")) {
                    if (line.startsWith(REPOSITORY_IDENTIFIER)) {
                        parseRepository(line);
                    } else if (line.startsWith(ARTIFACT_IDENTIFIER)) {
                        parseArtifact(line);
                    }
                }
            }
        });
    }

    /**
     * Parses a line of text coming from
     * {@link #scanPluginsAndLoadArtifacts()} and loads an artifact to the
     * {@link #dependencyLoader}
     * <p>
     * Artifacts' syntax is the same as the implementation of the
     * {@link DefaultArtifact}:
     * {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
     * must not be {@code null}.
     *
     * @param line the line of text to parse; it is known that it starts with
     *             {@link #ARTIFACT_IDENTIFIER}
     */
    private void parseArtifact(final String line) {
        dependencyLoader.loadArtifact(line.replace(ARTIFACT_IDENTIFIER, ""));
    }

    /**
     * Parses a line of text coming from
     * {@link #scanPluginsAndLoadArtifacts()} and adds a repository to the
     * {@link #dependencyLoader}.
     * <p>
     * Repositories' syntax is:
     * <code>
     * repository=<id>:<url>
     * </code>
     *
     * @param line the line of text to parse; it is known that it starts with
     *             {@link #REPOSITORY_IDENTIFIER}
     */
    private void parseRepository(final String line) {
        String[] split = line.replace(REPOSITORY_IDENTIFIER, "").split(":");
        dependencyLoader.addRepository(split[0], split[1]);
    }


    /**
     * This class downloads an {@link Artifact} using Eclipse Aether and
     * loads it into the classpath.
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
    private final class DependencyLoader {

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
         * {@link org.bukkit.plugin.Plugin's artifacts.
         * The same session is reused for each artifact of a
         * {@link org.bukkit.plugin.Plugin}.
         */
        private DefaultRepositorySystemSession session;

        /**
         * The constructor.
         * Initializes the local repository and add the central repository as
         * a default repository, so it's not necessary to add it manually.
         */
        DependencyLoader() {
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
        public boolean loadArtifact(final String groupId,
                                    final String artifactId,
                                    final String version) {
            getLogger().info(
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
         * @param coordinates the concatenated coordinates of the artifact
         *                    to load
         * @return whether the artifact has been successfully loaded
         * @see #loadArtifact(String, String, String)
         */
        public boolean loadArtifact(final String coordinates) {
            getLogger().info(
                    String.format("Loading artifact %s", coordinates));
            Artifact artifact = new DefaultArtifact(coordinates);
            return loadArtifact(artifact);
        }

        /**
         * Loads an {@link Artifact} into the classpath.
         * <p>
         * This method builds an {@link ArtifactRequest} to resolve the artifact
         * that has been passed in.
         * It then loads the artifact into the
         * {@link org.bukkit.plugin.Plugin}'s classpath.
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
         * This loads an {@link Artifact} into the class path of the server.
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
         * server's classpath
         */
        private boolean loadArtifactIntoClassPath(final Artifact artifact) {
            try {
                Field fClassloader = JavaPluginLoader.class.getDeclaredField(
                        "classLoader");
                fClassloader.setAccessible(true);
                URLClassLoader ucl = (URLClassLoader) fClassloader.get(Bukkit
                        .getServer());

                Method mAddUrl = URLClassLoader.class.getDeclaredMethod(
                        "addURL", URL.class);
                mAddUrl.setAccessible(true);
                mAddUrl.invoke(ucl, artifact.getFile().toURI().toURL());

                getLogger().info(String.format("Loaded artifact %s", artifact));
                return true;
            } catch (IllegalAccessException | NoSuchFieldException
                    | MalformedURLException | InvocationTargetException
                    | NoSuchMethodException e) {
                getLogger().throwing("DependencyLoader",
                        "loadArtifactIntoGlobalClassPath", e);
                return false;
            }
        }
    }
}
