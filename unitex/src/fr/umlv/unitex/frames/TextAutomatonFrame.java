/*
 * Unitex
 *
 * Copyright (C) 2001-2021 Université Paris-Est Marne-la-Vallée <unitex@univ-mlv.fr>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA.
 *
 */
package fr.umlv.unitex.frames;

import fr.umlv.unitex.DropTargetManager;
import fr.umlv.unitex.common.project.manager.GlobalProjectManager;
import fr.umlv.unitex.config.Config;
import fr.umlv.unitex.config.ConfigManager;
import fr.umlv.unitex.config.PreferencesListener;
import fr.umlv.unitex.config.PreferencesManager;
import fr.umlv.unitex.console.Console;
import fr.umlv.unitex.files.FileUtil;
import fr.umlv.unitex.files.PersonalFileFilter;
import fr.umlv.unitex.graphrendering.GenericGraphBox;
import fr.umlv.unitex.graphrendering.TfstGraphBox;
import fr.umlv.unitex.graphrendering.TfstGraphicalZone;
import fr.umlv.unitex.graphrendering.TfstTextField;
import fr.umlv.unitex.io.Encoding;
import fr.umlv.unitex.io.GraphIO;
import fr.umlv.unitex.io.UnicodeIO;
import fr.umlv.unitex.listeners.GraphListener;
import fr.umlv.unitex.process.EatStreamThread;
import fr.umlv.unitex.process.Launcher;
import fr.umlv.unitex.process.Log;
import fr.umlv.unitex.process.ToDo;
import fr.umlv.unitex.process.commands.*;
import fr.umlv.unitex.tfst.Bounds;
import fr.umlv.unitex.tfst.TagFilter;
import fr.umlv.unitex.tfst.TfstTableModel;
import fr.umlv.unitex.tfst.TokensInfo;
import fr.umlv.unitex.utils.KeyUtil;
import fr.umlv.unitex.io.TfstGraphIO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class describes a frame used to display sentence automata.
 *
 * @author Sébastien Paumier
 */
public class TextAutomatonFrame extends TfstFrame {
	final TagFilter filter = new TagFilter();
	final TfstTableModel tfstTableModel = new TfstTableModel(filter, true);
	final JTextArea sentenceTextArea = new JTextArea();
	final JLabel sentence_count_label = new JLabel(" 0 sentence");
	boolean elagON;
	JSpinner spinner;
	SpinnerNumberModel spinnerModel;
	TfstGraphicalZone elaggraph;
	File elagrules;
	JLabel ruleslabel;
	JScrollBar tfstScrollbar;
	TfstGraphicalZone graphicalZone;
	public JScrollPane scrollPane;
	private final GraphListener listener = new GraphListener() {
		@Override
		public void graphChanged(boolean m) {
			if (m)
				setModified(true);
			repaint();
		}
	};

	public TfstGraphicalZone getGraphicalZone() {
		return graphicalZone;
	}

	private final TfstTextField textfield = new TfstTextField(25, this);
	boolean modified = false;
	int sentence_count = 0;
	File sentence_text;
	File sentence_grf;
	File sentence_tok;
	File sentence_modified;
	File text_tfst;
	File elag_tfst;
	File elagsentence_grf;
	boolean isAcurrentLoadingThread = false;
	boolean isAcurrentElagLoadingThread = false;
	Process currentElagLoadingProcess = null;
	JSplitPane superpanel;
	JButton revertSentenceGraph;
	private int currentSentenceNumber = 0;
	private ArrayList<String> checkList = new ArrayList<>();
	private JButton buildTokensButton;
	private JButton undoButton;
	private JButton redoButton;
	private UndoManager manager = new UndoManager();

