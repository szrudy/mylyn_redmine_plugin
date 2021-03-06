/*******************************************************************************
 * Copyright (c) 2004, 2008 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ken Sueda - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.sandbox.ui.planner;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.mylyn.internal.tasks.core.AbstractTask;

/**
 * @author Ken Sueda
 */
public class CompletedTaskCollector implements ITaskCollector {

	private final Map<String, AbstractTask> completedTasks = new HashMap<String, AbstractTask>();

	private final Date periodStartDate;

	private final Date periodEndDate;

	public CompletedTaskCollector(Date periodStartDate, Date periodEndDate) {
		this.periodStartDate = periodStartDate;
		this.periodEndDate = periodEndDate;

	}

	public String getLabel() {
		return "Completed Tasks";
	}

	public void consumeTask(AbstractTask task) {
		if (task.isCompleted() && task.getCompletionDate() != null
				&& task.getCompletionDate().compareTo(periodStartDate) >= 0
				&& task.getCompletionDate().compareTo(periodEndDate) <= 0
				&& !completedTasks.containsKey(task.getHandleIdentifier())) {
			completedTasks.put(task.getHandleIdentifier(), task);
		}
	}

	public Set<AbstractTask> getTasks() {
		Set<AbstractTask> tasks = new HashSet<AbstractTask>();
		tasks.addAll(completedTasks.values());
		return tasks;
	}
}
