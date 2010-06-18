/*
 * Unitex
 *
 * Copyright (C) 2001-2010 Université Paris-Est Marne-la-Vallée <unitex@univ-mlv.fr>
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
package fr.umlv.unitex.graphrendering;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.util.Date;

import javax.swing.undo.UndoableEdit;

import fr.umlv.unitex.MyCursors;
import fr.umlv.unitex.frames.GraphFrame;
import fr.umlv.unitex.frames.UnitexFrame;
import fr.umlv.unitex.io.GraphIO;
import fr.umlv.unitex.undo.TranslationGroupEdit;

/**
 * This class describes a component on which a graph can be drawn.
 * 
 * @author Sébastien Paumier
 * 
 */
public class GraphicalZone extends GenericGraphicalZone implements Printable {
	boolean dragBegin = true;
	int dX, dY;

	/**
	 * Constructs a new <code>GraphicalZone</code>.
	 * 
	 * @param w
	 *            width of the drawing area
	 * @param h
	 *            height of the drawing area
	 * @param t
	 *            text field to edit box contents
	 * @param p
	 *            frame that contains the component
	 */
	public GraphicalZone(GraphIO gio, TextField t, GraphFrame p) {
		super(gio, t, p);
		addMouseListener(new MyMouseListener());
		addMouseMotionListener(new MyMouseMotionListener());
	}

	@Override
	protected void initializeEmptyGraph() {
		GraphBox g, g2;
		// creating the final state
		g = new GraphBox(300, 200, 1, this);
		g.setContent("<E>");
		// and the initial state
		g2 = new GraphBox(70, 200, 0, this);
		g2.n_lines = 0;
		g2.setContent("<E>");
		addBox(g2);
		addBox(g);
		Dimension d = new Dimension(1188, 840);
		setSize(d);
		setPreferredSize(new Dimension(d));
	}

	@Override
	protected GenericGraphBox createBox(int x, int y) {
		GraphBox g = new GraphBox(x, y, 2, this);
		g.setContent("<E>");
		addBox(g);
		return g;
	}

	@Override
	protected GenericGraphBox newBox(int x, int y, int type, GenericGraphicalZone p) {
		return new GraphBox(x, y, type, (GraphicalZone) p);
	}

	class MyMouseListener implements MouseListener {

