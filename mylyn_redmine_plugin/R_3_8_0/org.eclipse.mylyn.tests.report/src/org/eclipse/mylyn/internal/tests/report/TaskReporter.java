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

package org.eclipse.mylyn.internal.tests.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylyn.internal.tasks.core.RepositoryModel;
import org.eclipse.mylyn.internal.tasks.core.TaskList;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryManager;
import org.eclipse.mylyn.internal.tests.report.TestCaseResult.TestCaseResultType;
import org.eclipse.mylyn.internal.trac.core.TracRepositoryConnector;
import org.eclipse.mylyn.internal.trac.core.TracTaskDataHandler;
import org.eclipse.mylyn.internal.trac.core.TracTaskMapper;
import org.eclipse.mylyn.internal.trac.core.TracRepositoryConnector.TaskStatus;
import org.eclipse.mylyn.internal.trac.core.client.ITracClient;
import org.eclipse.mylyn.internal.trac.core.client.TracRemoteException;
import org.eclipse.mylyn.internal.trac.core.model.TracSearch;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.RepositoryResponse;
import org.eclipse.mylyn.tasks.core.RepositoryStatus;
import org.eclipse.mylyn.tasks.core.TaskMapping;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataCollector;
import org.eclipse.mylyn.tasks.core.data.TaskOperation;

/**
 * @author Steffen Pingel
 */
class TaskReporter implements TestCaseVisitor {

	private static final TaskOperation OPERATION_REOPEN = new TaskOperation("", "", "", "reopen");

	private static final TaskOperation OPERATION_RESOLVE = new TaskOperation("", "", "", "resolve");

	private static final int MAX_ERROR_TIMEOUT = 3;

	private final TracRepositoryConnector connector;

	private final AbstractTaskDataHandler taskDataHandler;

	private final TaskRepository taskRepository;

	private final Build build;

	private final TaskReporterStatistics statistics;

	private int timeoutErrorCount;

	private final RepositoryModel repositoryModel;

	private final String tag;

	private Map<String, TaskData> tasks;

	private Set<String> processedTasks;

	public TaskReporter(Build build, TaskRepository taskRepository, String tag) {
		this.build = build;
		this.taskRepository = taskRepository;
		this.tag = tag;
		this.connector = new TracRepositoryConnector();
		this.taskDataHandler = connector.getTaskDataHandler();
		this.statistics = new TaskReporterStatistics();
		TaskRepositoryManager repositoryManager = new TaskRepositoryManager();
		repositoryManager.addRepository(taskRepository);
		this.repositoryModel = new RepositoryModel(new TaskList(), repositoryManager);
	}

	private TaskData createTaskData(final TestCase testCase) throws CoreException {
		TaskData taskData = new TaskData(taskDataHandler.getAttributeMapper(taskRepository),
				taskRepository.getConnectorKind(), taskRepository.getRepositoryUrl(), "");
		ITaskMapping initializationData = new TaskMapping() {
			@Override
			public String getSummary() {
				return getTaskSummary(testCase);
			}

			@Override
			public String getDescription() {
				return getTaskDescription(testCase);
			}
		};
		taskDataHandler.initializeTaskData(taskRepository, taskData, null, null);
		connector.getTaskMapping(taskData).merge(initializationData);
		return taskData;
	}

	private TaskData createParentTaskData(final TestCase testCase) throws CoreException {
		TaskData taskData = new TaskData(taskDataHandler.getAttributeMapper(taskRepository),
				taskRepository.getConnectorKind(), taskRepository.getRepositoryUrl(), "");
		ITaskMapping initializationData = new TaskMapping() {
			@Override
			public String getSummary() {
				return getParentTaskSummary(testCase);
			}

			@Override
			public String getDescription() {
				return getParentTaskDescription(testCase);
			}
		};
		taskDataHandler.initializeTaskData(taskRepository, taskData, null, null);
		connector.getTaskMapping(taskData).merge(initializationData);
		return taskData;
	}

//	private String getQueryUrl(TestCase testCase) {
//		TracSearch search = new TracSearch();
//		search.addFilter("summary", getTaskSummary(testCase));
//		search.addFilter("description", getTaskDescription(testCase));
//
//		StringBuilder sb = new StringBuilder();
//		sb.append(taskRepository.getRepositoryUrl());
//		sb.append(ITracClient.QUERY_URL);
//		sb.append(search.toUrl());
//		return sb.toString();
//	}

