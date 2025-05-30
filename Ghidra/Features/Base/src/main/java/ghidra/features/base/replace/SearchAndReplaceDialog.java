/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.features.base.replace;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import docking.DialogComponentProvider;
import docking.widgets.combobox.GhidraComboBox;
import ghidra.app.util.HelpTopics;
import ghidra.framework.plugintool.PluginTool;
import ghidra.util.HelpLocation;
import ghidra.util.layout.*;

/**
 * Dialog for entering information to perform a search and replace operation.
 */
public class SearchAndReplaceDialog extends DialogComponentProvider {

	private static final int MAX_HISTORY = 20;
	private List<SearchType> allTypes;
	private List<JCheckBox> typeCheckboxes = new ArrayList<>();
	private SearchAndReplaceQuery query;
	private Set<SearchType> selectedTypes = new HashSet<>();
	private GhidraComboBox<String> searchTextComboBox;
	private GhidraComboBox<String> replaceTextComboBox;
	private JCheckBox regexCheckbox;
	private JCheckBox caseSensitiveCheckbox;
	private JCheckBox wholeWordCheckbox;
	private int searchLimit;

	/**
	 * Constructor
	 * @param searchLimit the maximum number of search matches to find before stopping.
	 */
	public SearchAndReplaceDialog(int searchLimit) {
		super("Search And Replace");
		this.searchLimit = searchLimit;
		this.allTypes = new ArrayList<>(SearchType.getSearchTypes());
		Collections.sort(allTypes);
		addWorkPanel(buildMainPanel());
		addOKButton();
		addCancelButton();
		updateDialogStatus();
		setHelpLocation(new HelpLocation(HelpTopics.SEARCH, "Search And Replace"));
	}

	/**
	 * Sets a new maximum number of search matches to find before stopping.
	 * @param searchLimit the new maximum number of search matches to find before stopping.
	 */
	public void setSearchLimit(int searchLimit) {
		this.searchLimit = searchLimit;
	}

	/**
	 * Convenience method for initializing the dialog, showing it and returning the query.
	 * @param tool the tool this dialog belongs to
	 * @return the SearchAndReplaceQuery generated by the information in the dialog when the 
	 * "Ok" button is pressed, or null if the dialog was cancelled.
	 */
	public SearchAndReplaceQuery show(PluginTool tool) {
		query = null;
		searchTextComboBox.getTextField().selectAll();
		replaceTextComboBox.setText("");
		updateDialogStatus();
		tool.showDialog(this);
		return query;
	}

	/**
	 * Returns the query generated by the dialog when the "Ok" button is pressed or null if the
	 * dialog was cancelled.
	 * @return the SearchAndReplaceQuery generated by the information in the dialog when the 
	 * "Ok" button is pressed, or null if the dialog was cancelled.	 
	 */
	public SearchAndReplaceQuery getQuery() {
		return query;
	}

	private JComponent buildMainPanel() {
		JPanel panel = new JPanel(new VerticalLayout(20));
		panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		panel.add(buildPatternsPanel());
		panel.add(buildOptionsPanel());
		panel.add(buildTypesPanel());
		return panel;
	}

	private JComponent buildTypesPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Search For:"));
		panel.add(buildInnerTypesPanel(), BorderLayout.CENTER);
		panel.add(buildSelectAllPanel(), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel buildInnerTypesPanel() {
		JPanel panel = new JPanel(new GridLayout(0, 3, 10, 5));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		for (SearchType type : allTypes) {
			JCheckBox cb = new JCheckBox(type.getName());
			typeCheckboxes.add(cb);
			cb.setToolTipText(type.getDescription());
			cb.addActionListener(e -> typeCheckBoxChanged(cb, type));
			panel.add(cb);
		}
		return panel;
	}

	private Component buildSelectAllPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
		JButton selectAllButton = new JButton("Select All");
		JButton deselectAllButton = new JButton("Deselect All");
		selectAllButton.addActionListener(e -> selectAllTypes());
		deselectAllButton.addActionListener(e -> deselectAllTypes());
		panel.add(selectAllButton);
		panel.add(deselectAllButton);
		return panel;
	}

	private void selectAllTypes() {
		for (JCheckBox checkBox : typeCheckboxes) {
			checkBox.setSelected(true);
		}
		selectedTypes.addAll(allTypes);
		updateDialogStatus();
	}

	private void deselectAllTypes() {
		for (JCheckBox checkBox : typeCheckboxes) {
			checkBox.setSelected(false);
		}
		selectedTypes.clear();
		updateDialogStatus();
	}

	private void typeCheckBoxChanged(JCheckBox cb, SearchType type) {
		if (cb.isSelected()) {
			selectedTypes.add(type);
		}
		else {
			selectedTypes.remove(type);
		}
		updateDialogStatus();
	}

	private void updateDialogStatus() {
		boolean isValid = hasValidInformation();
		setOkEnabled(isValid);
	}

	private boolean hasValidInformation() {
		clearStatusText();
		String searchText = searchTextComboBox.getText().trim();
		if (searchText.isBlank()) {
			setStatusText("Please enter search text");
			return false;
		}
		if (selectedTypes.isEmpty()) {
			setStatusText("Please select at least one \"search for\" item to search!");
			return false;
		}
		return createQuery() != null;
	}