		public void mouseClicked(MouseEvent e) {
			int boxSelected;
			GraphBox b;
			int x_tmp, y_tmp;
			if (EDITING_MODE == MyCursors.REVERSE_LINK_BOXES
					|| (EDITING_MODE == MyCursors.NORMAL && e.isShiftDown())) {
				// Shift+click
				// reverse transitions
				boxSelected = getSelectedBox((int) (e.getX() / scaleFactor),
						(int) (e.getY() / scaleFactor));
				if (boxSelected != -1) {
					// if we click on a box
					b = (GraphBox) graphBoxes.get(boxSelected);
					if (!selectedBoxes.isEmpty()) {
						// if there are selected boxes, we rely them to the
						// current
						addReverseTransitionsFromSelectedBoxes(b);
						unSelectAllBoxes();
					} else {
						if (EDITING_MODE == MyCursors.REVERSE_LINK_BOXES) {
							// if we click on a box while there is no box
							// selected in REVERSE_LINK_BOXES mode,
							// we select it
							b.selected = true;
							selectedBoxes.add(b);
							fireGraphTextChanged(b.content);
						}
					}
				} else {
					// simple click not on a box
					unSelectAllBoxes();
				}
			} else if (EDITING_MODE == MyCursors.CREATE_BOXES
					|| (EDITING_MODE == MyCursors.NORMAL && e.isControlDown())) {
				// Control+click
				// creation of a new box
				b = (GraphBox) createBox((int) (e.getX() / scaleFactor),
						(int) (e.getY() / scaleFactor));
				// if some boxes are selected, we rely them to the new one
				if (!selectedBoxes.isEmpty()) {
					addTransitionsFromSelectedBoxes(b, false);
				}
				// then, the only selected box is the new one
				unSelectAllBoxes();
				b.selected = true;
				selectedBoxes.add(b);
				fireGraphTextChanged(b.content); /* Should be "<E>" */
				fireGraphChanged(true);
			} else if (EDITING_MODE == MyCursors.OPEN_SUBGRAPH
					|| (EDITING_MODE == MyCursors.NORMAL && e.isAltDown())) {
				// Alt+click
				// opening of a sub-graph
				x_tmp = (int) (e.getX() / scaleFactor);
				y_tmp = (int) (e.getY() / scaleFactor);
				boxSelected = getSelectedBox(x_tmp, y_tmp);
				if (boxSelected != -1) {
					// if we click on a box
					b = (GraphBox) graphBoxes.get(boxSelected);
					File file = b.getGraphClicked(y_tmp);
					if (file != null) {
						UnitexFrame.getFrameManager().newGraphFrame(file);
					}
				}
			} else if (EDITING_MODE == MyCursors.KILL_BOXES) {
				// killing a box
				if (!selectedBoxes.isEmpty()) {
					// if boxes are selected, we remove them
					removeSelected();
				} else {
					// else, we check if we clicked on a box
					x_tmp = (int) (e.getX() / scaleFactor);
					y_tmp = (int) (e.getY() / scaleFactor);
					boxSelected = getSelectedBox(x_tmp, y_tmp);
					if (boxSelected != -1) {
						b = (GraphBox) graphBoxes.get(boxSelected);
						b.selected = true;
						selectedBoxes.add(b);
						removeSelected();
					}
				}
			} else {
				boxSelected = getSelectedBox((int) (e.getX() / scaleFactor),
						(int) (e.getY() / scaleFactor));
				if (boxSelected != -1) {
					// if we click on a box
					b = (GraphBox) graphBoxes.get(boxSelected);
					if (!selectedBoxes.isEmpty()) {
						// if there are selected boxes, we rely them to the
						// current one
						addTransitionsFromSelectedBoxes(b, true);
						unSelectAllBoxes();
					} else {
						if (!((EDITING_MODE == MyCursors.LINK_BOXES) && (b.type == 1))) {
							// if not, we just select this one, but only if we
							// are not clicking
							// on final state in LINK_BOXES mode
							b.selected = true;
							selectedBoxes.add(b);
							fireGraphTextChanged(b.content);
						}
					}
				} else {
					// simple click not on a box
					unSelectAllBoxes();
				}
			}
			fireGraphChanged(false);
		}

		public void mousePressed(MouseEvent e) {
			int selectedBox;
			if ((EDITING_MODE == MyCursors.NORMAL && (e.isShiftDown()
					|| e.isAltDown() || e.isControlDown()))
					|| (EDITING_MODE == MyCursors.OPEN_SUBGRAPH)
					|| (EDITING_MODE == MyCursors.KILL_BOXES)) {
				return;
			}
			validateContent();
			X_start_drag = (int) (e.getX() / scaleFactor);
			Y_start_drag = (int) (e.getY() / scaleFactor);
			X_end_drag = X_start_drag;
			Y_end_drag = Y_start_drag;
			X_drag = X_start_drag;
			Y_drag = Y_start_drag;
			dragWidth = 0;
			dragHeight = 0;
			selectedBox = getSelectedBox(X_start_drag, Y_start_drag);
			singleDragging = false;
			dragging = false;
			selecting = false;
			if (selectedBox != -1) {
				// if we start dragging a box
				singleDraggedBox = graphBoxes.get(selectedBox);
				fireGraphTextChanged(singleDraggedBox.content);
				if (!singleDraggedBox.selected) {
					/* Dragging a selected box is handled below with
					 * the general multiple box draggind case */
					dragging = true;
					singleDragging = true;
					singleDraggedBox.singleDragging = true;
					fireGraphChanged(true);
				}
				return;
			}
			if (!selectedBoxes.isEmpty()) {
				dragging = true;
				fireGraphChanged(true);
				return;
			}
			if ((selectedBox == -1) && selectedBoxes.isEmpty()) {
				// being drawing a selection rectangle
				dragging = false;
				selecting = true;
				fireGraphChanged(false);
			}
		}

