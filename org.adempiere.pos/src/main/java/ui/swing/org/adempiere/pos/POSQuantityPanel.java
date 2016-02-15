/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2014 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Raul Muñoz www.erpcya.com					              *
 *****************************************************************************/
package org.adempiere.pos;

import java.awt.Dimension;
import java.awt.Event;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;

import javax.swing.KeyStroke;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pos.service.I_POSPanel;
import org.compiere.apps.ADialog;
import org.compiere.grid.ed.VNumber;
import org.compiere.model.MOrder;
import org.compiere.swing.CButton;
import org.compiere.swing.CLabel;
import org.compiere.swing.CPanel;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * Button panel Up Down
 * @author Mario Calderon, mario.calderon@westfalia-it.com, Systemhaus Westfalia, http://www.westfalia-it.com
 * @author Raul Muñoz, rmunoz@erpcya.com, ERPCYA http://www.erpcya.com
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 * @author Victor Perez , victor.perez@e-evolution.com , e-Evolution http//www.e-evolution.com
 */
public class POSQuantityPanel extends POSSubPanel implements I_POSPanel, ActionListener {

	public POSQuantityPanel(VPOS posPanel) {
		super(posPanel);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -8002370020007368736L;
	
	/** Buttons Controls  		*/
	private CButton 			buttonUp;
	private CButton 			buttonDelete;
	private CButton				buttonDown;
	private CButton 			buttonPlus;
	private CButton 			buttonMinus;
	private CButton 			buttonScales;
	private VNumber 			fieldPrice;
	private VNumber 			fieldDiscountPercentage;
	private VNumber 			fieldQuantity;

	/**	Button Panel			*/
	private CPanel 				buttonPanel;
	/**	Padding					*/
	private int 				topPadding;
	private int 				leftPadding;
	private int 				bottonPadding;
	private int 				rightPadding;

	@Override
	protected void init() {
		//	Content
		setLayout(new GridBagLayout());
		//	Button Panel
		buttonPanel = new CPanel(new GridBagLayout());
		//	
		topPadding = 0;
		leftPadding = 1;
		bottonPadding = 0;
		rightPadding = 1;

		buttonDelete = createButtonAction("Cancel", KeyStroke.getKeyStroke(KeyEvent.VK_F3, Event.CTRL_MASK));
		buttonDelete.setToolTipText("CTL+F3-" + Msg.translate(ctx, "DeleteLine"));
		buttonDelete.addActionListener(posPanel.getUserPinListener());

		buttonPanel.add(buttonDelete, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		buttonPlus = createButtonAction("Plus", KeyStroke.getKeyStroke(KeyEvent.VK_1, Event.CTRL_MASK));
		buttonPlus.setToolTipText("CTL+1-" + Msg.translate(ctx, "add"));
		buttonPanel.add(buttonPlus, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		buttonMinus = createButtonAction("Minus", KeyStroke.getKeyStroke(KeyEvent.VK_0,Event.CTRL_MASK));
		buttonMinus.setToolTipText("CTL+0-" + Msg.translate(ctx, "decrement"));  // TODO: Create message for decrementing qty by 1
		buttonPanel.add(buttonMinus, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		buttonUp = createButtonAction("Previous", KeyStroke.getKeyStroke(KeyEvent.VK_UP, Event.ALT_MASK));
		buttonUp.setToolTipText("ALT+up-" + Msg.translate(ctx, "Previous"));
		buttonPanel.add(buttonUp, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));
		
		buttonDown = createButtonAction("Next", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, Event.ALT_MASK));
		buttonDown.setToolTipText("ALT+down-" + Msg.translate(ctx, "Next"));
		buttonPanel.add(buttonDown, new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		if (posPanel.isPresentElectronicScales()) {
			buttonScales = createButtonAction("Calculator", KeyStroke.getKeyStroke(KeyEvent.VK_W, Event.CTRL_MASK));
			buttonScales.setToolTipText("ALT+down-" + Msg.translate(ctx, "Calculator"));
			buttonPanel.add(buttonScales, new GridBagConstraints(5, 0, 1, 1, 0.0, 0.0
					, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));
			buttonScales.addActionListener(posPanel.getScalesListener());
		}

		CLabel qtyLabel = new CLabel(Msg.translate(Env.getCtx(), "Qty"));
		qtyLabel.setDisplayedMnemonic(KeyEvent.VK_Q);
		buttonPanel.add(qtyLabel, new GridBagConstraints(6, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		//
		fieldQuantity = new VNumber();
		fieldQuantity.addActionListener(this);
		fieldQuantity.setFont(posPanel.getFont());
		fieldQuantity.setPreferredSize(new Dimension(100, 50));
		fieldQuantity.setMinimumSize(new Dimension(100, 50));
		fieldQuantity.setValue(Env.ZERO);
		qtyLabel.setLabelFor(fieldQuantity);
		buttonPanel.add(fieldQuantity, new GridBagConstraints(7, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		CLabel priceLabel = new CLabel(Msg.translate(Env.getCtx(), "PriceActual"));
		priceLabel.setDisplayedMnemonic(KeyEvent.VK_P);
		buttonPanel.add(priceLabel, new GridBagConstraints(8, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));


		//
		fieldPrice = new VNumber();
		fieldPrice.setValue(Env.ZERO);
		fieldPrice.setFont(posPanel.getFont());
		fieldPrice.setName(priceLabel.getName());
		fieldPrice.setPreferredSize(new Dimension(100, 50));
		fieldPrice.setMinimumSize(new Dimension(100, 50));
		fieldPrice.addActionListener(this);
		if (!posPanel.isModifyPrice())
			fieldPrice.setReadWrite(false);
		else {
			fieldPrice.addActionListener(posPanel.getUserPinListener());
			fieldPrice.addActionListener(this);
		}
		priceLabel.setLabelFor(fieldPrice);

		buttonPanel.add(fieldPrice, new GridBagConstraints(9, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		CLabel discountPercentageLabel = new CLabel(Msg.translate(Env.getCtx(), "Discount"));
		discountPercentageLabel.setDisplayedMnemonic(KeyEvent.VK_T);
		buttonPanel.add(discountPercentageLabel, new GridBagConstraints(10, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		fieldDiscountPercentage = new VNumber();
		fieldDiscountPercentage.setValue(Env.ZERO);
		fieldDiscountPercentage.addActionListener(this);
		fieldDiscountPercentage.setName(discountPercentageLabel.getName());
		fieldDiscountPercentage.setFont(posPanel.getFont());
		fieldDiscountPercentage.setPreferredSize(new Dimension(100, 50));
		fieldDiscountPercentage.setMinimumSize(new Dimension(100, 50));
		if (!posPanel.isModifyPrice())
			fieldDiscountPercentage.setReadWrite(false);
		else {
			fieldDiscountPercentage.addActionListener(posPanel.getUserPinListener());
			fieldDiscountPercentage.addActionListener(this);
		}
		discountPercentageLabel.setLabelFor(fieldDiscountPercentage);


		buttonPanel.add(fieldDiscountPercentage, new GridBagConstraints(11, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(topPadding, leftPadding, bottonPadding, rightPadding), 0, 0));

		
		add(buttonPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1
				,GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(11, 0, 0, 0), 0, 0));

		changeStatus(false);
	}

	/**
	 * 	Distribute actions
	 *	@param actionEvent event
	 */
	public void actionPerformed (ActionEvent actionEvent) {
		try {
			if (actionEvent.getSource().equals(buttonUp)) {
				posPanel.moveUp();
				return;
			} else if (actionEvent.getSource().equals(buttonDown)) {
				posPanel.moveDown();
				return;
			}
			if (actionEvent.getSource().equals(buttonMinus)) {
				fieldQuantity.setValue(((BigDecimal) fieldQuantity.getValue()).subtract(Env.ONE));
			} else if (actionEvent.getSource().equals(buttonPlus)) {
				fieldQuantity.setValue(((BigDecimal) fieldQuantity.getValue()).add(Env.ONE));
			} else if (actionEvent.getSource().equals(buttonDelete)) {
				if (posPanel.isUserPinValid()) {
					posPanel.deleteLine(posPanel.getC_OrderLine_ID());
					fieldQuantity.setValue(0.0);
					fieldPrice.setValue(0.0);
					fieldDiscountPercentage.setValue(0.0);
					posPanel.refreshPanel();
				}
			}
			if (actionEvent.getSource().equals(buttonScales))
			{
				posPanel.hideKeyboard();
				posPanel.showScales();
				posPanel.getScalesTimer().restart();
			}
			BigDecimal quantity = (BigDecimal) fieldQuantity.getValue();
			if ((posPanel.getQty().compareTo(quantity) != 0 && fieldQuantity.hasChanged()
			&& (actionEvent.getSource().equals(fieldQuantity) || actionEvent.getSource().equals(buttonPlus) || actionEvent.getSource().equals(buttonMinus))))
			{
				posPanel.setQuantity((BigDecimal) fieldQuantity.getValue());
				posPanel.setPrice((BigDecimal) fieldPrice.getValue());
				posPanel.setDiscountPercentage((BigDecimal) fieldDiscountPercentage.getValue());
				posPanel.changeViewQuantityPanel();
				posPanel.updateLineTable();
			}

			if (actionEvent.getSource().equals(fieldDiscountPercentage) && actionEvent.getActionCommand().toString().equals("KeyEvent")
			||  actionEvent.getSource().equals(fieldPrice) && actionEvent.getActionCommand().toString().equals("KeyEvent")) {
				BigDecimal discountPercentage = (BigDecimal) fieldDiscountPercentage.getValue();
				BigDecimal price =  (BigDecimal) fieldPrice.getValue();
				if (discountPercentage.signum() < 0)
					throw new AdempierePOSException("@Discount@ @Error@");
				if (price.signum() < 0)
					throw new AdempierePOSException("@Price@ @Error@");

				if (posPanel.isUserPinValid()) {
					if ((posPanel.getQty().compareTo(quantity) != 0 && fieldQuantity.hasChanged()
							&& (actionEvent.getSource().equals(fieldQuantity) || actionEvent.getSource().equals(buttonPlus) || actionEvent.getSource().equals(buttonMinus)))
							|| (posPanel.getPrice().compareTo(price) != 0 && fieldPrice.hasChanged() && actionEvent.getSource().equals(fieldPrice))
							|| (posPanel.getDiscountPercentage().compareTo(discountPercentage) != 0 && fieldDiscountPercentage.hasChanged() && actionEvent.getSource().equals(fieldDiscountPercentage))) {
						posPanel.setQuantity((BigDecimal) fieldQuantity.getValue());
						posPanel.setPrice((BigDecimal) fieldPrice.getValue());
						posPanel.setDiscountPercentage((BigDecimal) fieldDiscountPercentage.getValue());
						posPanel.changeViewQuantityPanel();
						posPanel.updateLineTable();
					}
				}
				return;
			}

		} catch (AdempiereException exception) {
			ADialog.error(posPanel.getWindowNo(), this, exception.getLocalizedMessage());
		}
	}

	@Override
	public void refreshPanel() {
		if(posPanel.hasLines()){
			buttonDown.setEnabled(true);
			buttonUp.setEnabled(true);
			
			// Only enable buttons if status==(drafted or in progress)
			if(posPanel.getM_Order().getDocStatus().compareToIgnoreCase(MOrder.STATUS_Drafted)==0 || 
			   posPanel.getM_Order().getDocStatus().compareToIgnoreCase(MOrder.STATUS_InProgress)==0 ){
				buttonDelete.setEnabled(true);
				buttonPlus.setEnabled(true);
				buttonMinus.setEnabled(true);

				if (posPanel.isPresentElectronicScales())
					buttonScales.setEnabled(true);
				else
					buttonScales.setVisible(false);

				fieldQuantity.setEnabled(true);
				fieldPrice.setEnabled(true);
				fieldDiscountPercentage.setEnabled(true);
			}else {
				buttonDelete.setEnabled(false);
				buttonPlus.setEnabled(false);
				buttonMinus.setEnabled(false);

				if (posPanel.isPresentElectronicScales())
					buttonScales.setEnabled(false);
				else
					buttonScales.setVisible(false);

				fieldPrice.setEnabled(false);
				fieldQuantity.setEnabled(false);
				fieldDiscountPercentage.setEnabled(false);
			}
		} else {
			buttonDown.setEnabled(false);
			buttonUp.setEnabled(false);
		}
	}

	@Override
	public void moveUp() {
	}

	@Override
	public void moveDown() {
	}

	@Override
	public String validatePayment() {
		return null;
	}

	/**
	 * Change Status 
	 * @param status
	 */
	public void changeStatus(boolean status) {
		fieldQuantity.setEnabled(status);
		fieldPrice.setEnabled(status);
		fieldDiscountPercentage.setEnabled(status);
		buttonDelete.setEnabled(status);
		buttonPlus.setEnabled(status);
		buttonMinus.setEnabled(status);
	}
	
	@Override
	public void changeViewPanel() {
		if(posPanel.getQty().compareTo(Env.ZERO) == 0)
			changeStatus(false);
		else
			changeStatus(true);
		fieldQuantity.setValue(posPanel.getQty().doubleValue());
		fieldPrice.setValue(posPanel.getPrice().doubleValue());
		fieldDiscountPercentage.setValue(posPanel.getDiscountPercentage().doubleValue());
	}

	public void resetPanel() {
		fieldQuantity.setValue(0);
		fieldPrice.setValue(0);
		fieldDiscountPercentage.setValue(0);
		buttonDown.setEnabled(false);
		buttonUp.setEnabled(false);
		buttonDelete.setEnabled(false);
		buttonPlus.setEnabled(false);
		buttonMinus.setEnabled(false);
		if (posPanel.isPresentElectronicScales())
			buttonScales.setEnabled(false);
		fieldPrice.setEnabled(false);
		fieldQuantity.setEnabled(false);
		fieldDiscountPercentage.setEnabled(false);
	}

	public void setQuantity(Object value) {
		fieldQuantity.setValue((BigDecimal) value);
		fieldQuantity.requestFocus();
	}
}