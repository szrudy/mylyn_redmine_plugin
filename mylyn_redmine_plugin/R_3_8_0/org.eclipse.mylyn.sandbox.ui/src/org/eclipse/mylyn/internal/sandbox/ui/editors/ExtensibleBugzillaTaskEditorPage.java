/*******************************************************************************
 * Copyright (c) 2004, 2009 Jingwen Ou and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jingwen Ou - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.sandbox.ui.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.mylyn.commons.ui.CommonImages;
import org.eclipse.mylyn.commons.workbench.editors.CommonTextSupport;
import org.eclipse.mylyn.commons.workbench.forms.CommonFormUtil;
import org.eclipse.mylyn.internal.bugzilla.ui.editor.BugzillaTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;

/**
 * A bugzilla task editor page that has wiki facilities.
 * 
 * @author Jingwen Ou
 */
public class ExtensibleBugzillaTaskEditorPage extends BugzillaTaskEditorPage {

	private Action toggleFindAction;

	private static final Color HIGHLIGHTER_YELLOW = new Color(Display.getDefault(), 255, 238, 99);

	private static final StyleRange HIGHLIGHT_STYLE_RANGE = new StyleRange(0, 0, null, HIGHLIGHTER_YELLOW);

	public ExtensibleBugzillaTaskEditorPage(TaskEditor editor) {
		super(editor);
	}

	private void addFindAction(IToolBarManager toolBarManager) {
		if (toggleFindAction != null && toggleFindAction.isChecked()) {
			ControlContribution findTextboxControl = new ControlContribution("Find") {
				@Override
				protected Control createControl(Composite parent) {
					FormToolkit toolkit = getTaskEditor().getHeaderForm().getToolkit();
					Composite findComposite = toolkit.createComposite(parent);
					findComposite.setLayout(new RowLayout());
					findComposite.setBackground(null);

					final Text findText = toolkit.createText(findComposite, "", SWT.FLAT);
					findText.setLayoutData(new RowData(100, SWT.DEFAULT));
					findText.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TEXT_BORDER);
					findText.setFocus();
					toolkit.adapt(findText, false, false);
					findText.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetDefaultSelected(SelectionEvent event) {
							try {
								setReflow(false);
								findAndHighlight(ExtensibleBugzillaTaskEditorPage.this, findText.getText());
								// always toggle attachment part close after every search, since all ExpandableComposites are open
								AbstractTaskEditorPart attachmentsPart = getPart(AbstractTaskEditorPage.ID_PART_ATTACHMENTS);
								CommonFormUtil.setExpanded((ExpandableComposite) attachmentsPart.getControl(), false);
							} finally {
								setReflow(true);
							}
							reflow();
						}
					});
					toolkit.paintBordersFor(findComposite);
					return findComposite;
				}

			};
			toolBarManager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, findTextboxControl);
		}

		if (toggleFindAction == null) {
			toggleFindAction = new Action("", SWT.TOGGLE) {
				@Override
				public void run() {
					getTaskEditor().updateHeaderToolBar();
				}

			};
			toggleFindAction.setImageDescriptor(CommonImages.FIND);
			toggleFindAction.setToolTipText("Find");
			//getManagedForm().getForm().setData(TaskEditorFindHandler.KEY_FIND_ACTION, toggleFindAction);
		}

		toolBarManager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, toggleFindAction);
	}

	@Override
	public boolean canPerformAction(String actionId) {
		if (actionId.equals(ActionFactory.FIND.getId())) {
			return true;
		}

		return super.canPerformAction(actionId);
	}

	@Override
	protected Set<TaskEditorPartDescriptor> createPartDescriptors() {
		Set<TaskEditorPartDescriptor> descriptors = super.createPartDescriptors();
		boolean hasPartNewComment = false;
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_NEW_COMMENT)) {
				descriptors.remove(taskEditorPartDescriptor);
				hasPartNewComment = true;
				break;
			}
		}
		if (hasPartNewComment) {
			descriptors.add(new TaskEditorPartDescriptor(ID_PART_NEW_COMMENT) {
				@Override
				public AbstractTaskEditorPart createPart() {
					return new ExtensibleTaskEditorNewCommentPart();
				}
			}.setPath(PATH_COMMENTS));
		}
		return descriptors;
	}

	@Override
	public void doAction(String actionId) {
		if (actionId.equals(ActionFactory.FIND.getId())) {
			if (toggleFindAction != null) {
				toggleFindAction.setChecked(!toggleFindAction.isChecked());
				toggleFindAction.run();
			}
		}
		super.doAction(actionId);
	}

	@Override
	public void fillToolBar(IToolBarManager toolBarManager) {
		super.fillToolBar(toolBarManager);

		addFindAction(toolBarManager);
	}

	private static void findTextViewerControl(Composite composite, List<TextViewer> found) {
		if (!composite.isDisposed()) {
			for (Control child : composite.getChildren()) {
				TextViewer viewer = CommonTextSupport.getTextViewer(child);
				if (viewer != null && viewer.getDocument().get().length() > 0) {
					found.add(viewer);
				}

				// have to do this since TaskEditorCommentPart.expendComment(..) will dispose the TextViewer when the ExpandableComposite is close
				if (child instanceof ExpandableComposite) {
					CommonFormUtil.setExpanded((ExpandableComposite) child, true);
				}

				if (child instanceof Composite) {
					findTextViewerControl((Composite) child, found);
				}
			}
		}
	}

	private static boolean findAndHighlightTextViewer(TextViewer viewer, FindReplaceDocumentAdapter adapter,
			String findString, int startOffset) throws BadLocationException {
		IRegion matchRegion = adapter.find(startOffset, findString, true, false, false, false);

		if (matchRegion != null) {
			int widgetOffset = matchRegion.getOffset();
			int length = matchRegion.getLength();
			HIGHLIGHT_STYLE_RANGE.start = widgetOffset;
			HIGHLIGHT_STYLE_RANGE.length = length;
			viewer.getTextWidget().setStyleRange(HIGHLIGHT_STYLE_RANGE);

			findAndHighlightTextViewer(viewer, adapter, findString, widgetOffset + length);

			return true;
		}

		return false;
	}

	public static void findAndHighlight(IFormPage page, String findString) {
		IManagedForm form = page.getManagedForm();
		if (form == null) {
			return;
		}
		ScrolledForm scrolledForm = form.getForm();
		if (scrolledForm == null) {
			return;
		}

		List<TextViewer> found = new ArrayList<TextViewer>();
		findTextViewerControl(scrolledForm.getBody(), found);

		for (TextViewer viewer : found) {
			try {
				// Wiping previous highlighted element
				viewer.setRedraw(false);
				viewer.refresh();
				viewer.setRedraw(true);

				FindReplaceDocumentAdapter adapter = new FindReplaceDocumentAdapter(viewer.getDocument());

				if (!findAndHighlightTextViewer(viewer, adapter, findString, -1)) {
					// toggle close if can't match the keyword
					Composite comp = viewer.getControl().getParent();
					while (comp != null) {
						if (comp instanceof ExpandableComposite) {
							ExpandableComposite ex = (ExpandableComposite) comp;
							CommonFormUtil.setExpanded(ex, false);
							break;
						}
						comp = comp.getParent();
					}
				}
			} catch (BadLocationException e) {
				//ignore
			}
		}
	}

}