		public void mouseReleased(MouseEvent e) {
			if (e.isShiftDown() || e.isAltDown() || e.isControlDown())
				return;
			int dx = X_end_drag - X_start_drag;
			int dy = Y_end_drag - Y_start_drag;
			if (singleDragging) {
				// save position after the dragging
				selectedBoxes.add(singleDraggedBox);
				UndoableEdit edit = new TranslationGroupEdit(selectedBoxes, dx,
						dy);
				postEdit(edit);
				selectedBoxes.remove(singleDraggedBox);
				dragging = false;
				singleDragging = false;
				singleDraggedBox.singleDragging = false;
				fireGraphChanged(true);
				return;
			}
			if (dragging && EDITING_MODE == MyCursors.NORMAL) {
				// save the position of all the translated boxes
				UndoableEdit edit = new TranslationGroupEdit(selectedBoxes,
							dx, dy);
				postEdit(edit);
				fireGraphChanged(true);
			}
			dragging = false;
			if (selecting == true) {
				selectByRectangle(X_drag, Y_drag, dragWidth, dragHeight);
				selecting = false;
			}
			fireGraphChanged(false);
		}

		public void mouseEntered(MouseEvent e) {
			mouseInGraphicalZone = true;
			fireGraphChanged(false);
		}

		public void mouseExited(MouseEvent e) {
			mouseInGraphicalZone = false;
			fireGraphChanged(false);
		}
	}

	class MyMouseMotionListener implements MouseMotionListener {
		public void mouseDragged(MouseEvent e) {
			int Xtmp = X_end_drag;
			int Ytmp = Y_end_drag;
			X_end_drag = (int) (e.getX() / scaleFactor);
			Y_end_drag = (int) (e.getY() / scaleFactor);
			int dx = X_end_drag - Xtmp;
			int dy = Y_end_drag - Ytmp;
			dX += dx;
			dY += dy;
			if (singleDragging) {
				// translates the single dragged box
				singleDraggedBox.translate(dx, dy);
				fireGraphChanged(true);
				return;
			}
			if (dragging && EDITING_MODE == MyCursors.NORMAL) {
				// translates all the selected boxes
				translateAllSelectedBoxes(dx, dy);
				// if we were dragging, we have nothing else to do
				return;
			}
			/* If the user is setting the selection rectangle */
			if (X_start_drag < X_end_drag) {
				X_drag = X_start_drag;
				dragWidth = X_end_drag - X_start_drag;
			} else {
				X_drag = X_end_drag;
				dragWidth = X_start_drag - X_end_drag;
			}
			if (Y_start_drag < Y_end_drag) {
				Y_drag = Y_start_drag;
				dragHeight = Y_end_drag - Y_start_drag;
			} else {
				Y_drag = Y_end_drag;
				dragHeight = Y_start_drag - Y_end_drag;
			}
			fireGraphChanged(false);
		}

		public void mouseMoved(MouseEvent e) {
			Xmouse = (int) (e.getX() / scaleFactor);
			Ymouse = (int) (e.getY() / scaleFactor);
			if ((EDITING_MODE == MyCursors.REVERSE_LINK_BOXES || EDITING_MODE == MyCursors.LINK_BOXES)
					&& !selectedBoxes.isEmpty()) {
				fireGraphChanged(false);
			}
		}
	}