	private JComponent buildOptionsPanel() {
		regexCheckbox = new JCheckBox("Regular expression");
		caseSensitiveCheckbox = new JCheckBox("Case sensitive");
		wholeWordCheckbox = new JCheckBox("Whole word");

		regexCheckbox.addActionListener(e -> regExCheckboxChanged());

		regexCheckbox.setToolTipText("Interprets search and replace text as regular expressions");
		caseSensitiveCheckbox.setToolTipText("Determines if search text is case sensitive");
		wholeWordCheckbox.setToolTipText("Sets if the input pattern must match whole words. For" +
			" names, this means the entire name. For comments, it means matching entire words.");

		JPanel panel = new JPanel(new HorizontalLayout(10));
		Border titleBorder = BorderFactory.createTitledBorder("Options:");
		Border innerBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
		panel.setBorder(BorderFactory.createCompoundBorder(titleBorder, innerBorder));
		panel.add(regexCheckbox);
		panel.add(caseSensitiveCheckbox);
		panel.add(wholeWordCheckbox);
		return panel;
	}

	private void regExCheckboxChanged() {
		wholeWordCheckbox.setEnabled(!regexCheckbox.isSelected());
		updateDialogStatus();
	}

	private Component buildPatternsPanel() {
		searchTextComboBox = new GhidraComboBox<>();
		searchTextComboBox.setEditable(true);
		searchTextComboBox.setToolTipText("Enter the text to search for");
		searchTextComboBox.getTextField()
				.getDocument()
				.addDocumentListener(new DocumentListener() {

					@Override
					public void removeUpdate(DocumentEvent e) {
						updateDialogStatus();
					}

					@Override
					public void insertUpdate(DocumentEvent e) {
						updateDialogStatus();
					}

					@Override
					public void changedUpdate(DocumentEvent e) {
						updateDialogStatus();
					}
				});

		replaceTextComboBox = new GhidraComboBox<>();
		replaceTextComboBox.setEditable(true);
		replaceTextComboBox.setToolTipText("Enter the text to replace matched search text");

		searchTextComboBox.addActionListener(e -> pressOk());
		replaceTextComboBox.addActionListener(e -> pressOk());

		JPanel panel = new JPanel(new PairLayout(10, 10));
		panel.add(new JLabel("Find:", SwingConstants.RIGHT));
		panel.add(searchTextComboBox);
		panel.add(new JLabel("Replace with:", SwingConstants.RIGHT));
		panel.add(replaceTextComboBox);

		return panel;
	}

	private void pressOk() {
		if (isOKEnabled()) {
			okCallback();
		}
	}

	@Override
	protected void okCallback() {
		query = createQuery();
		updateHistory(searchTextComboBox);
		updateHistory(replaceTextComboBox);
		closeDialog();
	}

	private SearchAndReplaceQuery createQuery() {
		String searchText = searchTextComboBox.getText().trim();
		String replacePattern = replaceTextComboBox.getText().trim();
		boolean isRegEx = regexCheckbox.isSelected();
		boolean isCaseSensitive = caseSensitiveCheckbox.isSelected();
		boolean isWholeWord = wholeWordCheckbox.isSelected();
		Set<SearchType> searchTypes = getSelectedSearchTypes();
		try {
			return new SearchAndReplaceQuery(searchText, replacePattern, searchTypes, isRegEx,
				isCaseSensitive, isWholeWord, searchLimit);
		}
		catch (PatternSyntaxException e) {
			return null;
		}
	}

	private Set<SearchType> getSelectedSearchTypes() {
		Set<SearchType> set = new HashSet<>();
		for (SearchType type : allTypes) {
			if (selectedTypes.contains(type)) {
				set.add(type);
			}
		}
		return set;
	}

	private void updateHistory(GhidraComboBox<String> combo) {
		String value = combo.getText();
		if (value.isBlank()) {
			return;
		}
		DefaultComboBoxModel<String> model =
			(DefaultComboBoxModel<String>) combo.getModel();
		model.removeElement(value);
		if (model.getSize() > MAX_HISTORY) {
			model.removeElementAt(model.getSize() - 1);
		}
		model.insertElementAt(value, 0);
		model.setSelectedItem(value);
	}

	/**
	 * Sets the search and replace text fields with given values.
	 * @param searchText the text to be put in the search field
	 * @param replaceText the text to be put in the replace field
	 */
	public void setSarchAndReplaceText(String searchText, String replaceText) {
		searchTextComboBox.setText(searchText);
		replaceTextComboBox.setText(replaceText);
	}

	/**
	 * Sets the search type with the given name to be selected.
	 * @param searchType the name of the search type to select
	 */
	public void selectSearchType(String searchType) {
		for (JCheckBox checkBox : typeCheckboxes) {
			if (checkBox.getText().equals(searchType)) {
				checkBox.setSelected(true);
				break;
			}
		}
		for (SearchType type : allTypes) {
			if (type.getName().equals(searchType)) {
				selectedTypes.add(type);
				break;
			}
		}
		updateDialogStatus();
	}

	/**
	 * Returns true if the "ok" button is enabled.
	 * @return true if the "ok" button is enabled.
	 */
	public boolean isOkEnabled() {
		return super.isOKEnabled();
	}

	/**
	 * Selects the RegEx checkbox in the dialog.
	 * @param b true to select RegEx, false to turn deselect it
	 */
	public void selectRegEx(boolean b) {
		regexCheckbox.setSelected(b);
		updateDialogStatus();
	}

}
