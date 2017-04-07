package com.github.gianttreelp.bukkitdependencyloader;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * This {@link Plugin} offers a way to get a {@link DependencyLoader} for your plugin
 * and use it to dynamically load {@link org.eclipse.aether.artifact.Artifact}s at runtime
 * without adding them to the jar.
 *
 * This allows other plugins to reuse artifacts and in turn reduces the file size
 * of many plugins.
 *
 * Example usage:
 *
 * <code>
 * // Loads the kotlin runtime into the classpath
 * DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
 * loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
 * </code>
 */
@SuppressWarnings("WeakerAccess")
public class DependencyLoaderPlugin extends JavaPlugin {

    /**
     * Empty constructor, we don't need to do anything here.
     */
    public DependencyLoaderPlugin() {}

    public static DependencyLoader forPlugin(JavaPlugin plugin) {
        return new DependencyLoader(plugin);
    }


    @Override
    public void onEnable() {
        super.onEnable();
        loadOwnArtifacts();
    }


    private void loadOwnArtifacts() {
        DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
        loader.loadArtifact("org.eclipse.aether", "aether-impl", "1.1.0");
        loader.loadArtifact("org.eclipse.aether", "aether-connector-basic", "1.1.0");
        loader.loadArtifact("org.eclipse.aether", "aether-transport-http", "1.1.0");
        loader.loadArtifact("org.eclipse.aether", "aether-transport-file", "1.1.0");
        loader.loadArtifact("org.apache.maven", "maven-aether-provider", "3.3.9");
    }

}
