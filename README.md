# bukkitdependencyloader
A plugin to dynamically load artifacts from maven repositories.

Example usage:
 
     // Loads the kotlin runtime into the classpath
     DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
     if(!loader.isArtifactLoaded("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1")) {
          loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
     }