	TextAutomatonFrame() {		
		super("FST-Text", true, true, true, true);
		DropTargetManager.getDropTarget().newDropTarget(this);
		setContentPane(constructPanel());
		pack();
		setBounds(10, 10, 900, 600);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addInternalFrameListener(new InternalFrameAdapter() {
			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
				try {
					setIcon(true);
				} catch (final java.beans.PropertyVetoException e2) {
					e2.printStackTrace();
				}
			}
		});
		textfield.setEditable(false);
		closeElagFrame();
		PreferencesManager.addPreferencesListener(new PreferencesListener() {
			@Override
			public void preferencesChanged(String language) {
				sentenceTextArea.setFont(ConfigManager.getManager()
						.getTextFont(null));
			}
		});
    getManager().setLimit(-1);
    graphicalZone.addUndoableEditListener(getManager());
	}

	private JPanel constructPanel() {
		final JPanel panel = new JPanel(new BorderLayout());
		panel.add(constructUpPanel(), BorderLayout.NORTH);
		superpanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				constructTextPanel(), constructElagPanel());
		superpanel.setBorder(new EmptyBorder(0,0,5,0));
		superpanel.setOneTouchExpandable(true);
		superpanel.setResizeWeight(0.5);
		superpanel.setDividerLocation(10000);
		panel.add(superpanel, BorderLayout.CENTER);
		KeyUtil.addMinimizeFrameListener(panel);
		return panel;
	}

	private JPanel constructTextPanel() {
		final JPanel textframe = new JPanel(new BorderLayout());
		final JPanel p = new JPanel(new GridLayout(4, 1));
		JButton button = new JButton("Explode");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				explodeTextAutomaton(text_tfst);
			}
		});
		p.add(button);
		button = new JButton("Implode");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				implodeTextAutomaton(text_tfst);
			}
		});
		p.add(button);
		button = new JButton("Apply Elag Rule");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				elagDialog();
			}
		});
		p.add(button);
		button = new JButton("Find and Replace");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//graphicalZone.setHighlight(graphicalZone.getBoxes().get(10), true);
				//FindAndReplace.find(graphicalZone, graphicalZone.getBoxes().get(0), "fdp", true, true, true, true);
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class).newTextAutomatonFindAndReplaceDialog();
			}
		});
		p.add(button);
		textframe.add(p, BorderLayout.WEST);
		final JPanel downPanel = new JPanel(new BorderLayout());
		graphicalZone = new TfstGraphicalZone(null, textfield, this, true);
		graphicalZone.addGraphListener(listener);
		graphicalZone.setPreferredSize(new Dimension(1188, 840));
		scrollPane = new JScrollPane(graphicalZone);
		tfstScrollbar = scrollPane.getHorizontalScrollBar();
		tfstScrollbar.setUnitIncrement(20);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);
		scrollPane.setPreferredSize(new Dimension(1188, 840));
		textfield.setFont(ConfigManager.getManager().getInputFont(null));
		downPanel.add(textfield, BorderLayout.NORTH);
		downPanel.add(scrollPane, BorderLayout.CENTER);
		final JTabbedPane tabbed = new JTabbedPane();
		downPanel.add(scrollPane, BorderLayout.CENTER);
		tabbed.addTab("Automaton", downPanel);
		tabbed.addTab("Table", createTablePanel());
		textframe.add(tabbed, BorderLayout.CENTER);
		return textframe;
	}

	private JPanel createTablePanel() {
		final JPanel p = new JPanel(new BorderLayout());
		final JTable table = new JTable(tfstTableModel);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setFont(ConfigManager.getManager().getTextFont(null));
		PreferencesManager.addPreferencesListener(new PreferencesListener() {
			@Override
			public void preferencesChanged(String language) {
				table.setFont(ConfigManager.getManager().getTextFont(null));
				refreshTableColumnWidths(table);
				refreshTableRowHeight(table);
			}
		});
		table.getColumnModel().addColumnModelListener(
				new TableColumnModelListener() {
					@Override
					public void columnSelectionChanged(ListSelectionEvent e) {
						/* nop */
					}

					@Override
					public void columnRemoved(TableColumnModelEvent e) {
						refreshTableColumnWidths(table);
					}

					@Override
					public void columnMoved(TableColumnModelEvent e) {
						/* nop */
					}

					@Override
					public void columnMarginChanged(ChangeEvent e) {
						/* nop */
					}

					@Override
					public void columnAdded(TableColumnModelEvent e) {
						refreshTableColumnWidths(table);
					}
				});
		refreshTableColumnWidths(table);
		refreshTableRowHeight(table);
		p.add(new JScrollPane(table));
		final JPanel filterPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gbc = new GridBagConstraints();
		filterPanel.setBorder(BorderFactory
				.createTitledBorder("Filter grammatical/semantic codes"));
		final JCheckBox alwaysShowGramCode = new JCheckBox(
				"Always show POS category, regardless filtering");
		final JRadioButton all = new JRadioButton("All", true);
		final JRadioButton onlyShowGramCode = new JRadioButton(
				"Only POS category");
		final JRadioButton usePattern = new JRadioButton("Use filter: ");
		final JButton export = new JButton("Export all text as POS list");
		final JCheckBox delafStyle = new JCheckBox("Export DELAF style", false);
		export.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				exportTextAsTable(delafStyle.isSelected());
			}
		});
		final JButton tagFilterButton = new JButton("Filter Tags");
		tagFilterButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class).newTextAutomatonTagFilterDialog();
			}
		});
		final ButtonGroup group = new ButtonGroup();
		group.add(all);
		group.add(onlyShowGramCode);
		group.add(usePattern);
		final JFormattedTextField filterField = new JFormattedTextField(
				new JFormattedTextField.AbstractFormatter() {
					@Override
					public String valueToString(Object value)
							throws ParseException {
						try {
							final Pattern p2 = (Pattern) value;
							if (p2 == null)
								return null;
							final String s = p2.toString();
							return s.substring(2, s.length() - 2);
						} catch (final ClassCastException e) {
							throw new ParseException("", 0);
						}
					}

					@Override
					public Object stringToValue(String text)
							throws ParseException {
						try {
							if (text.equals(""))
								return null;
							return Pattern.compile(".*" + text + ".*");
						} catch (final PatternSyntaxException e) {
							throw new ParseException("", 0);
						}
					}
				});
		final ActionListener l = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final boolean onlyGramCode = onlyShowGramCode.isSelected();
				final boolean alwaysGramCode = alwaysShowGramCode.isSelected();
				final boolean showAll = all.isSelected();
				final Pattern pattern = (Pattern) ((showAll || onlyGramCode || !usePattern
						.isSelected()) ? null : filterField.getValue());
				filter.setFilter(pattern, alwaysGramCode, onlyGramCode);
			}
		};
		filterField.addCaretListener(new CaretListener() {
			@Override
			public void caretUpdate(CaretEvent e) {
				try {
					filterField.commitEdit();
					filterField.setForeground(Color.BLACK);
					l.actionPerformed(null);
				} catch (final ParseException e2) {
					filterField.setForeground(Color.RED);
				}
			}
		});
		alwaysShowGramCode.addActionListener(l);
		all.addActionListener(l);
		onlyShowGramCode.addActionListener(l);
		usePattern.addActionListener(l);
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(alwaysShowGramCode, gbc);
		filterPanel.add(export, gbc);
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		filterPanel.add(delafStyle, gbc);
		filterPanel.add(tagFilterButton, gbc);
		gbc.gridwidth = 1;
		filterPanel.add(all, gbc);
		filterPanel.add(onlyShowGramCode, gbc);
		filterPanel.add(usePattern, gbc);
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		filterPanel.add(filterField, gbc);
		p.add(filterPanel, BorderLayout.NORTH);
		return p;
	}

	void exportTextAsTable(boolean delafStyle) {
		final JFileChooser chooser = new JFileChooser();
		chooser.addChoosableFileFilter(new PersonalFileFilter("txt",
				"Unicode Raw Texts"));
		chooser.setDialogType(JFileChooser.SAVE_DIALOG);
		chooser.setCurrentDirectory(Config.getCurrentCorpusDir());
		final int returnVal = chooser.showOpenDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			// we return if the user has clicked on CANCEL
			return;
		}
		GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
				.newExportTextAsPOSListDialog(chooser.getSelectedFile(), filter, delafStyle);
	}

	void refreshTableRowHeight(JTable table) {
		if (table.getRowCount() == 0)
			return;
		final TableCellRenderer renderer = table.getCellRenderer(0, 0);
		final int h = renderer.getTableCellRendererComponent(table,
				table.getValueAt(0, 0), false, false, 0, 0).getPreferredSize().height;
		table.setRowHeight(h);
	}

	/**
	 * We set each column to its preferred width.
	 */
	void refreshTableColumnWidths(JTable table) {
		for (int c = 0; c < table.getColumnCount(); c++) {
			final int w = getPreferredWidth(table, c);
			table.getColumnModel().getColumn(c).setPreferredWidth(w);
			table.getColumnModel().getColumn(c).setWidth(w);
		}
	}

	private int getPreferredWidth(JTable table, int column) {
		int max = 0;
		final TableCellRenderer renderer = table.getCellRenderer(0, column);
		int w = renderer.getTableCellRendererComponent(table,
				table.getColumnName(column), false, false, 0, column)
				.getPreferredSize().width;
		if (w > max)
	 		max = w;
		for (int row = 0; row < table.getRowCount(); row++) {
			w = renderer.getTableCellRendererComponent(table,
					table.getValueAt(row, column), false, false, row, column)
					.getPreferredSize().width;
			if (w > max)
				max = w;
		}
		return max + 10;
	}

	private JPanel constructElagPanel() {
		final JPanel elagframe = new JPanel(new BorderLayout());
		elagframe.setMinimumSize(new Dimension(0, 0));
		final JPanel p = new JPanel(new GridLayout(3, 1));
		JButton button = new JButton("Explode");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				exploseElagFst();
			}
		});
		p.add(button);
		button = new JButton("Implode");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				implodeElagFst();
			}
		});
		p.add(button);
		button = new JButton("Replace");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTfstTagsFrame();
				replaceElagFst();
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.newTfstTagsFrame(new File(Config.getCurrentSntDir(),"tfst_tags_by_freq.txt"));
			}
		});
		p.add(button);
		elagframe.add(p, BorderLayout.WEST);
		elaggraph = new TfstGraphicalZone(null, textfield, this, false);
		elaggraph.setPreferredSize(new Dimension(1188, 840));
		elagframe.add(new JScrollPane(elaggraph), BorderLayout.CENTER);
		return elagframe;
	}

	private JPanel constructUpPanel() {
		final JPanel upPanel = new JPanel(new BorderLayout());
		sentenceTextArea.setFont(ConfigManager.getManager().getTextFont(null));
		sentenceTextArea.setEditable(false);
		sentenceTextArea.setText("");
		sentenceTextArea.setLineWrap(true);
		sentenceTextArea.setWrapStyleWord(true);
		final JScrollPane textScroll = new JScrollPane(sentenceTextArea);
		textScroll.setPreferredSize(new Dimension(600, 100));
		textScroll
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		final JPanel tmp = new JPanel(new BorderLayout());
		tmp.setBorder(new EmptyBorder(2, 2, 2, 2));
		tmp.add(textScroll, BorderLayout.CENTER);
		upPanel.add(tmp, BorderLayout.CENTER);
		upPanel.add(constructCornerPanel(), BorderLayout.WEST);
		return upPanel;
	}

	private JPanel constructCornerPanel() {
		final JPanel cornerPanel = new JPanel(new GridLayout(8, 1));
		cornerPanel.setBorder(BorderFactory.createRaisedBevelBorder());
		cornerPanel.add(sentence_count_label);
		final JPanel middle = new JPanel(new BorderLayout());
		middle.add(new JLabel(" Sentence # "), BorderLayout.WEST);
		spinnerModel = new SpinnerNumberModel(1, 1, 1, 1);
		spinnerModel.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				// if we changed the value of the spinner in the code
				if (currentSentenceNumber == spinnerModel.getNumber().intValue()) {
					return;
				}
				if(containsEmptyState()) {
					spinnerModel.setValue(new Integer(currentSentenceNumber));
					JOptionPane.showMessageDialog(null,
							"Warning: the automaton can't contain empty boxes",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
					
				}
				loadSentence(spinnerModel.getNumber().intValue());
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class).updateTextAutomatonFindAndReplaceDialog();
			}
		});
		spinner = new JSpinner(spinnerModel);
		middle.add(spinner, BorderLayout.CENTER);
		cornerPanel.add(middle);
		final Action revertSentenceAction = new AbstractAction(
				"Revert To Last Save") {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				final int n = spinnerModel.getNumber().intValue();
				graphicalZone.clearStateSelection(n);
				final File f2 = new File(sentence_modified.getAbsolutePath()
						+ n + ".grf");
				if (f2.exists())
					f2.delete();
				loadSentence(n);
				graphicalZone.clearStateSelection(n);
			}
		};
		revertSentenceGraph = new JButton(revertSentenceAction);
		revertSentenceGraph.setVisible(false);
		cornerPanel.add(revertSentenceGraph);
	    undoButton = new JButton("Undo");
	    undoButton.setEnabled(false);
	    undoButton.addActionListener(new UndoIt());
	    cornerPanel.add(undoButton);
	    redoButton = new JButton("Redo");
	    redoButton.setEnabled(false);
	    redoButton.addActionListener(new RedoIt());
	    cornerPanel.add(redoButton);
	    buildTokensButton = new JButton("Token");
		buildTokensButton.setEnabled(true);
		buildTokensButton.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GenericGraphBox b = graphicalZone.getBoxes().get(0);
				int i = 2;
				while(b.getType() != 1) {
					if (b.getTransitions().size() == 1) {
						b = b.getTransitions().get(0);
						i++;
					} else {
						return;
					}
				}
			}
		});
		cornerPanel.add(buildTokensButton);

		final Action saveAction = new AbstractAction("Save") {
			@Override

			public void actionPerformed(ActionEvent arg0) {
				if(containsEmptyState()) {
					JOptionPane.showMessageDialog(null,
							"Warning: the automaton can't contain empty boxes",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
					
				}
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTextAutomatonFrame();
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTfstTagsFrame();
				Config.cleanTfstFiles(false);
				final RebuildTfstCommand command = new RebuildTfstCommand()
						.automaton(new File(Config.getCurrentSntDir(),
								"text.tfst"));
				Launcher.exec(command, true,
						new RebuildTextAutomatonDo(Config.getCurrentSntDir()));
			}
		};
		final JButton saveTfstButton = new JButton(saveAction);
		cornerPanel.add(saveTfstButton);
		
/*		final Action testsaveAction1 = new AbstractAction("Save best path") {
			
			public void actionPerformed(ActionEvent arg0) {
				if(containsEmptyState()) {
					JOptionPane.showMessageDialog(null,
							"Warning: the automaton can't contain empty boxes",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
					
				}
				
			        try {
			 
			            File file = new File(Config.getCurrentSntDir(),
								"best_path.tfst");
			 
			            if (file.createNewFile()) {
			                System.out.println("File 1 is created!");
			            } else {
			                System.out.println("File 1 already exists.");
			            }
			 
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTextAutomatonFrame();
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTfstTagsFrame();
				Config.cleanTfstFiles(false);
				
				final RebuildTfstCommand command = new RebuildTfstCommand()
						.automaton(new File(Config.getCurrentSntDir(),
								"text.tfst"));
				Launcher.exec(command, true,
						new RebuildTextAutomatonDo(Config.getCurrentSntDir()));
				
				InputStream inStream = null;
		        OutputStream outStream = null;

	        	System.out.println("Start copying files...!");
	            try {
					inStream = new FileInputStream(new File(Config.getCurrentSntDir(),
							"text.tfst"));
					outStream = new FileOutputStream(new File(Config.getCurrentSntDir(),
							"best_path.tfst"));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	 
	            int length;
	            byte[] buffer = new byte[1024];
	 
	            // copy the file content in bytes
	            try {
					while ((length = inStream.read(buffer)) > 0) {
					    outStream.write(buffer, 0, length);
					}
					inStream.close();
					outStream.close();
					
					System.out.println("File is copied successful!");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			    				

			}

		};
		final JButton testsaveTfstButton1 = new JButton(testsaveAction1);
		cornerPanel.add(testsaveTfstButton1);
		
		final Action testsaveAction2 = new AbstractAction("Save 2nd best path") {
			
			public void actionPerformed(ActionEvent arg0) {
				if(containsEmptyState()) {
					JOptionPane.showMessageDialog(null,
							"Warning: the automaton can't contain empty boxes",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
					
				}
				
			        try {
			 
			            File file = new File(Config.getCurrentSntDir(),
								"2nd_best_path.tfst");
			 
			            if (file.createNewFile()) {
			                System.out.println("File 2 is created!");
			            } else {
			                System.out.println("File 2 already exists.");
			            }
			 
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTextAutomatonFrame();
				GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
						.closeTfstTagsFrame();
				Config.cleanTfstFiles(false);
				
				final RebuildTfstCommand command = new RebuildTfstCommand()
						.automaton(new File(Config.getCurrentSntDir(),
								"text.tfst"));
				Launcher.exec(command, true,
						new RebuildTextAutomatonDo(Config.getCurrentSntDir()));
				
				InputStream inStream = null;
		        OutputStream outStream = null;

	        	System.out.println("Start copying files...!");
	            try {
					inStream = new FileInputStream(new File(Config.getCurrentSntDir(),
							"text.tfst"));
					outStream = new FileOutputStream(new File(Config.getCurrentSntDir(),
							"2nd_best_path.tfst"));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	 
	            int length;
	            byte[] buffer = new byte[1024];
	 
	            // copy the file content in bytes
	            try {
					while ((length = inStream.read(buffer)) > 0) {
					    outStream.write(buffer, 0, length);
					}
					inStream.close();
					outStream.close();
					
					System.out.println("File is copied successful!");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			    				

			}

		};
		final JButton testsaveTfstButton2 = new JButton(testsaveAction2);
		cornerPanel.add(testsaveTfstButton2);		
*/
		final JButton elagButton = new JButton("Elag Frame");
		elagButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleElagFrame();
				if (elagON) {
					elagButton.setText("Close Elag Frame");
				} else {
					elagButton.setText("Open  Elag Frame");
				}
			}
		});
		cornerPanel.add(elagButton);
		final JButton deleteStates = new JButton("Remove greyed boxes");
		deleteStates.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final ArrayList<GenericGraphBox> boxes = new ArrayList<GenericGraphBox>();
				for (final GenericGraphBox gb : graphicalZone.graphBoxes) {
					if (graphicalZone.isBoxNotPreferred((TfstGraphBox) gb)) {
						boxes.add(gb);
					}
				}
				graphicalZone.removeBoxes(boxes);
			}
		});
		buildTokensButton = new JButton("Check");
		buildTokensButton.setEnabled(true);
		buildTokensButton.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				checkGraph();
				if (checkList.isEmpty()) {
					JOptionPane.showMessageDialog(null,
							"Everything looks OK",
							"OK",
							JOptionPane.PLAIN_MESSAGE);
				} else {
					final CheckTextAutomatonDialog dialog = GlobalProjectManager.search(null)
														.getFrameManagerAs(InternalFrameManager.class)
														.newCheckTextAutomatonDialog(checkList);
				}
        final CheckTextAutomatonDialog dialog = GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class).newCheckTextAutomatonDialog(checkList);
			}
		});
		cornerPanel.add(deleteStates);
		return cornerPanel;
	}

	private boolean containsEmptyState() {
		for(int i = 0; i < graphicalZone.graphBoxes.size(); i++)
			if(graphicalZone.graphBoxes.get(i).getContent().equals("<E>") && graphicalZone.graphBoxes.get(i).type == GenericGraphBox.NORMAL)
				return true;
		return false;
	}
	
	 private boolean isGraphValid() {
         return checkGraph() == 0;
	 }

	private void reinitializeUndoManager() {
		graphicalZone.removeUndoableEditListener(manager);
		manager = new UndoManager();
		manager.setLimit(-1);
		graphicalZone.addUndoableEditListener(manager);
		updateDoUndoButtons();
	}
	
	private int checkGraph() {
		String text = sentenceTextArea.getText();
		int errorCount = 0;
		if (graphicalZone.getBoxes().isEmpty()) {
			return 0;
		}
		checkList.clear();
		// TODO for effiency
		/*TfstGraphBox firstBox = (TfstGraphBox) graphicalZone.getBoxes().get(0);
		for (int i = 0; i < firstBox.getTransitions().size(); i++) {
			TfstGraphBox nextBox =(TfstGraphBox) firstBox.getTransitions().get(i);
			checkContent(nextBox, 0, firstBox.getBounds());
		}
		System.out.println("End");*/
		for (int i = 0; i < graphicalZone.getBoxes().size(); i++) {
			TfstGraphBox b = (TfstGraphBox) graphicalZone.getBoxes().get(i);
			// if the box is not final then it should have at least one transition
			if (b.getTransitions().size() == 0 && b.getType() != 1) {
				errorCount++;
				checkList.add("Error: the box \"" + b.getContentText() + "\" has no outgoing transition");
			}
			if (b.isModified()) {
				if (b.getContent().startsWith("{")) {
					if (!text.contains(b.getContentText())) {
						errorCount++;
						checkList.add("Warning: the token \"" + b.getContentText() + "\" is not in the sentence ERR1");
					}
				}
				for (int j = 0; j < b.getTransitions().size(); j++) {
					TfstGraphBox nextBox = (TfstGraphBox) b.getTransitions().get(j);
					if (b.getBounds() == null) {
						// start & end
						// if initial
						if (b.getType() == 0 && (nextBox.getBounds() == null || nextBox.getBounds().getStart_in_tokens() != 0)) {
							errorCount++;
							checkList.add("Error: the first box has incorrect outgoing transition(s)");
							// if final
						} else if (b.getType() == 1) {
							errorCount++;
							checkList.add("Error: the last box must not have outgoing transition(s)");		
						} // if normal
					} else if ( b.getBounds() != null && nextBox.getBounds() != null) {
//						Only for Debugging
//						int diff = nextBox.getBounds().getStart_in_tokens() - b.getBounds().getStart_in_tokens();
//						System.out.println(b.getBoxNumber()+" "+b.getContentText());
//						System.out.println("diff = nextBounds - currentBounds : "+diff+" = "+nextBox.getBounds().getStart_in_tokens()+" - "+
//								b.getBounds().getStart_in_tokens());
//						if (diff > 2 || diff <= 0) {
//							System.out.println("dans le cas d'erreur de diff = 2 ou = 0");
//							errorCount++;
//							checkList.add("Error: the box \"" + b.getContentText() + "\" has incorrect transition with the box \"");
//						} else if (nextBox.getBounds().equals(b.getBounds())){
//							errorCount++;
//							checkList.add("Error: the box \"" + b.getContentText() + "\" has incorrect transition with the box \"");
//						}
					}
				}
			}
		}
		return errorCount;
	}

	// usefull later for efficiency
	private void checkContent(TfstGraphBox box, int textIndex, Bounds bounds) {
		// if last box then we are finished
		if (box.getType() == 1) {
			//System.out.println("End");
			return;
		}
		// If the parent box is the first one or if both box are side by side
		String text = sentenceTextArea.getText();
		int nextIndex = bounds == null || box.getBounds().getStart_in_tokens()-bounds.getEnd_in_tokens() == 1 ? textIndex+box.getBounds().getEnd_in_chars()+1 : textIndex+box.getBounds().getEnd_in_chars()+2;
		if (box.isModified()) {
			if (bounds == null || box.getBounds().getStart_in_tokens() - bounds.getEnd_in_tokens() == 1) {
				String subContent = text.substring(textIndex, textIndex + box.getBounds().getEnd_in_chars() + 1);
				//System.out.println("1Box: " +box.getContentText() + " || " +subContent);
				//nextIndex = textIndex+box.getBounds().getEnd_in_chars()+1;
				if (!box.getContentText().equals(subContent)) {
					JOptionPane.showMessageDialog(null,
							"Warning: the token \"" + box.getContentText() + "\" is not in the sentence. ERR2",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				//
			} else if (box.getBounds().getStart_in_tokens() - bounds.getEnd_in_tokens() == 2) {
				//System.out.println("textIndex: "+textIndex+ " | textIndex+1: "+(textIndex+1));
				String subContent = text.substring(textIndex, textIndex + 1);
				//System.out.println("subContent: \""+subContent+"\"");
				//nextIndex = textIndex+box.getBounds().getEnd_in_chars()+2;
				if (!subContent.equals(" ")) {
					JOptionPane.showMessageDialog(null,
							"Warning: there should be a white space in the text before \"" + box.getContentText() + "\".",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				//System.out.println("textIndex+1: "+(textIndex+1)+" | textIndex+1+box.getBounds().getEnd_in_chars()+1: " +(textIndex+1+box.getBounds().getEnd_in_chars()+1));
				subContent = text.substring(textIndex + 1, textIndex + 1 + box.getBounds().getEnd_in_chars() + 1);
				//System.out.println("2Box: " +box.getContentText() + " || " +subContent);
				if (!box.getContentText().equals(subContent)) {
					JOptionPane.showMessageDialog(null,
							"Warning: the token \"" + box.getContentText() + "\" is not in the sentence. ERR3",
							"Warning",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
			} else {
				return;
			}
		}
		for (int i = 0; i < box.getTransitions().size(); i++) {
			TfstGraphBox nextBox = (TfstGraphBox) box.getTransitions().get(i);
			checkContent(nextBox, nextIndex, box.getBounds());
		}
	}

  /**
	 * Shows the frame
	 */
	boolean loadTfst() {
		text_tfst = new File(Config.getCurrentSntDir(), "text.tfst");
		if (!text_tfst.exists()) {
			return false;
		}
		sentence_text = new File(Config.getCurrentSntDir(), "cursentence.txt");
		sentence_grf = new File(Config.getCurrentSntDir(), "cursentence.grf");
		sentence_tok = new File(Config.getCurrentSntDir(), "cursentence.tok");
		sentence_modified = new File(Config.getCurrentSntDir(), "sentence");
		elag_tfst = new File(Config.getCurrentSntDir(), "text-elag.tfst");
		elagsentence_grf = new File(Config.getCurrentSntDir(),
				"currelagsentence.grf");
		sentence_count = readSentenceCount(text_tfst);
		String s = " " + sentence_count;
		s = s + " sentence";
		if (sentence_count > 1)
			s = s + "s";
		sentence_count_label.setText(s);
		spinnerModel.setMaximum(sentence_count);
		spinnerModel.setValue(new Integer(1));
		for(int i = 1; i < getSentenceCount(); i++) 
			graphicalZone.clearStateSelection(i);
		if (sentence_count == 1) {
			/*
			 * The sentence_count!=1, spinnerModel.setValue does the job.
			 * Otherwise, setValue(1) is ignored because the current value is
			 * already 1
			 */
			loadSentence(1);
		}
		return true;
	}

	/**
	 * Indicates if the graph has been modified
	 *
	 * @param b
	 *            <code>true</code> if the graph has been modified,
	 *            <code>false</code> otherwise
	 */
	void setModified(boolean b) {
		repaint();
		revertSentenceGraph.setVisible(b);
		final int n = spinnerModel.getNumber().intValue();
		if (b && !isAcurrentLoadingThread && n != 0) {
			/*
			 * We save each modification, but only if the sentence graph loading
			 * is terminated
			 */
			final TfstGraphIO g = new TfstGraphIO(graphicalZone);
			g.saveSentenceGraph(new File(sentence_modified.getAbsolutePath()
					+ n + ".grf"), graphicalZone.getGraphPresentationInfo());
		}
	}
/*
	void setModified(boolean b) {
		repaint();
		revertSentenceGraph.setVisible(b);
		final int n = spinnerModel.getNumber().intValue();
		if (b && !isAcurrentLoadingThread && n != 0) {
		
			/*
			 * We save each modification, but only if the sentence graph loading
			 * is terminated
			 */
/*
			final TfstGraphIO g = new TfstGraphIO(graphicalZone);
			g.saveSentenceGraph(new File(sentence_modified.getAbsolutePath()
					+ n + ".grf"), graphicalZone.getGraphPresentationInfo());
		}
	}
*/
	private int readSentenceCount(File f) {
		String s = "0";
		try {
			final InputStreamReader reader = Encoding.getInputStreamReader(f);
			if (reader == null) {
				return 0;
			}
			s = UnicodeIO.readLine(reader);
			if (s == null || s.equals("")) {
				return 0;
			}
			reader.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return Integer.parseInt(s);
	}

	public void loadSentenceFromConcordance(int n) {
		if (!isVisible() || isIcon()) {
			return;
		}
		if (n < 1 || n > sentence_count)
			return;
		if (loadSentence(n))
			spinnerModel.setValue(new Integer(n));
	}

	public boolean loadCurrSentence() {
		return loadSentence(spinnerModel.getNumber().intValue());
	}

	/**
	 * Loads a sentence automaton
	 *
	 * @param n
	 *            sentence number
	 * @return <code>false</code> if a sentence is already being loaded,
	 *         <code>true</code> otherwise
	 */
	boolean loadSentence(int n) {
		if (n < 1 || n > sentence_count)
			return false;
    reinitializeUndoManager();
		final int z = n;
		if (isAcurrentLoadingThread)
			return false;
		isAcurrentLoadingThread = true;
		graphicalZone.empty();
		sentenceTextArea.setText("");
		Tfst2GrfCommand cmd = new Tfst2GrfCommand().automaton(text_tfst)
				.sentence(z);
		cmd = cmd.font(ConfigManager.getManager().getInputFont(null).getName())
				.fontSize(ConfigManager.getManager().getInputFontSize(null));
		Console.addCommand(cmd.getCommandLine(), false, Log.getCurrentLogID());
		Process p;
		try {
			p = Runtime.getRuntime().exec(cmd.getCommandArguments(true));
			final BufferedInputStream in = new BufferedInputStream(
					p.getInputStream());
			final BufferedInputStream err = new BufferedInputStream(
					p.getErrorStream());
			new EatStreamThread(in).start();
			new EatStreamThread(err).start();
			p.waitFor();
		} catch (final IOException e1) {
			e1.printStackTrace();
		} catch (final InterruptedException e1) {
			e1.printStackTrace();
		}
		final String text = readSentenceText();
		TokensInfo.loadTokensInfo(sentence_tok, text);
		final File f = new File(sentence_modified + String.valueOf(z) + ".grf");
		final boolean isSentenceModified = f.exists();
		if (isSentenceModified) {
			loadSentenceGraph(new File(sentence_modified.getAbsolutePath()
					+ String.valueOf(z) + ".grf"),n);
			setModified(isSentenceModified);
		} else {
			loadSentenceGraph(sentence_grf,n);
		}
		isAcurrentLoadingThread = false;
		loadElagSentence(z);
		return true;
	}

	boolean loadElagSentence(int n) {
		if (n < 1 || n > sentence_count) {
			System.err.println("loadElagSentence: n = " + n + " out of bounds");
			return false;
		}
		final int z = n;
		if (isAcurrentElagLoadingThread) {
			return false;
		}
		isAcurrentElagLoadingThread = true;
		elaggraph.empty();
		if (!elag_tfst.exists()) { // if fst file does not exist exit
			isAcurrentElagLoadingThread = false;
			return false;
		}
		final Tfst2GrfCommand cmd = new Tfst2GrfCommand().automaton(elag_tfst)
				.sentence(z).output("currelagsentence")
				.font(ConfigManager.getManager().getInputFont(null).getName())
				.fontSize(ConfigManager.getManager().getInputFontSize(null));
		Console.addCommand(cmd.getCommandLine(), false, Log.getCurrentLogID());
		try {
			final Process p = Runtime.getRuntime().exec(
					cmd.getCommandArguments(true));
			final BufferedInputStream in = new BufferedInputStream(
					p.getInputStream());
			final BufferedInputStream err = new BufferedInputStream(
					p.getErrorStream());
			new EatStreamThread(in).start();
			new EatStreamThread(err).start();
			p.waitFor();
		} catch (final IOException e1) {
			e1.printStackTrace();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		loadElagSentenceGraph(elagsentence_grf);
		isAcurrentElagLoadingThread = false;
		return true;
	}

	public void changeAntialiasingValue() {
		final boolean a = graphicalZone.getAntialiasing();
		graphicalZone.setAntialiasing(!a);
	}

	String readSentenceText() {
		String s = "";
		try {
			final InputStreamReader br = Encoding
					.getInputStreamReader(sentence_text);
			if (br == null) {
				return "";
			}
			s = UnicodeIO.readLine(br);
			if (s == null || s.equals("")) {
				return "";
			}
			sentenceTextArea.setFont(ConfigManager.getManager().getTextFont(
					null));
			sentenceTextArea.setText(s);
			br.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return s;
	}
	boolean loadSentenceGraph(File file,int sentence) {
		setModified(false);
		final GraphIO g = GraphIO.loadGraph(file, true, true);
		if (g == null) {
			return false;
		}
		textfield.setFont(g.getInfo().getInput().getFont());
		graphicalZone.setup(g,sentence);
		tfstTableModel.init(g.getBoxes());
		final Timer t = new Timer(300, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (ConfigManager.getManager().isRightToLeftForGraphs(null)) {
					tfstScrollbar.setValue(tfstScrollbar.getMaximum());
				} else {
					tfstScrollbar.setValue(0);
				}
			}
		});
		t.setRepeats(false);
		t.start();
		return true;
	}
/*
	boolean loadSentenceGraph(File file,int sentence) {
		setModified(false);
		final GraphIO g = GraphIO.loadGraph(file, true, true);
		if (g == null) {
			return false;
		}
		textfield.setFont(g.getInfo().getInput().getFont());
		graphicalZone.setup(g,sentence);
		tfstTableModel.init(g.getBoxes());
		final Timer t = new Timer(300, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (ConfigManager.getManager().isRightToLeftForGraphs(null)) {
					tfstScrollbar.setValue(tfstScrollbar.getMaximum());
				} else {
					tfstScrollbar.setValue(0);
				}
			}
		});
		t.setRepeats(false);
		t.start();
		return true;
	}
*/
	boolean loadElagSentenceGraph(File file) {
		setModified(false);
		final GraphIO g = GraphIO.loadGraph(file, true, true);
		if (g == null)
			return false;
		elaggraph.setup(g,-1);
		return true;
	}

	void openElagFrame() {
		superpanel.setDividerLocation(0.5);
		superpanel.setResizeWeight(0.5);
		elagON = true;
	}

	void closeElagFrame() {
		superpanel.setDividerLocation(1000);
		superpanel.setResizeWeight(1.0);
		elagON = false;
	}

	void toggleElagFrame() {
		if (elagON) {
			closeElagFrame();
		} else {
			openElagFrame();
		}
	}

	void elagDialog() {
		final JLabel titlelabel = new JLabel("Elag Rule:");
		elagrules = new File(Config.getCurrentElagDir(), "elag.rul");
		ruleslabel = new JLabel(elagrules.getName());
		ruleslabel.setBorder(new LineBorder(Color.black, 1, true));
		final JButton button = new JButton("Browse");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final JFileChooser fc = new JFileChooser();
				fc.setCurrentDirectory(elagrules.getParentFile());
				fc.setFileFilter(new PersonalFileFilter("rul",
						"Elag rules file ( .rul)"));
				fc.setAcceptAllFileFilterUsed(false);
				fc.setDialogTitle("Choose Elag Rule File");
				fc.setDialogType(JFileChooser.OPEN_DIALOG);
				if ((fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
						|| (fc.getSelectedFile() == null)) {
					return;
				}
				elagrules = fc.getSelectedFile();
				ruleslabel.setText(elagrules.getName());
			}
		});
		final JCheckBox implodeCheckBox = new JCheckBox(
				"Implode resulting text automaton", true);
		final BorderLayout layout = new BorderLayout();
		layout.setVgap(10);
		layout.setHgap(10);
		final JPanel p = new JPanel(layout);
		p.add(titlelabel, BorderLayout.WEST);
		p.add(ruleslabel, BorderLayout.CENTER);
		p.add(button, BorderLayout.EAST);
		p.add(implodeCheckBox, BorderLayout.SOUTH);
		if (JOptionPane.showInternalConfirmDialog(this, p, "Apply Elag Rule",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		final ElagCommand elagcmd = new ElagCommand()
				.lang(new File(Config.getCurrentElagDir(), "tagset.def"))
				.rules(elagrules).output(elag_tfst).automaton(text_tfst);
		if (implodeCheckBox.isSelected()) {
			Launcher.exec(elagcmd, false, new ImplodeDo(this, elag_tfst));
		} else {
			Launcher.exec(elagcmd, false, new LoadSentenceDo1(this));
		}
	}

	void replaceElagFst() {
		if (!elag_tfst.exists()) {
			JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
					"replaceElagFst: file '" + elag_tfst + "' doesn't exists");
			return;
		}
		/* cleanup files */
		final File dir = Config.getCurrentSntDir();
		final File old_tfst_tags_by_freq = new File(Config.getCurrentSntDir(),
				"tfst_tags_by_freq.txt");
		final File old_tfst_tags_by_alph = new File(Config.getCurrentSntDir(),
				"tfst_tags_by_alph.txt");
		final File new_tfst_tags_by_freq = new File(Config.getCurrentSntDir(),
				"tfst_tags_by_freq.new.txt");
		final File new_tfst_tags_by_alph = new File(Config.getCurrentSntDir(),
				"tfst_tags_by_alph.new.txt");
		old_tfst_tags_by_freq.delete();
		old_tfst_tags_by_alph.delete();
		new_tfst_tags_by_freq.renameTo(old_tfst_tags_by_freq);
		new_tfst_tags_by_alph.renameTo(old_tfst_tags_by_alph);
		FileUtil.deleteFileByName(new File(Config.getCurrentSntDir(),
				"sentence*.grf"));
		File f = new File(dir, "currelagsentence.grf");
		if (f.exists() && !f.delete()) {
			JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
					"Failed to delete " + f);
		}
		f = new File(dir, "currelagsentence.txt");
		if (f.exists() && !f.delete()) {
			JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
					"Failed to delete " + f);
		}
		if (text_tfst.exists() && !text_tfst.delete()) {
			JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
					"Failed to delete " + text_tfst);
		}
		if (!elag_tfst.renameTo(text_tfst)) {
			JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
					"Failed to replace " + text_tfst + " with " + elag_tfst);
		}
		final File old_tfst_tind = new File(Config.getCurrentSntDir(),
				"text.tind");
		final File new_tfst_tind = new File(Config.getCurrentSntDir(),
				"text-elag.tind");
		old_tfst_tind.delete();
		new_tfst_tind.renameTo(old_tfst_tind);
		loadCurrSentence();
	}

	void exploseElagFst() {
		GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
				.closeTfstTagsFrame();
		explodeTextAutomaton(elag_tfst);
		GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
				.newTfstTagsFrame(new File(Config.getCurrentSntDir(), "tfst_tags_by_freq.txt"));
	}

	void implodeElagFst() {
		GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
				.closeTfstTagsFrame();
		implodeTextAutomaton(elag_tfst);
		GlobalProjectManager.search(null).getFrameManagerAs(InternalFrameManager.class)
				.newTfstTagsFrame(new File(Config.getCurrentSntDir(), "tfst_tags_by_freq.txt"));
	}

	boolean explodeTextAutomaton(File f) {
		if (!f.exists()) {
			return false;
		}
		final TagsetNormTfstCommand tagsetcmd = new TagsetNormTfstCommand()
				.tagset(new File(Config.getCurrentElagDir(), "tagset.def"))
				.automaton(f);
		Launcher.exec(tagsetcmd, true, new LoadSentenceDo1(this));
		return true;
	}

	boolean implodeTextAutomaton(File f) {
		if (!f.exists()) {
			return false;
		}
		final ImplodeTfstCommand imploseCmd = new ImplodeTfstCommand()
				.automaton(f);
		Launcher.exec(imploseCmd, true, new LoadSentenceDo1(this));
		return true;
	}

	/**
	 * Normalize the main text automaton according to tagset description in
	 * tagset.def if implode is true, then implode the resulting automaton
	 */
	void normalizeFst(boolean implode) {
		final TagsetNormTfstCommand tagsetcmd = new TagsetNormTfstCommand()
				.tagset(new File(Config.getCurrentElagDir(), "tagset.def"))
				.automaton(text_tfst);
		if (implode) {
			Launcher.exec(tagsetcmd, false, new ImplodeDo(this, text_tfst));
		} else {
			Launcher.exec(tagsetcmd, false, new LoadSentenceDo1(this));
		}
	}

	class RebuildTextAutomatonDo implements ToDo {
		File sntDir;

		public RebuildTextAutomatonDo(File sntDir) {
			this.sntDir = sntDir;
		}
		
		@Override
		public void toDo(boolean success) {
			FileUtil.deleteFileByName(new File(sntDir, "sentence*.grf"));
			File f = new File(sntDir, "currelagsentence.grf");
			if (f.exists() && !f.delete()) {
				JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
						"Failed to delete " + f);
			}
			f = new File(sntDir, "currelagsentence.txt");
			if (f.exists() && !f.delete()) {
				JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
						"Failed to delete " + f);
			}
			f = new File(sntDir, "text-elag.tfst");
			if (f.exists() && !f.delete()) {
				JOptionPane.showInternalMessageDialog(UnitexFrame.mainFrame,
						"unable to delete " + f);
			}
			GlobalProjectManager.search(sntDir).getFrameManagerAs(InternalFrameManager.class)
					.newTextAutomatonFrame(1,false);
		}
	}

	public int getSentenceCount() {
		return (Integer) spinnerModel.getMaximum();
	}

	@Override
	public JScrollPane getTfstScrollPane() {
		return scrollPane;
	}

	@Override
	public TfstGraphicalZone getTfstGraphicalZone() {
		return graphicalZone;
	}

  	public UndoManager getManager() {
    	return manager;
  	}

  	private void updateDoUndoButtons() {
    	if (undoButton != null && redoButton != null) {
      		undoButton.setEnabled(getManager().canUndo());
      		redoButton.setEnabled(getManager().canRedo());
    	}
  	}

  	@Override
  	public void repaint() {
    	super.repaint();
    	updateDoUndoButtons();
  	}

  	class UndoIt implements ActionListener {
    	@Override
    	public void actionPerformed(ActionEvent ev) {
     		undo();
    	}
  	}

  	public void undo() {
    	try {
      		if (getManager().canUndo()) {
        		getManager().undo();
      		}
    	} catch (final CannotUndoException ex) {
      		ex.printStackTrace();
    	} finally {
      		graphicalZone.unSelectAllBoxes();
      		repaint();
    	}
  	}

  	class RedoIt implements ActionListener {
    	@Override
    	public void actionPerformed(ActionEvent ev) {
      	redo();
    	}
  	}

  	public void redo() {
    	try {
      		if (getManager().canRedo()) {
        		getManager().redo();
      		}
    	} catch (final CannotRedoException ex) {
      	/* */
    	} finally {
      		repaint();
    	}
  	}

	public void loadNextSentence() {
		int currentSentence = spinnerModel.getNumber().intValue();
		int nextSentence = currentSentence + 1;
		if (nextSentence > sentence_count) {
			nextSentence = nextSentence % sentence_count;
		}
		spinnerModel.setValue(new Integer(nextSentence));
	}

	public void loadPrevSentence() {
		int currentSentence = spinnerModel.getNumber().intValue();
		int prevSentence = currentSentence - 1;
		if (prevSentence == 0) {
			prevSentence = sentence_count;
		}
		spinnerModel.setValue(new Integer(prevSentence));
	}

}

class LoadSentenceDo1 implements ToDo {
	private final TextAutomatonFrame frame;

	LoadSentenceDo1(TextAutomatonFrame f) {
		frame = f;
	}

	@Override
	public void toDo(boolean success) {
		frame.loadCurrSentence();
	}
}

class ImplodeDo implements ToDo {
	private final File fst;
	private final TextAutomatonFrame fr;

	public ImplodeDo(TextAutomatonFrame frame, File f) {
		fst = f;
		fr = frame;
	}

	@Override
	public void toDo(boolean success) {
		fr.implodeTextAutomaton(fst);
	}
}
