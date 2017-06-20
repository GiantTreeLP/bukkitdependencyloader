package com.github.gianttreelp.bukkitdependencyloader;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
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
import org.eclipse.aether.transport.classpath.ClasspathTransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This {@link org.bukkit.plugin.Plugin} offers a way to dynamically load
 * {@link org.eclipse.aether.artifact.Artifact}s at runtime without adding
 * them to a plugin's jar.
 * <p>
 * This allows other plugins to reuse artifacts and in turn reduces the file
 * size of many plugins.
 * <p>
 * Specification of the dependencies.conf file:
 * </p>
 * <ul>
 * <li>Lines that do <i>not</i> start with "artifact=" or "repository="
 * are ignored.
 * </li>
 * <li>
 * The proper format for an artifact line is:<br>
 * artifact=&lt;groupId&gt;:&lt;artifactId&gt;[:&lt;
 * extension&gt;[:&lt;classifier
 * &gt;]]:&lt;version&gt;
 * </li>
 * <li>
 * The proper format for a repository is:<br>
 * repository=&lt;id&gt;:&lt;url&gt;
 * </li>
 * <li>
 * For an example, look at this plugin's
 * <a href="https://github.com/GiantTreeLP/bukkitdependencyloader/blob/master/src/main/resources/dependencies.conf">
 * dependencies.conf
 * </a>
 * </li>
 * </ul>
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
     * All the  work is kicked of here to make sure the dependencies are
     * loaded before any other plugin is initialized.
     */
    public DependencyLoaderPlugin() {
        super();
        dependencyLoader = new DependencyLoader();
        scanPluginsAndLoadArtifacts();
    }

    /**
     * List all files in the directory given and filters them for their
     * extension.
     * This returns only files!
     *
     * @param directory the directory where the files reside
     * @param extension the extension to filter for
     * @return a list containing all files satisfying the
     * extensions' constraint
     */
    @SuppressWarnings("SameParameterValue")
    private static List<File> getFilesWithExtension(final File directory,
                                                    final String extension) {
        String[] files = directory.list();
        if (files != null) {
            return Arrays.stream(files).filter(s -> s.endsWith(extension))
                    .map(s -> new File(directory, s)).filter(File::isFile)
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * Scans the {@code plugins} directory for plugins and loads the
     * artifacts
     * present in the {@code dependencies.conf} file in the root of the
     * plugin's {@code .jar} file.
     */
    private void scanPluginsAndLoadArtifacts() {
        List<File> plugins = getFilesWithExtension(getDataFolder()
                .getParentFile(), ".jar");
        if (plugins == null || plugins.size() == 0) {
            getLogger().severe("No plugins found!");
            return;
        }

        mapToJarFile(plugins).forEach(pluginJar -> {
            JarEntry dependenciesEntry = pluginJar.getJarEntry(
                    DEPENDENCIES_CONF);
            if (dependenciesEntry == null) {
                return;
            }

            getLogger().info(String.format("Loading artifacts for %s",
                    pluginJar.getName()));

            String dependenciesString = getStringFromEntry(pluginJar,
                    dependenciesEntry);

            String[] lines = dependenciesString.split("\r?\n");

            Arrays.stream(lines).filter(line ->
                    line.startsWith(REPOSITORY_IDENTIFIER))
                    .forEach(this::parseRepository);

            Arrays.stream(lines).filter(line ->
                    line.startsWith(ARTIFACT_IDENTIFIER)).forEach(line -> {
                if (parseArtifact(line)) {
                    getLogger().info(String.format("Successfully "
                            + "loaded %s", line));
                } else {
                    getLogger().severe(String.format("Error loading "
                                    + "%s. Please check your network "
                                    + "connection and report"
                                    + "this to the developer of %s",
                            line,
                            pluginJar.getName()));
                }
            });
        });
    }

    /**
     * Reads a {@link ZipEntry}, decompresses it and returns the contents as a
     * String.
     * No validation is done except for checking the presence of the entry.
     *
     * @param file  the file to read the entry from.
     * @param entry the entry to read and return as a String.
     * @return a string representation of the decompressed contents of the
     * entry.
     */
    private String getStringFromEntry(final ZipFile file,
                                      final ZipEntry entry) {
        StringBuilder dependenciesStringBuilder = new StringBuilder();
        try (InputStream dependenciesStream = file.getInputStream(entry)) {
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
        return dependenciesStringBuilder.toString();
    }

    /**
     * Filter out {@link File} objects that are directories and map each of
     * the actual files to a {@link JarFile}.
     *
     * @param jarFiles the files which will be mapped to JarFiles
     * @return A stream of JarFiles, safe for use.
     */
    private Stream<JarFile> mapToJarFile(final List<File> jarFiles) {
        return jarFiles.stream().filter(File::isFile).map(file -> {
            try {
                return new JarFile(file);
            } catch (IOException e) {
                getLogger().severe(String.format("Error reading %s, please "
                        + "fix this and restart the server", file));
                getLogger().throwing(this.getClass().getName(),
                        "mapToJarFile", e);
            }
            return null;
        });
    }

    /**
     * Parses a line of text coming from
     * {@link #scanPluginsAndLoadArtifacts()} and loads an artifact to the
     * {@link #dependencyLoader}
     * <p>
     * Artifacts' syntax is the same as the implementation of the
     * {@link DefaultArtifact}:<br>
     * {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
     * <br>and must not be {@code null}.
     *
     * @param line the line of text to parse; it is known that it starts with
     *             {@link #ARTIFACT_IDENTIFIER}
     * @return whether the artifact has been successfully loaded
     * into the class path.
     */
    private boolean parseArtifact(final String line) {
        return dependencyLoader.loadArtifact(line.replace(ARTIFACT_IDENTIFIER,
                ""));
    }

    /**
     * Parses a line of text coming from
     * {@link #scanPluginsAndLoadArtifacts()} and adds a repository to the
     * {@link #dependencyLoader}.
     * <p>
     * Repositories' syntax is:
     * <code>
     * repository=&lt;id&gt;:&lt;url&gt;
     * </code>
     *
     * @param line the line of text to parse; it is known that it starts with
     *             {@link #REPOSITORY_IDENTIFIER}
     */
    private void parseRepository(final String line) {
        String newLine = line.replace(REPOSITORY_IDENTIFIER, "");
        int indexColon = newLine.indexOf(':');
        dependencyLoader.addRepository(newLine.substring(0, indexColon),
                newLine.substring(indexColon + 1));
    }


    /**
     * This class downloads an {@link Artifact} using Eclipse Aether and
     * loads it into the classpath.
     * The Apache Maven repository is added by default, as is the local
     * repository for storing downloaded artifacts.
     */
    private final class DependencyLoader {

        /**
         * A set of type {@link RemoteRepository}.
         * Prevents duplicate repository entries.
         */
        private Set<RemoteRepository> repositories = new HashSet<>();

        /**
         * The {@link RepositorySystem} to use for resolving the artifacts.
         */
        private RepositorySystem system;

        /**
         * The {@link DefaultRepositorySystemSession} for resolving artifacts.
         * The same session is reused for each artifact.
         */
        private DefaultRepositorySystemSession session;

        /**
         * The constructor.
         * Initializes the local repository and add the central repository as
         * a default repository, so it's not necessary to add it manually.
         */
        private DependencyLoader() {
            DefaultServiceLocator locator =
                    MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(RepositoryConnectorFactory.class,
                    BasicRepositoryConnectorFactory.class);

            locator.addService(TransporterFactory.class,
                    FileTransporterFactory.class);
            locator.addService(TransporterFactory.class,
                    HttpTransporterFactory.class);
            locator.addService(TransporterFactory.class,
                    ClasspathTransporterFactory.class);

            system = locator.getService(RepositorySystem.class);
            session = MavenRepositorySystemUtils.newSession();
            LocalRepository localRepo = new LocalRepository(
                    getDataFolder().getAbsolutePath() + "/.m2");
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
            RemoteRepository repository = new RemoteRepository.Builder(
                    id, "default", url).build();
            repositories.add(repository);
            getLogger().info(String.format("Added remote repository %s",
                    repository));
        }

        /**
         * Builds an {@link Artifact} and passes it off to be loaded into the
         * classpath.
         * This method uses the concatenated (Gradle?) syntax.
         *
         * @param coordinates the concatenated coordinates of the artifact
         *                    to load
         * @return whether the artifact has been successfully loaded
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
         * It then loads the artifact into the classpath.
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
                getLogger().severe(String.format(
                        "Artifact %scould not be resolved!", artifact));
                getLogger().throwing(this.getClass().getName(),
                        "loadArtifact", e);
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
         * Exceptions are caught and will be printed; in turn {@code false}
         * will be returned in case of an exception.
         * <p>
         *
         * @param artifact the artifact to load
         * @return whether the artifact has been successfully loaded into the
         * server's classpath
         */
        private boolean loadArtifactIntoClassPath(final Artifact artifact) {
            try {

                URLClassLoader ucl = (URLClassLoader) JavaPluginLoader.class
                        .getClassLoader();

                Method mAddUrl = URLClassLoader.class.getDeclaredMethod(
                        "addURL", URL.class);
                mAddUrl.setAccessible(true);
                mAddUrl.invoke(ucl, artifact.getFile().toURI().toURL());
                return true;
            } catch (IllegalAccessException | MalformedURLException
                    | InvocationTargetException
                    | NoSuchMethodException e) {
                getLogger().severe(String.format("Error loading artifact %s",
                        artifact));
                getLogger().throwing("DependencyLoader",
                        "loadArtifactIntoGlobalClassPath", e);
                return false;
            }
        }
    }
}