	private String getQueryUrl(String tag) {
		TracSearch search = new TracSearch();
		if (tag != null) {
			search.addFilter("summary", "^" + tag);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(taskRepository.getRepositoryUrl());
		sb.append(ITracClient.QUERY_URL);
		sb.append(search.toUrl());
		return sb.toString();
	}

	public TaskReporterStatistics getStatistics() {
		return statistics;
	}

	private String getTaskComment(TestCaseResult result) {
		StringBuilder sb = new StringBuilder();
		if (result.getResultType() == TestCaseResultType.FAILURE) {
			sb.append("Failure since build ");
		} else {
			sb.append("Error since build ");
		}
		sb.append(build.getId());
		sb.append(": ");
		if (result.getMessage() != null) {
			sb.append(result.getMessage());
		}
		sb.append("\n\n");
		sb.append(result.getStackTrace());
		return sb.toString();
	}

	private String getTaskDescription(TestCase testCase) {
		return "Test results for " + testCase.getClassName() + "." + testCase.getTestName() + "()";
	}

	private String getParentTaskDescription(TestCase testCase) {
		return "Test results for " + testCase.getClassName();
	}

	private String getTaskSummary(TestCase testCase) {
		StringBuilder sb = new StringBuilder();
		if (tag != null) {
			sb.append(tag);
			sb.append(" ");
		}
//		sb.append(testCase.getShortClassName());
//		sb.append(": ");
		sb.append(testCase.getTestName());
		return sb.toString();
	}

	private String getParentTaskSummary(TestCase testCase) {
		StringBuilder sb = new StringBuilder();
		if (tag != null) {
			sb.append(tag);
			sb.append(" ");
		}
		sb.append(testCase.getShortClassName());
		return sb.toString();
	}

	private void handleError(IStatus status) {
		System.err.println(status);
		if (status.getException() != null) {
			status.getException().printStackTrace();
		}
		System.exit(1);
	}

	private void handleResults(TestCase testCase, TaskData taskData) throws CoreException {
		if (taskData == null) {
			if (testCase.getResult() != null) {
				message(" creating task");
				taskData = createTaskData(testCase);
				// create task to post comment in second step
				RepositoryResponse response = taskDataHandler.postTaskData(taskRepository, taskData, null, null);
				taskData = connector.getTaskData(taskRepository, response.getTaskId(), null);
			} else {
				statistics.tasksUntouched++;
				// test case succeeded and task does not exist
				message(" nothing to do");
				return;
			}
		} else {
			processedTasks.add(taskData.getTaskId());
		}

		// set as sub task
		TaskData parentTaskData = updateParentTask(testCase, taskData, testCase.getResult() != null);

		ITaskMapping taskMapping = connector.getTaskMapping(taskData);
		String status = taskMapping.getStatus();
		boolean modified = false;
		if (testCase.getResult() != null) {
			if (taskData.isPartial()) {
				message(" retrieving full details");
				taskData = connector.getTaskData(taskRepository, taskData.getTaskId(), null);
			}
			if (TaskStatus.CLOSED == TaskStatus.fromStatus(status)) {
				setTaskOperation(taskData, OPERATION_REOPEN);
				modified = true;
			}
			if (matchesLastComment(taskData, testCase.getResult())) {
				statistics.tasksStackTraceUpToDate++;
				message(" stack trace is up to date");
			} else {
				statistics.tasksReopened++;
				message(" adding new stack trace");
				setNewComment(taskData, getTaskComment(testCase.getResult()));
				modified = true;
			}
			// this parent's task should not be closed
			processedTasks.add(parentTaskData.getTaskId());
		} else {
			if (TaskStatus.CLOSED != TaskStatus.fromStatus(status)) {
				statistics.tasksResolved++;
				message(" resolving task");
				setNewComment(taskData, "Fixed in build " + build.getId());
				setTaskOperation(taskData, OPERATION_RESOLVE);
				modified = true;
			}
		}

		if (modified) {
			message(" submitting task");
			taskDataHandler.postTaskData(taskRepository, taskData, null, new NullProgressMonitor());
		} else {
			statistics.tasksUntouched++;
			// test case succeeded and task is closed
			message(" nothing to do, task is already closed");
		}
	}

	private TaskData updateParentTask(TestCase testCase, TaskData taskData, boolean reopen) throws CoreException {
		TaskData parentTaskData = getParentTask(testCase);
		if (parentTaskData == null) {
			message(" creating parent task");
			parentTaskData = createParentTaskData(testCase);
			RepositoryResponse response = taskDataHandler.postTaskData(taskRepository, parentTaskData, null, null);
			parentTaskData = connector.getTaskData(taskRepository, response.getTaskId(), null);
			tasks.put(parentTaskData.getTaskId(), parentTaskData);
		}

		boolean modified = false;
		TaskAttribute attribute = parentTaskData.getRoot().getAttribute(TracTaskDataHandler.ATTRIBUTE_BLOCKED_BY);
		if (attribute != null) {
			String childId = " " + taskData.getTaskId();
			if (!(" " + attribute.getValue() + " ").contains(childId + " ")) {
				message(" updating parent task");
				attribute.setValue(attribute.getValue() + childId);
				modified = true;
			}
		}
		ITaskMapping taskMapping = connector.getTaskMapping(parentTaskData);
		String status = taskMapping.getStatus();
		if (reopen && TaskStatus.CLOSED == TaskStatus.fromStatus(status)) {
			message(" reopening parent task");
			setTaskOperation(parentTaskData, OPERATION_REOPEN);
			modified = true;
		}
		if (modified) {
			// avoid database exceptions when running fast locally
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//			}
			RepositoryResponse response = taskDataHandler.postTaskData(taskRepository, parentTaskData, null, null);
			parentTaskData = connector.getTaskData(taskRepository, response.getTaskId(), null);
			tasks.put(parentTaskData.getTaskId(), parentTaskData);
		}
		return parentTaskData;
	}

	private void setNewComment(TaskData taskData, String comment) {
		TaskAttribute attribute = taskData.getRoot().getMappedAttribute(TaskAttribute.COMMENT_NEW);
		taskData.getAttributeMapper().setValue(attribute, comment);
	}

	private void setTaskOperation(TaskData taskData, TaskOperation operation) {
		TaskAttribute operationAttribute = taskData.getRoot().getMappedAttribute(TaskAttribute.OPERATION);
		taskData.getAttributeMapper().setTaskOperation(operationAttribute, operation);
	}

	private boolean matchesLastComment(TaskData taskData, TestCaseResult result) {
		List<TaskAttribute> comments = taskData.getAttributeMapper().getAttributesByType(taskData,
				TaskAttribute.TYPE_COMMENT);
		if (comments != null && !comments.isEmpty()) {
			TaskAttribute taskAttribute = comments.get(comments.size() - 1);
			TaskCommentMapper lastComment = TaskCommentMapper.createFrom(taskAttribute);
			if (lastComment.getText().endsWith(result.getStackTrace())) {
				return true;
			}
		}
		return false;
	}

	private void message(String string) {
		System.out.println(string);
	}

	public void visit(TestCase testCase) {
		// ITracClient client =
		// connector.getClientManager().getRepository(repository);
		// if (!client.hasAttributes()) {
		// message("Updating repository configuration: " + repository.getUrl());
		// try {
		// client.updateAttributes(new NullProgressMonitor(), false);
		// } catch (TracException e) {
		// handleError(TracCorePlugin.toStatus(e, repository));
		// }
		// }
		//
		message("Processing: " + testCase.getClassName() + "#" + testCase.getTestName());
		try {
			handleResults(testCase, getTask(testCase));
		} catch (CoreException e) {
			handleSubmitException(e);
		}
	}

	private void handleSubmitException(CoreException e) {
		if (e.getStatus().getException() instanceof TracRemoteException) {
			String message = e.getStatus().getException().getMessage();
			if (message != null && message.contains("timeout")) {
				timeoutErrorCount++;
				if (timeoutErrorCount <= MAX_ERROR_TIMEOUT) {
					// ignore a few timeouts
					statistics.ignoredErrors++;
					message(" timeout (" + timeoutErrorCount + "/" + MAX_ERROR_TIMEOUT + ")");
					return;
				}
			}
		}
		handleError(e.getStatus());
	}

	private TaskData getTask(TestCase testCase) {
		for (TaskData task : tasks.values()) {
			TracTaskMapper mapping = connector.getTaskMapping(task);
			if (getTaskSummary(testCase).equals(mapping.getSummary())
					&& getTaskDescription(testCase).equals(mapping.getDescription())) {
				return task;
			}
		}
		return null;
	}

	private TaskData getParentTask(TestCase testCase) {
		for (TaskData task : tasks.values()) {
			TracTaskMapper mapping = connector.getTaskMapping(task);
			if (getParentTaskSummary(testCase).equals(mapping.getSummary())
					&& getParentTaskDescription(testCase).equals(mapping.getDescription())) {
				return task;
			}
		}
		return null;
	}

//	private class SingleResultCollector extends TaskDataCollector {
//		private TaskData taskData;
//
//		@Override
//		public void accept(TaskData taskData) {
//			this.taskData = taskData;
//		}
//
//		public TaskData getTaskData() {
//			return taskData;
//		}
//	}

	private class ResultCollector extends TaskDataCollector {
		private final List<TaskData> results = new ArrayList<TaskData>();

		@Override
		public void accept(TaskData taskData) {
			this.results.add(taskData);
		}

		public List<TaskData> getResults() {
			return results;
		}
	}

	public void initialize() {
		message("Retrieving tasks from " + taskRepository.getRepositoryLabel());
		String queryUrl = getQueryUrl(tag);
		IRepositoryQuery query = repositoryModel.createRepositoryQuery(taskRepository);
		query.setUrl(queryUrl);
		ResultCollector resultCollector = new ResultCollector();
		IStatus status = connector.performQuery(taskRepository, query, resultCollector, null, new NullProgressMonitor());
		if (status.isOK()) {
			this.processedTasks = new HashSet<String>();
			this.tasks = new HashMap<String, TaskData>();
			for (TaskData task : resultCollector.getResults()) {
				this.tasks.put(task.getTaskId(), task);
			}
		} else {
			handleError(status);
		}
	}

	public void done() {
		List<String> deletedTasks = new ArrayList<String>(tasks.keySet());
		deletedTasks.removeAll(processedTasks);
		for (String taskId : deletedTasks) {
			handleDeleted(tasks.get(taskId));
		}
	}

	private void handleDeleted(TaskData taskData) {
		ITaskMapping taskMapping = connector.getTaskMapping(taskData);
		message("Processing task: " + taskMapping.getSummary());
		try {
			processedTasks.add(taskData.getTaskId());

			String status = taskMapping.getStatus();
			if (TaskStatus.CLOSED == TaskStatus.fromStatus(status)) {
				statistics.tasksUntouched++;
				// test case succeeded and task is closed
				message(" nothing to do, task is alread closed");
				return;
			} else {
				statistics.tasksDeleted++;
				message(" resolving task");
				setNewComment(taskData, "Fixed or removed in build " + build.getId());
				setTaskOperation(taskData, OPERATION_RESOLVE);
			}

			message(" submitting task");
			taskDataHandler.postTaskData(taskRepository, taskData, null, new NullProgressMonitor());
		} catch (CoreException e) {
			handleSubmitException(e);
		}
	}

	public void clearAll() {
		message("Deleting all tasks from " + taskRepository.getRepositoryLabel());
		String queryUrl = getQueryUrl(null);
		IRepositoryQuery query = repositoryModel.createRepositoryQuery(taskRepository);
		query.setUrl(queryUrl);
		ResultCollector resultCollector = new ResultCollector();
		IStatus status = connector.performQuery(taskRepository, query, resultCollector, null, new NullProgressMonitor());
		if (status.isOK()) {
			ITracClient client = connector.getClientManager().getTracClient(taskRepository);
			for (TaskData task : resultCollector.getResults()) {
				try {
					client.deleteTicket(Integer.parseInt(task.getTaskId()), null);
				} catch (Exception e) {
					handleError(new RepositoryStatus(taskRepository, IStatus.ERROR, "", 0, "Error deleting task", e));
				}
			}
		} else {
			handleError(status);
		}
	}

}
