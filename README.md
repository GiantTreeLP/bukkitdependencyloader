# bukkitdependencyloader

[![Build Status](http://giant.ddnss.de:8180/buildStatus/icon?job=BukkitDependencyLoader)](http://giant.ddnss.de:8180/job/BukkitDependencyLoader/)

A plugin to dynamically load artifacts from maven repositories.

Example usage:
 
     // Loads the kotlin runtime into the classpath
     DependencyLoader loader = DependencyLoaderPlugin.forPlugin(this);
     loader.loadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.1.1");
