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
 * Copyright (C) 2003-2013 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor: Yamel Senih www.erpcya.com                                    *
 * Contributor: Mario Calderon www.westfalia-it.com                           *
 *****************************************************************************/
package org.adempiere.pos;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import org.adempiere.pipo.exception.POSaveFailedException;
import org.adempiere.pos.service.Collect;
import org.adempiere.pos.service.CollectDetail;
import org.adempiere.pos.service.I_POSPanel;
import org.compiere.apps.ADialog;
import org.compiere.apps.AEnv;
import org.compiere.apps.AppsAction;
import org.compiere.apps.ConfirmPanel;
import org.compiere.model.X_C_Payment;
import org.compiere.swing.CButton;
import org.compiere.swing.CCheckBox;
import org.compiere.swing.CDialog;
import org.compiere.swing.CLabel;
import org.compiere.swing.CPanel;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;

/**
 * @author Mario Calderon, mario.calderon@westfalia-it.com, Systemhaus Westfalia, http://www.westfalia-it.com
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 * @author Victor Perez <victor.perez@e-evolution.com>,  eEvolution http://www.e-evolution.com
 */
public class VCollect extends Collect
		implements ActionListener, I_POSPanel, VetoableChangeListener {
	
	/**
	 * From POS
	 * *** Constructor ***
	 * @param posPanel
	 */
	public VCollect(VPOS posPanel) {
		super(posPanel.getCtx(), posPanel.getM_Order(), posPanel.getM_POS());
		pos = posPanel;
		ctx = pos.getCtx();
		collectRowNo = 0;
		init();
	}

	public VCollect load (VPOS posPanel)
	{
		//	Instance Collects
		load(posPanel.getCtx() , posPanel.getM_Order() , posPanel.getM_POS());
		centerPanel.removeAll();
		collectRowNo = 0;
		calculatePanelData();
		refreshPanel();
		return this;
	}

	/**
	 * Init Dialog
	 * @return void
	 */
	public void init() {
		log.info("");
		try {
			jbInit();
			refreshPanel();
		} catch (Exception e) {
			log.log(Level.SEVERE, "", e);
		}
	} // init

	/**	View Panel			*/
	private VPOS 			pos;
	//private CDialog 		dialog;
	private CPanel			dialog;
	private BorderLayout 	mainLayout;
	private GridBagLayout 	parameterLayout;
	private CPanel 			mainPanel;
	private CPanel 			parameterPanel;
	private JScrollPane 	scrollPane;
	private CPanel 			centerPanel;
	
	/**	Fields Summary		*/
	//private CLabel 			labelGrandTotal;
	//private CLabel 			fieldGrandTotal;
	private CLabel 			labelPayAmt;
	private CLabel 			fieldPayAmt;
	private CLabel 			labelOpenAmt;
	private CLabel 			fieldOpenAmt;
	private CLabel 			labelReturnAmt;
	private CLabel 			fieldReturnAmt;
	private CCheckBox 		fieldIsPrePayOrder;
	//private CCheckBox 		fieldIsCreditOrder;
	//private VLookup 		fPaymentTerm;
	
	/**	Action				*/
	private CButton 		buttonPlus;
	private CButton 		buttonCancel;
	private CButton 		buttonOk;
	
	/**	Generic Values		*/
	private boolean 		isProcessed;
	private Properties 		ctx;
	private int 			collectRowNo;
	
	/**	Log					*/
	private CLogger 		log = CLogger.getCLogger(VCollect.class);
	/**	Default Width		*/
	private final int		SUMMARY_FIELD_WIDTH 	= 200;
	/**	Default Height		*/
	private final int		SUMMARY_FIELD_HEIGHT 	= 30;

	/**
	 * Instance Frame and fill fields
	 * @throws Exception
	 * @return void
	 */
	private void jbInit() throws Exception {
		//	Instance Dialog
		//dialog = new CDialog(Env.getWindow(pos.getWindowNo()), Msg.translate(ctx, "Payment"), true);
		dialog = new CPanel();
		dialog.setName(Msg.translate(ctx, "Payment"));
		//
		mainLayout = new BorderLayout();
		parameterLayout = new GridBagLayout();
		mainPanel = new CPanel();
		parameterPanel = new CPanel();

		centerPanel = new CPanel();
		//centerPanel.setMinimumSize(new Dimension(200, 300));
		//centerPanel.setSize(new Dimension(200, 300));
		scrollPane = new JScrollPane();
		scrollPane.setPreferredSize(new Dimension(700, 700));

		mainPanel.setLayout(mainLayout);
		parameterPanel.setLayout(parameterLayout);
		centerPanel.setLayout(parameterLayout);
		mainPanel.add(scrollPane);
		scrollPane.getViewport().add(centerPanel);
		
		// Add Grand Total
		//labelGrandTotal = new CLabel(Msg.translate(ctx, "GrandTotal") + ":");
		//labelGrandTotal.setFont(pos.getPlainFont());
		//	
		//fieldGrandTotal = new CLabel();
		//fieldGrandTotal.setFont(pos.getBigFont());
		//	
		//fieldGrandTotal.setPreferredSize(new Dimension(SUMMARY_FIELD_WIDTH, SUMMARY_FIELD_HEIGHT));
		
		//	Add Payment Amount
		labelPayAmt = new CLabel(Msg.translate(ctx, "PayAmt") + ":");
		labelPayAmt.setFont(pos.getPlainFont());
		//
		fieldPayAmt = new CLabel();
		fieldPayAmt.setFont(pos.getFont());
		fieldPayAmt.setPreferredSize(new Dimension(SUMMARY_FIELD_WIDTH, SUMMARY_FIELD_HEIGHT));
		
		//	Add Payment Amount
		labelOpenAmt = new CLabel(Msg.translate(ctx, "OpenAmt") + ":");
		labelOpenAmt.setFont(pos.getPlainFont());
		//	
		fieldOpenAmt = new CLabel();
		fieldOpenAmt.setFont(pos.getFont());
		fieldOpenAmt.setPreferredSize(new Dimension(SUMMARY_FIELD_WIDTH, SUMMARY_FIELD_HEIGHT));
		
		//	For Returned Amount
		labelReturnAmt = new CLabel(Msg.translate(ctx, "AmountReturned") + ":");
		labelReturnAmt.setFont(pos.getPlainFont());
		//	
		fieldReturnAmt = new CLabel();
		fieldReturnAmt.setFont(pos.getFont());
		fieldReturnAmt.setPreferredSize(new Dimension(SUMMARY_FIELD_WIDTH, SUMMARY_FIELD_HEIGHT));
		
		//	Add Is Pre-Payment
		fieldIsPrePayOrder = new CCheckBox(Msg.translate(ctx, "IsPrePayment"));
		fieldIsPrePayOrder.setFont(pos.getPlainFont());
		
		//	Add Is Credit Order
		//fieldIsCreditOrder = new CCheckBox(Msg.translate(ctx, "IsCreditSale"));
		//fieldIsCreditOrder.setFont(pos.getPlainFont());
		
		// Completed Standard Order: only prepayment possible 
		if(pos.getTotalLines().compareTo(Env.ZERO)==1 &&
		   pos.isCompleted() &&
		   pos.isStandardOrder()) {
			fieldIsPrePayOrder.setEnabled(false);
			//fieldIsCreditOrder.setEnabled(false);
			fieldIsPrePayOrder.setSelected(true);
		}
		// Not completed Order 
		else if(pos.getTotalLines().compareTo(Env.ZERO)==1 &&
				!pos.isCompleted()) {
			if(pos.isStandardOrder() /*|| pos.isWarehouseOrder()*/) {
				 // Standard Order or Warehouse Order: no Credit Order, no prepayment
				fieldIsPrePayOrder.setEnabled(false);
				fieldIsPrePayOrder.setSelected(false);
				//fieldIsCreditOrder.setEnabled(false);
			}
			else {		
				fieldIsPrePayOrder.setEnabled(true);
				//fieldIsCreditOrder.setEnabled(true);
			}
		}
		else {
			fieldIsPrePayOrder.setEnabled(false);
			//fieldIsCreditOrder.setEnabled(false);
			if(pos.isCompleted() &&
				pos.getM_Order().isInvoiced()  &&
				pos.getOpenAmt().compareTo(Env.ZERO)==1) {
				//fieldIsCreditOrder.setSelected(true);
			}
		}
//		int AD_Column_ID = 2187;        //  C_Order.C_PaymentTerm_ID
//		MLookup lookup = MLookupFactory.get(Env.getCtx(), 0, 0, AD_Column_ID, DisplayType.TableDir);
//		fPaymentTerm = new VLookup("C_PaymentTerm_ID", true, false, true, lookup);
//		((VComboBox)fPaymentTerm.getCombo()).setRenderer(new POSLookupTableDirCellRenderer(posPanel.getFont()));
//		fPaymentTerm.setPreferredSize(new Dimension(200, posPanel.getFieldLenght()));
//		((VComboBox)fPaymentTerm.getCombo()).setFont(posPanel.getFont());
//		fPaymentTerm.addVetoableChangeListener(this);
		//	Add Plus Button
		AppsAction action = new AppsAction("Plus", KeyStroke.getKeyStroke(KeyEvent.VK_F2, Event.F2), false);
		action.setDelegate(this);
		buttonPlus = (CButton)action.getButton();
		buttonPlus.setPreferredSize(new Dimension(pos.getButtonSize(), pos.getButtonSize()));
		buttonPlus.setFocusable(false);
		//	For Confirm Panel Button
		buttonCancel = ConfirmPanel.createCancelButton(true);
		buttonCancel.setPreferredSize(new Dimension(pos.getButtonSize(), pos.getButtonSize()));
		buttonOk = ConfirmPanel.createOKButton(true);
		buttonOk.setPreferredSize(new Dimension(pos.getButtonSize(), pos.getButtonSize()));

		//parameterPanel.add(labelGrandTotal, new GridBagConstraints(1, 0, 1, 1, 0.0,0.0,
		//		GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		//parameterPanel.add(fieldGrandTotal, new GridBagConstraints(2, 0, 1, 1, 0.0,0.0,
		//		GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(labelPayAmt, new GridBagConstraints(1, 0, 1, 1, 0.0,	0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(fieldPayAmt, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(labelOpenAmt, new GridBagConstraints(1, 1, 1, 1, 0.0,	0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(fieldOpenAmt, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(labelReturnAmt, new GridBagConstraints(1, 2, 1, 1, 0.0,0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(fieldReturnAmt, new GridBagConstraints(2, 2, 1, 1, 0.0,0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,new Insets(0, 0, 0, 0), 0, 0));

		parameterPanel.add(fieldIsPrePayOrder, new GridBagConstraints(1, 3, 1, 1, 0.0,0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		//parameterPanel.add(fieldIsCreditOrder, new GridBagConstraints(2, 4, 1, 1, 0.0,0.0,
		//		GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		parameterPanel.add(buttonPlus, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));

		parameterPanel.add(buttonCancel, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));

		parameterPanel.add(buttonOk, new GridBagConstraints(4, 3, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
		
//		parameterPanel.add(fPaymentTerm, new GridBagConstraints(2, 5, 1, 1, 0.0,0.0,
//				GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		//	Add Fields to Main Panel
		mainPanel.add(parameterPanel, BorderLayout.NORTH);

		//	Add Listeners
		fieldIsPrePayOrder.addActionListener(this);
		//fieldIsCreditOrder.addActionListener(this);
		buttonOk.addActionListener(this);
		buttonCancel.addActionListener(this);
		
		//	Add to Dialog
		//dialog.getContentPane().add(commandPanel, BorderLayout.SOUTH);
		//dialog.getContentPane().add(mainPanel, BorderLayout.CENTER);
		dialog.add(mainPanel, BorderLayout.CENTER);
	}

	/**
	 * Add new Collect
	 * @return void
	 */
	private void addCollectType() {
		//	
		String tenderType = X_C_Payment.TENDERTYPE_Cash;
		if(collectRowNo > 0) {
			tenderType = X_C_Payment.TENDERTYPE_CreditCard;
		}
		//	FR https://github.com/erpcya/AD-POS-WebUI/issues/7
		BigDecimal balance = getBalance();
		if(balance.doubleValue() < 0)
			balance = Env.ZERO;
		//	
		VCollectDetail collectDetail = new VCollectDetail(this, tenderType, getBalance());
		//	Add Collect controller
		addCollect(collectDetail);
		// add parameter panel
		centerPanel.add(collectDetail.getPanel(), new GridBagConstraints(0, collectRowNo, 1, 1, 0.0, 0.0,
						GridBagConstraints.EAST, GridBagConstraints.NORTH, new Insets(5, 5, 5, 5), 0, 0));
		//	Repaint
		scrollPane.validate();
		scrollPane.repaint();
		//	Request Focus
		collectDetail.requestFocusInPayAmt();
		//	Add Count
		collectRowNo++;
		//	Calculate Data
		calculatePanelData();
	}

	/**
	 * Process Window
	 * @return
	 * @return String
	 */
	public String saveData() {
		String errorMsg = null;
		try {
			dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			Trx.run(new TrxRunnable() {
				public void run(String trxName) {
					if(pos.processOrder(trxName, isPrePayOrder(), getBalance().doubleValue() <= 0)) {
						processPayment(trxName, pos.getOpenAmt());
					} else {
						throw new POSaveFailedException(pos.getProcessMsg());
					}
				}
			});
		} catch (Exception e) {
			errorMsg = e.getLocalizedMessage();
		} finally {
			dialog.setCursor(Cursor.getDefaultCursor());
		}
		//	Default
		return errorMsg;
	}
	
	@Override
	public void actionPerformed(ActionEvent actionEvent) {
		isProcessed = false;
		//	Validate Event
		if (actionEvent.getSource().equals(buttonPlus)) {
			addCollectType();
		} else if (actionEvent.getSource().equals(buttonOk)) {	//	Process if is ok validation
			//	Validate before process
			String validResult = validatePanel();
			if(validResult == null) {
				validResult = saveData();
			}
			//	Show Dialog
			if(validResult != null) {
				ADialog.warn(pos.getWindowNo(), dialog, Msg.parseTranslation(ctx, validResult));
				return;
			}
			//	Set Processed
			isProcessed = true;
			if(!pos.isStandardOrder() /*&& !posPanel.isWarehouseOrder()*/ && pos.isToPrint()) {
				Trx.run(new TrxRunnable() {
					public void run(String trxName) {
						if (pos.getAD_Sequence_ID()!= 0) {
							String docno = pos.getSequenceDoc(trxName);
							String q = "Confirmar el número consecutivo "  + docno;
							if (ADialog.ask(pos.getWindowNo(), pos.getFrame(), q)) {
								pos.setPOReference(docno);
								pos.saveNextSeq(trxName);
							}
						}
					}
				});
				pos.printTicket();
			}
			//dialog.dispose();
			dialog.setVisible(false);
			pos.showKeyboard();
			return;
		} else if (actionEvent.getSource().equals(buttonCancel)) {	//	Nothing
			//dialog.dispose();
			dialog.setVisible(false);
			pos.showKeyboard();
			return;
		}
		/*else if(actionEvent.getSource().equals(fieldIsCreditOrder)) {	//	For Credit Order Checked
			//	Set to Controller
			setIsCreditOrder(fieldIsCreditOrder.isSelected());
			if(fieldIsCreditOrder.isSelected()) {
				removeAllCollectDetail();
			}
		}*/
		else if(actionEvent.getSource().equals(fieldIsPrePayOrder)) {	//	For Pre-Payment Order Checked
			//	Set to Controller
			setIsPrePayOrder(fieldIsPrePayOrder.isSelected());
		}
		//	Valid Panel
		changeViewPanel();
	}

	/**
	 * Remove All Collect Detail
	 * @return void
	 */
	public void removeAllCollectDetail() {
		centerPanel.removeAll();
		super.removeAllCollectDetail();
		//	Refresh View
		scrollPane.validate();
		scrollPane.repaint();
	}
	
	/**
	 * Remove Collect Detal From Child
	 * @param child
	 * @return void
	 */
	public void removeCollectDetail(VCollectDetail child) {
		Component comp = child.getPanel();
		removeCollect(child);
		centerPanel.remove(comp);
		scrollPane.validate();
		scrollPane.repaint();
	}
	
	/**
	 * Show Collect
	 * @return boolean
	 */
	public boolean showCollect() {
		//	Resize to Heigth
		Dimension screenSize = Env.getWindow(pos.getWindowNo()).getSize();
		//	Set static width
		screenSize.width = 500;
		dialog.setMinimumSize(screenSize);
		//dialog.pack();
		//	
		//AEnv.positionCenterScreen(dialog);
		dialog.setVisible(true);
		return isProcessed;
	}
	
	/**
	 * Get Keyboard
	 * @return POSKeyboard
	 */
	public POSKeyboard getKeyboard() {
		return pos.getKeyboard();
	}

	@Override
	public void refreshPanel() {
		calculatePanelData();
		changeViewPanel();
	}

	@Override
	public String validatePanel() {
		String errorMsg = null;
		if(!pos.hasOrder()) {	//	When is not created order
			errorMsg = "@POS.MustCreateOrder@";
		} else {
			if(!(pos.isStandardOrder() /*|| pos.isWarehouseOrder()*/))
				// No Check if Order is not Standard Order
				// TODO: Review why nor Warehouse Order
				errorMsg = validatePayment(pos.getOpenAmt());
		}
		//	
		return errorMsg;
	}

	@Override
	public void changeViewPanel() {
		//	Set Credit for Complete Documents
		boolean isCreditOpen = (pos.isCompleted()
				&& pos.getOpenAmt().doubleValue() > 0);
		//	Is Standard Order
		boolean isStandardOrder = pos.isStandardOrder();
		//	Set Credit Order
		setIsCreditOrder(isCreditOrder() 
				|| (isCreditOpen && !isStandardOrder));
		//	
		setIsPrePayOrder(isPrePayOrder()
				|| (isCreditOpen && isStandardOrder));
		//	Set Credit and Pre-Pay Order
		//fieldIsCreditOrder.setSelected(isCreditOrder());
		fieldIsPrePayOrder.setSelected(isPrePayOrder());
//		fPaymentTerm.setVisible(isCreditOrder());
		//	Verify complete order
		if(pos.isCompleted()) {
			//fieldIsCreditOrder.setEnabled(false);
			fieldIsPrePayOrder.setEnabled(false);
//			fPaymentTerm.setEnabled(false);
			buttonPlus.setEnabled(isCreditOpen);
			buttonOk.setEnabled(true);
		} else if(pos.isVoided()){
			//fieldIsCreditOrder.setEnabled(false);
			fieldIsPrePayOrder.setEnabled(false);
//			fPaymentTerm.setEnabled(false);
			buttonPlus.setEnabled(false);
			buttonOk.setEnabled(false);
		} else if(pos.isStandardOrder() /*|| pos.isWarehouseOrder()*/) {
			// Standard Order or Warehouse Order: no Credit Order, no prepayment
			fieldIsPrePayOrder.setEnabled(false);
			//fieldIsCreditOrder.setEnabled(false);
			buttonPlus.setEnabled(false);
		}
		else {
			//fieldIsCreditOrder.setEnabled(true);
			fieldIsPrePayOrder.setEnabled(true);
//			fPaymentTerm.setEnabled(true);
			buttonPlus.setEnabled(!isCreditOrder()
					|| isCreditOpen);
			buttonOk.setEnabled(true);
		}
	}
	
	/**
	 * Get Balance
	 * @return
	 * @return BigDecimal
	 */
	private BigDecimal getBalance() {
		BigDecimal m_PayAmt = getPayAmt();
		return pos.getOpenAmt().subtract(m_PayAmt);
	}
	
	/**
	 * Calculate and change data in panel
	 * @return void
	 */
	private void calculatePanelData() {
		//	Get from controller
		BigDecimal payAmt = getPayAmt();
		BigDecimal balance = getBalance();
		//	Change View
		String currencyISOCode = pos.getCurSymbol();
		//fieldGrandTotal.setText(currencyISOCode + " "
		//		+ pos.getNumberFormat().format(pos.getGrandTotal()));
		fieldPayAmt.setText(currencyISOCode + " "
				+ pos.getNumberFormat().format(payAmt.add(pos.getPaidAmt())));
		//	BR https://github.com/erpcya/AD-POS-WebUI/issues/6
		//	Show pretty Return Amount
		BigDecimal returnAmt = Env.ZERO;
		BigDecimal openAmt = Env.ZERO;
		if(balance.doubleValue() < 0) {
			returnAmt = balance.abs();
		} else if(balance.doubleValue() > 0){
			openAmt = balance;
		}
		//	Set Open Amount
		fieldOpenAmt.setText(currencyISOCode + " "
				+ pos.getNumberFormat().format(openAmt));
		//	Set Return Amount
		fieldReturnAmt.setText(currencyISOCode + " "
				+ pos.getNumberFormat().format(returnAmt));
		
//		fPaymentTerm.setValue(getC_PaymentTerm_ID());
	}

	@Override
	public void vetoableChange(PropertyChangeEvent e)
			throws PropertyVetoException {
		String name = e.getPropertyName();
		Object value = e.getNewValue();
		log.config(name + " = " + value);
		//	Verify Event
//		if(name.equals("C_PaymentTerm_ID")) {
//			int m_C_PaymentTerm_ID = ((Integer)(value != null? value: 0)).intValue();
//			setC_PaymentTerm_ID(m_C_PaymentTerm_ID);
//		}
	}

	@Override
	public void moveUp() {
	}

	@Override
	public void moveDown() {
	}

	public void hideCollect()
	{
		dialog.setVisible(false);
	}

	public CPanel getPanel()
	{
		return dialog;
	}

} // VCollect