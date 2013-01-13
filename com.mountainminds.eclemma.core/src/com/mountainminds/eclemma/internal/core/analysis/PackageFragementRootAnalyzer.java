/*******************************************************************************
 * Copyright (c) 2006, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 ******************************************************************************/
package com.mountainminds.eclemma.internal.core.analysis;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IDirectivesParser.SourceFileDirectivesParser;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ISourceFileLocator;

import com.mountainminds.eclemma.core.EclEmmaStatus;
import com.mountainminds.eclemma.core.ICorePreferences;
import com.mountainminds.eclemma.internal.core.EclEmmaCorePlugin;
import com.mountainminds.eclemma.internal.core.SessionExporter;

/**
 * Analyzes the class files that belong to given package fragment roots. This
 * analyzer implements an cache to remember the class files that have been
 * analyzed before.
 */
final class PackageFragementRootAnalyzer {

  private final ExecutionDataStore executiondata;
  private final Map<Object, AnalyzedNodes> cache;
  private ICorePreferences preferences;

  PackageFragementRootAnalyzer(final ExecutionDataStore executiondata) {
    this.executiondata = executiondata;
    this.cache = new HashMap<Object, AnalyzedNodes>();

    EclEmmaCorePlugin corePlugin = EclEmmaCorePlugin.getInstance();
    preferences = corePlugin.getPreferences();
  }

  AnalyzedNodes analyze(final IPackageFragmentRoot root) throws CoreException {
    if (root.isExternal()) {
      return analyzeExternal(root);
    } else {
      return analyzeInternal(root);
    }
  }

  private AnalyzedNodes analyzeInternal(final IPackageFragmentRoot root)
      throws CoreException {
    IResource location = null;
    try {
      location = getClassfilesLocation(root);

      AnalyzedNodes nodes = cache.get(location);
      if (nodes != null) {
        return nodes;
      }

      final CoverageBuilder builder = new CoverageBuilder();

      SourceFileDirectivesParser directivesParser = null;
      ISourceFileLocator sourceFileLocator = createSourceFileLocator(root);
      if (sourceFileLocator != null) {
        boolean requireComment = ICorePreferences.PREF_AGENT_SOURCEDIRECTIVES_ENABLE_REQUIRECOMMENT
            .equals(preferences.getAnalysisSourceDirectives());
        directivesParser = new SourceFileDirectivesParser(sourceFileLocator,
            requireComment);
      }

      final Analyzer analyzer = new Analyzer(executiondata, builder,
          directivesParser);
      new ResourceTreeWalker(analyzer).walk(location);
      nodes = new AnalyzedNodes(builder.getClasses(), builder.getSourceFiles());
      cache.put(location, nodes);
      return nodes;
    } catch (Exception e) {
      throw new CoreException(EclEmmaStatus.BUNDLE_ANALYSIS_ERROR.getStatus(
          root.getElementName(), location, e));
    }
  }

  private ISourceFileLocator createSourceFileLocator(IPackageFragmentRoot root)
      throws JavaModelException {
    if (ICorePreferences.PREF_AGENT_SOURCEDIRECTIVES_DISABLE.equals(preferences
        .getAnalysisSourceDirectives())) {
      return null;
    } else {
      return SessionExporter.createSourceFileLocator(root);
    }
  }

  private AnalyzedNodes analyzeExternal(final IPackageFragmentRoot root)
      throws CoreException {
    IPath location = null;
    try {
      location = root.getPath();

      AnalyzedNodes nodes = cache.get(location);
      if (nodes != null) {
        return nodes;
      }

      final CoverageBuilder builder = new CoverageBuilder();
      final Analyzer analyzer = new Analyzer(executiondata, builder);
      new ResourceTreeWalker(analyzer).walk(location);
      nodes = new AnalyzedNodes(builder.getClasses(), builder.getSourceFiles());
      cache.put(location, nodes);
      return nodes;
    } catch (Exception e) {
      throw new CoreException(EclEmmaStatus.BUNDLE_ANALYSIS_ERROR.getStatus(
          root.getElementName(), location, e));
    }
  }

  private IResource getClassfilesLocation(IPackageFragmentRoot root)
      throws CoreException {

    // For binary roots the underlying resource directly points to class files:
    if (root.getKind() == IPackageFragmentRoot.K_BINARY) {
      return root.getResource();
    }

    // For source roots we need to find the corresponding output folder:
    IPath path = root.getRawClasspathEntry().getOutputLocation();
    if (path == null) {
      path = root.getJavaProject().getOutputLocation();
    }
    return root.getResource().getWorkspace().getRoot().findMember(path);
  }

}
