/*******************************************************************************
 * Copyright (c) 2004, 2008 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.examples.monitor.study;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.mylyn.internal.monitor.ui.MonitorUiPlugin;
import org.eclipse.mylyn.internal.monitor.usage.UiUsageMonitorPlugin;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.editors.text.EditorsPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * @author Mik Kersten
 */
public class MylynUserStudyExamplePlugin extends AbstractUIPlugin {

	public static final String ID_PLUGIN = "org.eclipse.mylyn.examples.monitor.study";

	private static MylynUserStudyExamplePlugin plugin;

	private SelectionMonitor selectionMonitor;

	public MylynUserStudyExamplePlugin() {
		plugin = this;
	}

	public static class MylynUserStudyExampleStartup implements IStartup {

		public void earlyStartup() {
			final IWorkbench workbench = PlatformUI.getWorkbench();
			workbench.getDisplay().asyncExec(new Runnable() {
				public void run() {
					MylynUserStudyExamplePlugin.getDefault().selectionMonitor = new SelectionMonitor();
					MonitorUiPlugin.getDefault().getSelectionMonitors().add(
							MylynUserStudyExamplePlugin.getDefault().selectionMonitor);

					UiUsageMonitorPlugin.getDefault().addMonitoredPreferences(
							WorkbenchPlugin.getDefault().getPreferenceStore());
					UiUsageMonitorPlugin.getDefault().addMonitoredPreferences(
							JavaPlugin.getDefault().getPreferenceStore());
					UiUsageMonitorPlugin.getDefault().addMonitoredPreferences(
							WorkbenchPlugin.getDefault().getPreferenceStore());
					UiUsageMonitorPlugin.getDefault().addMonitoredPreferences(
							EditorsPlugin.getDefault().getPreferenceStore());
					UiUsageMonitorPlugin.getDefault().addMonitoredPreferences(
							PDEPlugin.getDefault().getPreferenceStore());
				}
			});
		}
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
		plugin = null;

		MonitorUiPlugin.getDefault().getSelectionMonitors().remove(selectionMonitor);
		UiUsageMonitorPlugin.getDefault().removeMonitoredPreferences(WorkbenchPlugin.getDefault().getPreferenceStore());
		UiUsageMonitorPlugin.getDefault().removeMonitoredPreferences(JavaPlugin.getDefault().getPreferenceStore());
		UiUsageMonitorPlugin.getDefault().removeMonitoredPreferences(WorkbenchPlugin.getDefault().getPreferenceStore());
		UiUsageMonitorPlugin.getDefault().removeMonitoredPreferences(EditorsPlugin.getDefault().getPreferenceStore());
		UiUsageMonitorPlugin.getDefault().removeMonitoredPreferences(PDEPlugin.getDefault().getPreferenceStore());
	}

	/**
	 * Returns the shared instance.
	 */
	public static MylynUserStudyExamplePlugin getDefault() {
		return plugin;
	}

}
