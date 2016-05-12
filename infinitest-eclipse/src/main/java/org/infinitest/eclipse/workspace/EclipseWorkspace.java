/*
 * Infinitest, a Continuous Test Runner.
 *
 * Copyright (C) 2010-2013
 * "Ben Rady" <benrady@gmail.com>,
 * "Rod Coffin" <rfciii@gmail.com>,
 * "Ryan Breidenbach" <ryan.breidenbach@gmail.com>
 * "David Gageot" <david@gageot.net>, et al.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.infinitest.eclipse.workspace;

import static com.google.common.collect.Lists.newArrayList;
import static org.infinitest.eclipse.InfinitestCoreClasspath.getCoreJarLocation;
import static org.infinitest.eclipse.workspace.WorkspaceStatusFactory.findingTests;
import static org.infinitest.eclipse.workspace.WorkspaceStatusFactory.noTestCasesFound;
import static org.infinitest.eclipse.workspace.WorkspaceStatusFactory.noTestsRun;
import static org.infinitest.eclipse.workspace.WorkspaceStatusFactory.workspaceErrors;
import static org.infinitest.util.Events.eventFor;
import static org.infinitest.util.InfinitestUtils.log;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.infinitest.InfinitestCore;
import org.infinitest.RuntimeEnvironment;
import org.infinitest.eclipse.InfinitestPlugin;
import org.infinitest.eclipse.UpdateListener;
import org.infinitest.eclipse.event.CoreUpdateNotifier;
import org.infinitest.eclipse.status.WorkspaceStatus;
import org.infinitest.eclipse.status.WorkspaceStatusListener;
import org.infinitest.util.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class EclipseWorkspace implements WorkspaceFacade {
	private final CoreRegistry coreRegistry;
	private final CoreFactory coreFactory;
	private WorkspaceStatus status;
	private final List<WorkspaceStatusListener> statusListeners = newArrayList();
	private final Events<UpdateListener> updateEvent = eventFor(UpdateListener.class);
	private final ProjectSet projectSet;

	@Autowired
	EclipseWorkspace(ProjectSet projectSet, CoreRegistry coreRegistry, CoreFactory coreFactory) {
		this.projectSet = projectSet;
		this.coreRegistry = coreRegistry;
		this.coreFactory = coreFactory;
	}

	@Autowired
	public void addStatusListeners(WorkspaceStatusListener... listeners) {
		Collections.addAll(statusListeners, listeners);
	}

	@Override
	public void updateProjects() throws CoreException {
		if (projectSet.hasErrors()) {
			setStatus(workspaceErrors());
		} else {
			int numberOfTestsToRun = updateProjectsIn(projectSet);
			System.out.println("CoreUpdateNotifier.globalFlag:"+CoreUpdateNotifier.globalFlag);
			if (numberOfTestsToRun == 0) {
				setStatus(noTestsRun());
			}
			if(CoreUpdateNotifier.globalFlag){
				setStatus(noTestCasesFound());
			}
		}
	}

	public void setStatus(WorkspaceStatus newStatus) {
		status = newStatus;
		for (WorkspaceStatusListener each : statusListeners) {
			each.statusChanged(newStatus);
		}
	}

	public WorkspaceStatus getStatus() {
		return status;
	}

	private int updateProjectsIn(ProjectSet projectSet) throws CoreException {
		updateEvent.fire();
		int totalTests = 0;
		for (ProjectFacade project : projectSet.projects()) {
			setStatus(findingTests(totalTests));
			totalTests += updateProject(project);
		}
		return totalTests;
	}

	private int updateProject(ProjectFacade project) throws CoreException {
		RuntimeEnvironment environment = buildRuntimeEnvironment(project);
		InfinitestCore core = coreRegistry.getCore(project.getLocationURI());
		if (core == null) {
			core = createCore(project, environment);
		}
		core.setRuntimeEnvironment(environment);
		return core.update();
	}

	public RuntimeEnvironment buildRuntimeEnvironment(ProjectFacade project) throws CoreException {
		File javaHome = project.getJvmHome();
		RuntimeEnvironment environment = buildRuntimeEnvironment(project, javaHome);
		environment.setInfinitestRuntimeClassPath(getCoreJarLocation(InfinitestPlugin.getInstance()).getAbsolutePath());
		return environment;
	}

	private RuntimeEnvironment buildRuntimeEnvironment(ProjectFacade project, File javaHome) throws CoreException {
		return new RuntimeEnvironment(projectSet.outputDirectories(project), project.workingDirectory(), project.rawClasspath(), javaHome);
	}

	private InfinitestCore createCore(ProjectFacade project, RuntimeEnvironment environment) {
		InfinitestCore core = coreFactory.createCore(project.getName(), environment);
		coreRegistry.addCore(project.getLocationURI(), core);
		log("Added core " + core.getName() + " with classpath " + environment.getCompleteClasspath());
		return core;
	}

	public void addUpdateListeners(UpdateListener... updateListeners) {
		for (UpdateListener each : updateListeners) {
			updateEvent.addListener(each);
		}
	}
}