	/**
	 * Draws the graph. This method should only be called by the virtual
	 * machine.
	 * 
	 * @param f_old
	 *            the graphical context
	 */
	@Override
	public void paintComponent(Graphics f_old) {
		setClipZone(f_old.getClipBounds());
		Graphics2D f = (Graphics2D) f_old;
		f.scale(scaleFactor, scaleFactor);
		if (info.antialiasing) {
			f.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
		} else {
			f.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_OFF);
		}
		f.setColor(new Color(205, 205, 205));
		f.fillRect(0, 0, getWidth(), getHeight());
		f.setColor(info.backgroundColor);
		f.fillRect(0, 0, getWidth(), getHeight());
		if (info.frame) {
			f.setColor(info.foregroundColor);
			f.drawRect(10, 10, getWidth() - 20, getHeight() - 20);
			f.drawRect(9, 9, getWidth() - 18, getHeight() - 18);
		}
		f.setColor(info.foregroundColor);
		File file = ((GraphFrame) parentFrame).getGraph();
		if (info.filename) {
			if (info.pathname)
				f.drawString((file != null) ? file.getAbsolutePath() : "", 20,
						getHeight() - 45);
			else
				f.drawString((file != null) ? file.getName() : "", 20,
						getHeight() - 45);
		}
		if (info.date)
			f.drawString(new Date().toString(), 20, getHeight() - 25);
		drawGrid(f);
		if (mouseInGraphicalZone && !selectedBoxes.isEmpty()) {
			if (EDITING_MODE == MyCursors.REVERSE_LINK_BOXES) {
				drawTransitionsFromMousePointerToSelectedBoxes(f);
			} else if (EDITING_MODE == MyCursors.LINK_BOXES) {
				drawTransitionsFromSelectedBoxesToMousePointer(f);
			}
		}
		drawAllTransitions(f);
		drawAllBoxes(f);
		if (selecting) {
			// here we draw the selection rectangle
			f.setColor(info.foregroundColor);
			f.drawRect(X_drag, Y_drag, dragWidth, dragHeight);
		}
	}

	/**
	 * Prints the graph.
	 * 
	 * @param g
	 *            the graphical context
	 * @param p
	 *            the page format
	 * @param pageIndex
	 *            the page index
	 */
	public int print(Graphics g, PageFormat p, int pageIndex) {
		if (pageIndex != 0)
			return Printable.NO_SUCH_PAGE;
		Graphics2D f = (Graphics2D) g;
		double DPI = 96.0;
		double WidthInInches = p.getImageableWidth() / 72;
		double realWidthInInches = (getWidth() / DPI);
		double HeightInInches = p.getImageableHeight() / 72;
		double realHeightInInches = (getHeight() / DPI);
		double scale_x = WidthInInches / realWidthInInches;
		double scale_y = HeightInInches / realHeightInInches;
		f.translate(p.getImageableX(), p.getImageableY());
		if (scale_x < scale_y)
			f.scale(0.99 * 0.72 * scale_x, 0.99 * 0.72 * scale_x);
		else
			f.scale(0.99 * 0.72 * scale_y, 0.99 * 0.72 * scale_y);
		f.setColor(info.backgroundColor);
		f.fillRect(0, 0, getWidth(), getHeight());
		if (info.frame) {
			f.setColor(info.foregroundColor);
			Stroke oldStroke=f.getStroke();
			f.setStroke(GraphicalToolBox.frameStroke);
			f.drawRect(10, 10, getWidth() - 20, getHeight() - 20);
			f.setStroke(oldStroke);
		}
		f.setColor(info.foregroundColor);
		File file = ((GraphFrame) parentFrame).getGraph();
		if (info.filename) {
			if (info.pathname)
				f.drawString((file != null) ? file.getAbsolutePath() : "", 20,
						getHeight() - 45);
			else
				f.drawString((file != null) ? file.getName() : "", 20,
						getHeight() - 45);
		}
		if (info.date)
			f.drawString(new Date().toString(), 20, getHeight() - 25);
		drawGrid(f);
		drawAllTransitions(f);
		drawAllBoxes(f);
		if (selecting) {
			// here we draw the selection rectangle
			f.drawRect(X_drag, Y_drag, dragWidth, dragHeight);
		}
		return Printable.PAGE_EXISTS;
	}

}