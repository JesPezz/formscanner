package com.albertoborsetta.formscanner.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import com.albertoborsetta.formscanner.api.FormGroup;
import com.albertoborsetta.formscanner.api.FormPoint;
import com.albertoborsetta.formscanner.api.FormQuestion;
import com.albertoborsetta.formscanner.api.FormTemplate;
import com.albertoborsetta.formscanner.api.commons.Constants.ShapeType;
import com.albertoborsetta.formscanner.commons.FormScannerConstants;
import com.albertoborsetta.formscanner.commons.translation.FormScannerTranslation;
import com.albertoborsetta.formscanner.commons.translation.FormScannerTranslationKeys;
import com.albertoborsetta.formscanner.model.FormScannerModel;

public class ReviewResultsFrame extends InternalFrame {

	private static final long serialVersionUID = 1L;

	private final JComboBox<String> studentSelector;
	private final JLabel imageLabel;
	private final DefaultTableModel tableModel;
	private final JTable table;
	private List<String> headerKeys;

	public ReviewResultsFrame(FormScannerModel viewModel) {
		super(viewModel);
		setName(FormScannerConstants.Frame.REVIEW_RESULTS_FRAME.name());
		setTitle(FormScannerTranslation.getTranslationFor(FormScannerTranslationKeys.REVIEW_RESULTS));
		setBounds(model.getLastPosition(FormScannerConstants.Frame.REVIEW_RESULTS_FRAME));
		setClosable(true);
		setIconifiable(true);
		setResizable(true);
		setMaximizable(true);
		setComponentOrientation(model.getOrientation());

		studentSelector = new JComboBox<>();
		imageLabel = new JLabel();
		tableModel = new DefaultTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return column > 0;
			}

			@Override
			public void setValueAt(Object value, int row, int column) {
				super.setValueAt(value, row, column);
				if (column > 0) {
					applyCorrection(row, column, String.valueOf(value));
				}
			}
		};
		table = new JTable(tableModel);
		table.setDefaultRenderer(Object.class, new ResultsGridFrame.MultilineTableCellRenderer(model));
		table.setComponentOrientation(model.getOrientation());
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		table.getTableHeader().setReorderingAllowed(false);

		buildHeader();
		updateReviewResults();

		studentSelector.addActionListener(e -> updatePreview());

		JScrollPane scroll = new JScrollPane(table);
		getContentPane().add(studentSelector, BorderLayout.NORTH);
		getContentPane().add(scroll, BorderLayout.CENTER);
		getContentPane().add(imageLabel, BorderLayout.SOUTH);
	}

	public void updateReviewResults() {
		List<String> students = new ArrayList<>(model.getFilledForms().keySet());
		Collections.sort(students);

		studentSelector.removeAllItems();
		for (String student : students) {
			studentSelector.addItem(student);
		}

		Object[] columns = new Object[1 + headerKeys.size()];
		String studentHeader = FormScannerTranslation.getTranslationFor(FormScannerTranslationKeys.FIRST_CSV_COLUMN);
		columns[0] = (studentHeader.isEmpty()) ? "File" : studentHeader;
		for (int i = 0; i < headerKeys.size(); i++) {
			columns[i + 1] = headerKeys.get(i);
		}

		Object[][] rows = new Object[students.size()][columns.length];
		for (int r = 0; r < students.size(); r++) {
			rows[r][0] = students.get(r);
			Map<String, FormQuestion> index = indexQuestions(model.getFilledForms().get(students.get(r)));
			for (int c = 0; c < headerKeys.size(); c++) {
				FormQuestion question = index.get(headerKeys.get(c));
				rows[r][c + 1] = (question == null) ? "" : question.getValues();
			}
		}

		tableModel.setDataVector(rows, columns);
		updatePreview();
	}

	private void buildHeader() {
		headerKeys = new ArrayList<>();
		for (FormTemplate form : model.getFilledForms().values()) {
			if (form != null) {
				headerKeys = new ArrayList<>(indexQuestions(form).keySet());
				Collections.sort(headerKeys);
				break;
			}
		}
	}

	private Map<String, FormQuestion> indexQuestions(FormTemplate form) {
		Map<String, FormQuestion> index = new HashMap<>();
		if (form == null) {
			return index;
		}
		Map<String, FormGroup> groups = form.getGroups();
		List<String> groupKeys = new ArrayList<>(groups.keySet());
		Collections.sort(groupKeys);
		for (String groupKey : groupKeys) {
			FormGroup group = groups.get(groupKey);
			List<String> fieldKeys = new ArrayList<>(group.getFields().keySet());
			Collections.sort(fieldKeys);
			for (String fieldKey : fieldKeys) {
				String fullKey = groupKey.equals(FormScannerConstants.EMPTY_GROUP_NAME) ? fieldKey : groupKey + "."
						+ fieldKey;
				index.put(fullKey, group.getFields().get(fieldKey));
			}
		}
		return index;
	}

	private void applyCorrection(int row, int column, String newValue) {
		Object student = tableModel.getValueAt(row, 0);
		if (student == null) {
			return;
		}
		FormTemplate form = model.getFilledForms().get(student.toString());
		if (form == null || column > headerKeys.size()) {
			return;
		}
		FormQuestion question = indexQuestions(form).get(headerKeys.get(column - 1));
		if (question == null) {
			return;
		}
		Map<String, FormPoint> points = new HashMap<>(question.getPoints());
		question.clearPoints();
		if (newValue != null) {
			for (String token : newValue.split("\\|")) {
				String trimmed = token.trim();
				if (points.containsKey(trimmed)) {
					question.setPoint(trimmed, points.get(trimmed));
				}
			}
		}
	}

	private void updatePreview() {
		Object student = studentSelector.getSelectedItem();
		imageLabel.setIcon(null);
		imageLabel.setText("");
		if (student == null) {
			return;
		}
		BufferedImage image = model.getImage(String.valueOf(student));
		if (image == null) {
			imageLabel.setText(String.valueOf(student));
			return;
		}
		double zoom = 200d / image.getHeight();
		int targetWidth = Math.max(1, (int) (image.getWidth() * zoom));
		int targetHeight = 200;
		BufferedImage preview = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = preview.createGraphics();
		g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		showResponsePoints(g, String.valueOf(student), zoom);
		g.dispose();
		imageLabel.setIcon(new ImageIcon(preview));
	}

	private void showResponsePoints(Graphics2D g, String student, double zoom) {
		int marker = (int) (model.getShapeSize() * zoom);
		for (FormQuestion question : indexQuestions(formForStudent(student)).values()) {
			for (FormPoint point : question.getPoints().values()) {
				if (point != null) {
					int x = (int) (point.getX() * zoom);
					int y = (int) (point.getY() * zoom);
					g.setColor(Color.RED);
					if (model.getShapeType().equals(ShapeType.CIRCLE)) {
						g.fillArc(x - marker, y - marker, 2 * marker, 2 * marker, 0, 360);
					} else {
						g.fillRect(x - marker, y - marker, 2 * marker, 2 * marker);
					}
					g.setColor(Color.BLACK);
				}
			}
		}
	}

	private FormTemplate formForStudent(String student) {
		return model.getFilledForms().get(student);
	}
}