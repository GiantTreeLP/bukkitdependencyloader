package com.github.gianttreelp.bukkitdependencyloader;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.aether.artifact.Artifact;

/**
 * This {@link Plugin} offers a way to get a {@link DependencyLoader} for
 * your plugin and use it to dynamically load
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
     * Empty constructor, we don't need to do anything here.
     */
    public DependencyLoaderPlugin() {
    }

    /**
     * Creates a new {@link DependencyLoader} for a given {@link Plugin}
     *
     * @param plugin the plugin that requested the dependency loader
     * @return a new instance of {@link DependencyLoader}
     */
    public static DependencyLoader forPlugin(final JavaPlugin plugin) {
        return new DependencyLoader(plugin);
    }


    @Override
    public void onEnable() {
        super.onEnable();
        loadOwnArtifacts();
    }


    /**
     * Loads its own {@link Artifact}s as a way to test the connection and
     * to provide those artifacts completely,
     * because some functionality might be lost when minifying the plugin.
     * <p>
     * It is known that none of these artifacts are loaded because we are the
     * first plugin to actually load anything.
     */
    private void loadOwnArtifacts() {
        DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
        loader.loadArtifact(
                "org.slf4j", "slf4j-api", "1.7.25");
        loader.loadArtifact(
                "org.slf4j", "slf4j-jdk14", "1.7.25");
        loader.loadArtifact(
                "org.eclipse.aether",
                "aether-impl", "1.1.0");
        loader.loadArtifact(
                "org.eclipse.aether",
                "aether-connector-basic", "1.1.0");
        loader.loadArtifact(
                "org.eclipse.aether",
                "aether-transport-http", "1.1.0");
        loader.loadArtifact(
                "org.eclipse.aether",
                "aether-transport-file", "1.1.0");
        loader.loadArtifact(
                "org.apache.maven",
                "maven-aether-provider", "3.3.9");
    }

}
