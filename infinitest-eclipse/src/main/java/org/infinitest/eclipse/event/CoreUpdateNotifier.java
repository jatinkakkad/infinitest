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
package org.infinitest.eclipse.event;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.infinitest.EventQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CoreUpdateNotifier implements IResourceChangeListener {
	private final List<EclipseEventProcessor> processors;
	private final EventQueue queue;
	List<String> source = new ArrayList<String>();
	List<String> test = new ArrayList<String>();
	public static List<String> toReturn = new ArrayList<String>();
	public static boolean globalFlag = false;
	String className = "";

	@Autowired
	CoreUpdateNotifier(EventQueue queue) {
		this.queue = queue;
		
		processors = newArrayList();
	}

	@Autowired
	public void addProcessor(EclipseEventProcessor... eventProcessors) {
		processors.addAll(asList(eventProcessors));
	}

	/**
	 * http://www.eclipse.org/articles/Article-Resource-deltas/resource-deltas.
	 * html
	 */
	@Override
	public void resourceChanged(final IResourceChangeEvent event) {
		if (event.getDelta() != null) {
			IResourceDelta[] allChanges = event.getDelta()
					.getAffectedChildren();
			System.out.println("allChanges" + allChanges);
			if (allChanges != null) {
				System.out.println("all changes length:" + allChanges.length);
				for (IResourceDelta delta : allChanges) {
					System.out.println("delta.getKind():" + delta.getKind());
					try {
						IProject[] projects = ResourcesPlugin.getWorkspace()
								.getRoot().getProjects();
						for (IProject project : projects) {
							System.out.println("Working in project "
									+ project.getName());
							// check if we have a Java project
							if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
								System.out.println("Is a java project");
								
								// visits resource deltas
								ResourceDeltaVisitor resourceDeltaVisitor = new ResourceDeltaVisitor();
								delta.accept(resourceDeltaVisitor);
								ArrayList<IResource> changedClasses = resourceDeltaVisitor
										.getChangedResources();
								System.out.println("changedClasses size:"
										+ changedClasses.size());
								for (IResource changedClass : changedClasses) {
									ICompilationUnit iCompilationUnit = (ICompilationUnit) JavaCore
											.create(changedClass);
									System.out.println("iCompilationUnit:"
											+ iCompilationUnit);
									prepareSourceList(iCompilationUnit);
								}
								IJavaProject javaProject = JavaCore
										.create(project);
								//Used to populate Test List
								prepareTestList(javaProject);
								
								System.out.println("source size:" + source.size());
								System.out.println("test size:" + test.size());
								if (!source.isEmpty() && !test.isEmpty() && !source.equals(test)) {
									toReturn = new ArrayList(source);
									toReturn.removeAll(test);
									for (int i = 0; i < toReturn.size(); i++) {
										System.out.println("to return:" + toReturn.get(i));
									}
									if (toReturn != null && !toReturn.isEmpty()) {
										globalFlag = true;
									} else {
										globalFlag = false;
									}
								} else if(!source.isEmpty() && test.isEmpty()){
									toReturn = new ArrayList(source);
									globalFlag = true;
								}else{
									globalFlag = false;
								}
							}
							
						}

					} catch (CoreException e1) {
						e1.printStackTrace();
					}
				}
			}
			processEvent(event);
			source = new ArrayList<String>();
			test = new ArrayList<String>();
		}
	}

	private void prepareSourceList(ICompilationUnit unit) throws JavaModelException {
		IType[] allTypes = unit.getAllTypes();
		className = unit.getElementName();
		for (IType type : allTypes) {
			printAllMethodDetails(type);
		}
	}

	private void printAllMethodDetails(IType type) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		System.out.println("classname#####:" + className);
		if (!className.contains("test") && !className.contains("Test")) {
			for (IMethod method : methods) {
				System.out.println("Source Method name " + method.getElementName());
				if (!source.contains(method.getElementName().toLowerCase())) {
					source.add(method.getElementName().toLowerCase());
				}
			}
		}else{
			for (IMethod method : methods) {
				System.out.println("Test Method name " + method.getElementName());
				String val = method.getElementName().toLowerCase()
						.substring(4, method.getElementName().length());
				if (!test.contains(val)) {
					test.add(method.getElementName().toLowerCase()
							.substring(4, method.getElementName().length()));
				}
				
			}
		}

	}

	public void processEvent(IResourceChangeEvent event) {
		for (EclipseEventProcessor processor : processors) {
			if (processor.canProcessEvent(event)) {
				queue.pushNamed(new EventProcessorRunnable(processor, event));
			}
		}
	}

	private void prepareTestList(IJavaProject javaProject)
			throws JavaModelException {
		IPackageFragment[] packages = javaProject.getPackageFragments();
		for (IPackageFragment mypackage : packages) {
			if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
				System.out.println("Package " + mypackage.getElementName());
				printICompilationUnitInfo(mypackage);

			}

		}
	}

	private void printICompilationUnitInfo(IPackageFragment mypackage)
			throws JavaModelException {
		for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
			printCompilationUnitDetails(unit);

		}
	}

	private void printMethods(ICompilationUnit unit, String classType) throws JavaModelException {
		IType[] allTypes = unit.getAllTypes();
		for (IType type : allTypes) {
			printMethodDetails(type, classType);
		}
	}

	private void printCompilationUnitDetails(ICompilationUnit unit)
			throws JavaModelException {
		System.out.println("Source file " + unit.getElementName()+"className:"+className);
		if(!className.contains("Test")){
			String tempString = className;
			tempString = tempString.replace(".java", "Test.java");
			System.out.println("tempString:"+tempString);
			if (unit.getElementName().equalsIgnoreCase(tempString)) {
				printMethods(unit, "test");
			}
		}else{
				printMethods(unit, "source");
		}
	}

	private void printMethodDetails(IType type, String classType) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		if(classType.equalsIgnoreCase("source")){
			for (IMethod method : methods) {
				String val = method.getElementName().toLowerCase();
				if (!source.contains(val) && !val.contains("test")) {
					source.add(method.getElementName().toLowerCase());
				}
				System.out.println("Source Method name " + method.getElementName());
			}
		}else{
			for (IMethod method : methods) {
				String val = method.getElementName().toLowerCase()
						.substring(4, method.getElementName().length());
				if (!test.contains(val)) {
					test.add(method.getElementName().toLowerCase()
							.substring(4, method.getElementName().length()));
				}
				System.out.println("Test Method name " + method.getElementName());
			}
		}
		
	}
}