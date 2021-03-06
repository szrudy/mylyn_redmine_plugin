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

package org.eclipse.mylyn.internal.sandbox.ui.views;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.core.ScheduledTaskContainer;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskContainer;
import org.eclipse.mylyn.tasks.core.ITask.PriorityLevel;
import org.eclipse.ui.IMemento;

/**
 * @author Rob Elves
 */
public class TaskActivityViewSorter extends ViewerSorter {

	private static final String activitySortColumn = "org.eclipse.mylyn.tasklist.ui.views.taskactivitysorter.sortcolumn";

	private static final String activityViewSorterSettings = "org.eclipse.mylyn.tasklist.ui.views.taskactivitysorter.sortsettings";

	private int[] directions;

	private final static int ASCENDING = 1;

	private final static int DESCENDING = -1;

	private final static int PRIORITY = 0;

	private final static int DESCRIPTION = 1;

	private final static int ELAPSED = 2;

	private final static int ESTIMATED = 3;

	private final static int REMINDER = 4;

	private final static int LAST_ACTIVE = 5;

	private int sortColumn = LAST_ACTIVE;

	final static int[] DEFAULT_DIRECTIONS = { ASCENDING, ASCENDING, ASCENDING, DESCENDING, DESCENDING, DESCENDING,
			DESCENDING };

	public TaskActivityViewSorter() {
		resetState();
	}

	public void reverseDirection() {
		directions[sortColumn] *= -1;
	}

	public void setSortDirection(int direction) {
		if (direction == ASCENDING || direction == DESCENDING) {
			directions[sortColumn] = direction;
		}
	}

	public int getDirection() {
		return directions[sortColumn];
	}

	public int getSortColumn() {
		return sortColumn;
	}

	public void setSortColumn(int col) {
		if (col < 0 || col >= directions.length) {
			sortColumn = LAST_ACTIVE;
		} else {
			sortColumn = col;
		}
	}

	public void resetState() {
		directions = new int[DEFAULT_DIRECTIONS.length];
		System.arraycopy(DEFAULT_DIRECTIONS, 0, directions, 0, directions.length);
	}

	@SuppressWarnings("unchecked")
	private int compare(AbstractTask task1, AbstractTask task2) {
		if (sortColumn >= directions.length) {
			return 0;
		}

		switch (sortColumn) {
		case PRIORITY: {
			PriorityLevel a = PriorityLevel.fromString(task1.getPriority());
			PriorityLevel b = PriorityLevel.fromString(task2.getPriority());
			int result = a.compareTo(b);
			return result * directions[sortColumn];
		}
		case DESCRIPTION: {
			String description1 = task1.getSummary();
			String description2 = task2.getSummary();
			int result = getComparator().compare(description1, description2);
			return result * directions[sortColumn];
		}
		case ELAPSED: {
			long elapsed1 = TasksUiPlugin.getTaskActivityManager().getElapsedTime(task1);
			long elapsed2 = TasksUiPlugin.getTaskActivityManager().getElapsedTime(task2);
			int result = new Long(elapsed1).compareTo(new Long(elapsed2));
			return result * directions[sortColumn];
		}
		case ESTIMATED: {
			int estimate1 = task1.getEstimatedTimeHours();
			int estimate2 = task2.getEstimatedTimeHours();
			int result = estimate1 - estimate2;
			return result * directions[sortColumn];
		}
		case REMINDER: {
			int result = 0;
			if (task1.getScheduledForDate() != null && task2.getScheduledForDate() != null) {
				long reminder1 = task1.getScheduledForDate().getEndDate().getTimeInMillis();
				long reminder2 = task2.getScheduledForDate().getEndDate().getTimeInMillis();
				result = new Long(reminder1).compareTo(new Long(reminder2));
			} else if (task1.getScheduledForDate() != null) {
				result = 1;
			} else if (task2.getScheduledForDate() != null) {
				result = -1;
			}
			return result * directions[sortColumn];
		}
		case LAST_ACTIVE: {
//			long active1 = task1.getStart();
//			long active2 = task2.getStart();
//			int result = new Long(active1).compareTo(new Long(active2));
//			return result * directions[sortColumn];
			return directions[sortColumn];
		}
		}
		return 0;
	}

	@Override
	public int compare(Viewer viewer, Object o1, Object o2) {
		if (o1 instanceof ScheduledTaskContainer) {
			if (o2 instanceof ScheduledTaskContainer) {
				ScheduledTaskContainer dateRangeTaskContainer1 = (ScheduledTaskContainer) o1;
				ScheduledTaskContainer dateRangeTaskContainer2 = (ScheduledTaskContainer) o2;
				return -1 * dateRangeTaskContainer2.getStart().compareTo(dateRangeTaskContainer1.getStart());
			} else {
				return -1;
			}
		} else if (o1 instanceof ITask) {
			if (o2 instanceof ITask) {
				AbstractTask task1 = (AbstractTask) o1;
				AbstractTask task2 = (AbstractTask) o2;
				return compare(task1, task2);
			} else if (o2 instanceof ITaskContainer) {
				return -1;
			}
		}
		return 0;
	}

	public void saveState(IMemento memento) {
		if (memento == null) {
			return;
		}
		IMemento sortingMemento = memento.createChild(activityViewSorterSettings);
		if (sortingMemento != null) {

			for (int i = 0; i < directions.length; i++) {
				sortingMemento.putInteger("direction" + i, directions[i]);
			}
			sortingMemento.putInteger(activitySortColumn, sortColumn);
		}
	}

	public void restoreState(IMemento memento) {
		if (memento == null) {
			return;
		}

		IMemento sortingMemento = memento.getChild(activityViewSorterSettings);
		if (sortingMemento != null) {
			try {
				for (int i = 0; i < directions.length; i++) {
					directions[i] = sortingMemento.getInteger("direction" + i);
				}
				sortColumn = sortingMemento.getInteger(activitySortColumn);
			} catch (NumberFormatException e) {
				resetState();
			}
		}
	}

}
