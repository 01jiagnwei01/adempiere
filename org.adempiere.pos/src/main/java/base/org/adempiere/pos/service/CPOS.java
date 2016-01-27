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
 * Contributor(s): Yamel Senih www.erpcya.com                                 *
 *****************************************************************************/
package org.adempiere.pos.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pos.AdempierePOSException;
import org.adempiere.util.ProcessUtil;
import org.compiere.model.I_AD_User;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MCurrency;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MLocator;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MOrderTax;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MPOS;
import org.compiere.model.MPOSKey;
import org.compiere.model.MPayment;
import org.compiere.model.MPaymentProcessor;
import org.compiere.model.MPriceList;
import org.compiere.model.MPriceListVersion;
import org.compiere.model.MProduct;
import org.compiere.model.MProductPrice;
import org.compiere.model.MSequence;
import org.compiere.model.MTax;
import org.compiere.model.MUser;
import org.compiere.model.MWarehouse;
import org.compiere.model.MWarehousePrice;
import org.compiere.model.Query;
import org.compiere.model.X_C_Order;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.compiere.util.ValueNamePair;

/**
 * @author Mario Calderon, mario.calderon@westfalia-it.com, Systemhaus Westfalia, http://www.westfalia-it.com
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 * @author victor.perez@e-evolution.com , http://www.e-evolution.com
 */
public class CPOS {
	
	/**
	 * 
	 * *** Constructor ***
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 */
	public CPOS() {
		ctx = Env.getCtx();
	}
	
	/**	POS Configuration		*/
	private MPOS 				entityPOS;
	/**	Current Order			*/
	private MOrder 				currentOrder;
	/** Sequence Doc 			*/
	private MSequence 			documentSequence;
	/**	The Business Partner	*/
	private MBPartner 			partner;
	/**	Price List Version		*/
	private int 				priceListVersionId;
	/** Context					*/
	protected Properties 		ctx = Env.getCtx();
	/**	Today's (login) date	*/
	private Timestamp 			today = Env.getContextAsDate(ctx, "#Date");
	/**	Order List				*/
	private ArrayList<Integer>  orderList;
	/**	Order List Position		*/
	private int 				recordPosition;
	/**	Is Payment Completed	*/
	private boolean 			isToPrint;
	/**	Logger					*/
	private CLogger 			log = CLogger.getCLogger(getClass());
	/**	Quantity Ordered		*/
	private BigDecimal 			quantity = BigDecimal.ZERO;
	/** Price Limit 			*/
	private BigDecimal 			priceLimit = BigDecimal.ZERO;
	/**	Price					*/
	private BigDecimal 			price = BigDecimal.ZERO;
	/**	Price List				*/
	private BigDecimal 			priceList = BigDecimal.ZERO;
	/**	% Discount		    	*/
	private BigDecimal 			discountPercentage = BigDecimal.ZERO;
	
	
	/**
	 * 	Set MPOS
	 * @param salesRepId
	 * @return true if found/set
	 */
	public void setPOS(int salesRepId) {
		//List<MPOS> poss = getPOSs(p_SalesRep_ID);
		List<MPOS> poss = getPOSByOrganization(Env.getAD_Org_ID(getCtx()));
		//
		if (poss.size() == 0) {
			throw new AdempierePOSException("@NoPOSForUser@");
		} else if (poss.size() == 1) {
			entityPOS = poss.get(0);
		}
	}	//	setMPOS
	
	/**
	 * Set POS
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @param pos
	 * @return void
	 */
	public void setM_POS(MPOS pos) {
		entityPOS = pos;
	}
	
	/**
	 * Validate if is Order Completed
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return boolean
	 */
	public boolean isCompleted() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return currentOrder.isProcessed()
				&& X_C_Order.DOCSTATUS_Completed.equals(currentOrder.getDocStatus());
	}
	
	/**
	 * Get Sequence
	 * @return
	 * @return int
	 */
	public int getAD_Sequence_ID() {
		if (entityPOS.getC_DocType_ID() > 0)
			return entityPOS.getC_DocType().getDocNoSequence_ID();
		else
			throw new AdempierePOSException("@C_POS_ID@ @C_DocType_ID @NotFound@");
	}
	
	/**
	 * Get Organization
	 * @return
	 * @return int
	 */
	public int getAD_Org_ID() {
		return entityPOS.getAD_Org_ID();
	}


	/**
	 * Validate if is voided
	 * @return
	 * @return boolean
	 */
	public boolean isClosed() {
		if(!hasOrder()) {
			return false;
		}
		//
		return X_C_Order.DOCSTATUS_Closed.equals(currentOrder.getDocStatus());
	}

	/**
	 * Validate if is voided
	 * @return
	 * @return boolean
	 */
	public boolean isVoided() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return X_C_Order.DOCSTATUS_Voided.equals(currentOrder.getDocStatus());
	}
	
	/**
	 * Validate if is drafted
	 * @return
	 * @return boolean
	 */
	public boolean isDrafted() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return !isCompleted() 
				&& !isVoided() 
				&& X_C_Order.DOCSTATUS_Drafted.equals(currentOrder.getDocStatus());
	}
	
	/**
	 * Validate if is "In Process"}
	 * @return
	 * @return boolean
	 */
	public boolean isInProgress() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return !isCompleted() 
				&& !isVoided() 
				&& X_C_Order.DOCSTATUS_InProgress.equals(currentOrder.getDocStatus());
	}
	
	/**
	 * Validate if has lines
	 * @return
	 * @return boolean
	 */
	public boolean hasLines() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return currentOrder.getLines().length > 0;
	}
	
	/**
	 * Validate if is POS Order
	 * @return
	 * @return boolean
	 */
	public boolean isPOSOrder() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return MOrder.DocSubTypeSO_POS.equals(getDocSubTypeSO());
	}
	
	/**
	 * Validate if is Credit Order
	 * @return
	 * @return boolean
	 */
	public boolean isCreditOrder() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return MOrder.DocSubTypeSO_OnCredit.equals(getDocSubTypeSO());
	}
	
	/**
	 * Validate if is Standard Order
	 * @return
	 * @return boolean
	 */
	public boolean isStandardOrder() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return MOrder.DocSubTypeSO_Standard.equals(getDocSubTypeSO());
	}
	
	/**
	 * Validate if is Prepay Order
	 * @return
	 * @return boolean
	 */
	public boolean isPrepayOrder() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return MOrder.DocSubTypeSO_Prepay.equals(getDocSubTypeSO());
	}
	
	/**
	 * Validate if is Standard Order
	 * @return
	 * @return boolean
	 */
	public boolean isWarehouseOrder() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return MOrder.DocSubTypeSO_Warehouse.equals(getDocSubTypeSO());
	}

	/**
	 * Validate if is Return Material
	 * @return
	 * @return boolean
	 */
	public boolean isReturnMaterial() {
		if(!hasOrder()) {
			return false;
		}
		//
		return MOrder.DocSubTypeSO_RMA.equals(getDocSubTypeSO());
	}
	
	/**
	 * Valid date if is invoiced
	 * @return
	 * @return boolean
	 */
	public boolean isInvoiced() {
		if(!hasOrder()) {
			return false;
		}
		//	
		int[] invoice_IDs = MInvoice.getAllIDs(MInvoice.Table_Name, MInvoice.COLUMNNAME_C_Order_ID + "=" + currentOrder.getC_Order_ID(), null);
		boolean orderInvoiced = false;
		if (invoice_IDs!=null && invoice_IDs.length>0 && invoice_IDs[0]>0) {
			MInvoice invoice = new MInvoice(getCtx(), invoice_IDs[0], null);
			orderInvoiced = invoice.getDocStatus().equalsIgnoreCase(MInvoice.DOCSTATUS_Completed);
		}
	
		return currentOrder.isInvoiced() || orderInvoiced;
	}
	
	/**
	 * Validate if is delivered
	 * @return
	 * @return boolean
	 */
	public boolean isDelivered() {
		if(!hasOrder()) {
			return false;
		}
		//	
		return currentOrder.isDelivered();
	}
	
	/**
	 * Get Document Sub Type SO
	 * @return
	 * @return String
	 */
	private String getDocSubTypeSO() {
		//	
		MDocType docType = MDocType.get(getCtx(), getC_DocType_ID());
		if(docType != null) {
			if(docType.getDocSubTypeSO() != null) {
				return docType.getDocSubTypeSO();
			}
		}
		//	
		return "";
	}
	
	/**
	 * Get Document Type from Order
	 * @return
	 * @return int
	 */
	public int getC_DocType_ID() {
		if(!hasOrder()) {
			return 0;
		}
		//	
		if(isCompleted()
				|| isVoided()) {
			return currentOrder.getC_DocType_ID();
		} else {
			return currentOrder.getC_DocTypeTarget_ID();
		}
	}
	
	/**
	 * Validate if is to print invoice
	 * @return
	 * @return boolean
	 */
	public boolean isToPrint() {
		return isToPrint;
	}
	
	/**
	 * Get Current Order
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return MOrder
	 */
	public MOrder getM_Order() {
		return currentOrder;
	}
	
	/**
	 * Has Order
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return boolean
	 */
	public boolean hasOrder() {
		return currentOrder != null
				&& currentOrder.getC_Order_ID() != 0;
	}
	
	/**
	 * Has Business Partner
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return boolean
	 */
	public boolean hasBPartner() {
		return partner != null;
	}
	
	/**
	 * Compare BP Name
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @param name
	 * @return
	 * @return boolean
	 */
	public boolean compareBPName(String name) {
		return partner.getName().equals(name);
	}
	
	/**
	 * 	Get BPartner
	 *	@return C_BPartner_ID
	 */
	public int getC_BPartner_ID () {
		if (hasBPartner())
			return partner.getC_BPartner_ID();
		return 0;
	}	//	getC_BPartner_ID
	
	
	/**
	 * Get Business Partner Name
	 * @return
	 * @return String
	 */
	public String getBPName() {
		if (hasBPartner())
			return partner.getName();
		return null;
	}
	
	/**
	 * Get Currency Identifier
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getC_Currency_ID() {
		if (hasBPartner()
				&& currentOrder != null) {
			return currentOrder.getC_Currency_ID();
		}
		//	Default
		return 0;
	}
	
	/**
	 * 	Get BPartner Contact
	 *	@return AD_User_ID
	 */
	public int getAD_User_ID () {
		return Env.getAD_User_ID(Env.getCtx());
	}	//	getAD_User_ID
	
	/**
	 * Get Auto Delay
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getAutoLogoutDelay() {
		return entityPOS.getAutoLogoutDelay();
	}
	
	/**
	 * Get Sales Rep. Name
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return String
	 */
	public String getSalesRepName() {
		MUser salesRep = MUser.get(ctx);
		if(salesRep == null) {
			return null;
		}
		//	Default Return
		return salesRep.getName();
	}
	
	/**
	 * Get Sales Representative
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getSalesRep_ID() {
		return entityPOS.getSalesRep_ID();
	}
	
	/**
	 * Get POS Configuration
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return MPOS
	 */
	public MPOS getM_POS() {
		return entityPOS;
	}
	
	/**
	 * Get POS Name
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return String
	 */
	public String getPOSName() {
		return entityPOS.getName();
	}
	
	/**
	 * Get POS Identifier
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getC_POS_ID() {
		return entityPOS.getC_POS_ID();
	}


	/**
	 * Is Enable Product Lookup
	 * @return
     */
	public boolean isEnableProductLookup() {
		return entityPOS.isEnableProductLookup();
	}

	/**
	 * Is POS Required PIN
	 * @return
     */
	public boolean isRequiredPIN(){
		return entityPOS.isPOSRequiredPIN();
	}

	/**
	 * 	New Order
	 *  @param partnerId
	 */
	public void newOrder(int partnerId) {
		log.info( "PosPanel.newOrder");
		currentOrder = null;
		int docTypeId = entityPOS.getC_DocType_ID();
		//	Create Order
		createOrder(partnerId, docTypeId);
		//	
		reloadOrder();
	}	//	newOrder
	
	/**
	 * Set Custom Document Type
	 * @param docTypeTargetId
	 * @return void
	 */
	public void setC_DocType_ID(int docTypeTargetId) {
		//	Valid if has a Order
		if(!isDrafted())
			return;
		//	Set Document Type
		currentOrder.setC_DocTypeTarget_ID(docTypeTargetId);
		//	Set Sequenced No
		String value = DB.getDocumentNo(getC_DocType_ID(), null, false, currentOrder);
		if (value != null) {
			currentOrder.setDocumentNo(value);
		}
		currentOrder.saveEx();
	}
	
	
	/**
	 * Get/create Order
	 *	@param partnerId Business Partner
	 *	@param docTypeTargetId ID of document type
	 */
	private void createOrder(int partnerId, int docTypeTargetId) {
		int orderId = getFreeC_Order_ID();
		//	Change Values for new Order
		if(orderId > 0) {
			currentOrder = new MOrder(Env.getCtx(), orderId, null);
			currentOrder.setDateOrdered(getToday());
			currentOrder.setDateAcct(getToday());
			currentOrder.setDatePromised(getToday());
		} else {
			currentOrder = new MOrder(Env.getCtx(), 0, null);
		}
		currentOrder.setAD_Org_ID(entityPOS.getAD_Org_ID());
		currentOrder.setIsSOTrx(true);
		currentOrder.setM_PriceList_ID(entityPOS.getM_PriceList_ID());
		currentOrder.setC_POS_ID(entityPOS.getC_POS_ID());
		currentOrder.setM_Warehouse_ID(entityPOS.getM_Warehouse_ID());
		if (docTypeTargetId != 0) {
			currentOrder.setC_DocTypeTarget_ID(docTypeTargetId);
		} else {
			currentOrder.setC_DocTypeTarget_ID(MOrder.DocSubTypeSO_OnCredit);
		}
		//	Set BPartner
		setC_BPartner_ID(partnerId);
		//	Add if is new
		if(orderId < 0) {
			//	Add To List
			orderList.add(currentOrder.getC_Order_ID());
		}
		//  Add record
		reloadIndex(currentOrder.getC_Order_ID());
	} // PosOrderModel
	
	/**
	 * Find a free order and reuse
	 * @return
	 * @return int
	 */
	private int getFreeC_Order_ID() {
		return DB.getSQLValue(null, "SELECT o.C_Order_ID "
				+ "FROM C_Order o "
				+ "WHERE o.DocStatus = 'DR' "
				+ "AND o.C_POS_ID = ? "
				+ "AND o.SalesRep_ID = ? "
				+ "AND NOT EXISTS(SELECT 1 "
				+ "					FROM C_OrderLine ol "
				+ "					WHERE ol.C_Order_ID = o.C_Order_ID) "
				+ "ORDER BY o.Updated", 
				getC_POS_ID(), getSalesRep_ID());
	}
	
	/**
	 * Is BPartner Standard 
	 * @return boolean
	 */
	public boolean isBPartnerStandard() {
		int partnerId = currentOrder != null ? currentOrder.getC_BPartner_ID() : 0 ;
		if(entityPOS.getC_BPartnerCashTrx_ID() == partnerId)
			return true;
		else
			return false;
	}
	
	/**
	 * 	Set BPartner, update price list and locations
	 *  Configuration of Business Partner has priority over POS configuration
	 *	@param p_C_BPartner_ID id
	 */
	
	/**
	 * set BPartner and save
	 */
	public void setC_BPartner_ID(int partnerId) {
		//	Valid if has a Order
		if(isCompleted()
				|| isVoided())
			return;
		log.fine( "CPOS.setC_BPartner_ID=" + partnerId);
		boolean isSamePOSPartner = false;
		//	Validate BPartner
		if (partnerId == 0) {
			isSamePOSPartner = true;
			partnerId = entityPOS.getC_BPartnerCashTrx_ID();
		}
		//	Get BPartner
		partner = MBPartner.get(ctx, partnerId);
		if (partner == null || partner.get_ID() == 0) {
			throw new AdempierePOSException("POS.NoBPartnerForOrder");
		} else {
			log.info("CPOS.SetC_BPartner_ID -" + partner);
			currentOrder.setBPartner(partner);
			//	
			MBPartnerLocation [] partnerLocations = partner.getLocations(true);
			if(partnerLocations.length > 0) {
				for(MBPartnerLocation partnerLocation : partnerLocations) {
					if(partnerLocation.isBillTo())
						currentOrder.setBill_Location_ID(partnerLocation.getC_BPartner_Location_ID());
					if(partnerLocation.isShipTo())
						currentOrder.setShip_Location_ID(partnerLocation.getC_BPartner_Location_ID());
				}				
			}
			//	Validate Same BPartner
			if(isSamePOSPartner) {
				if(currentOrder.getPaymentRule()==null)
					currentOrder.setPaymentRule(MOrder.PAYMENTRULE_Cash);
			}
			//	Set Sales Representative
			currentOrder.setSalesRep_ID(entityPOS.getSalesRep_ID());
			//	Save Header
			currentOrder.saveEx();
			//	Load Price List Version
			MPriceListVersion priceListVersion = loadPriceListVersion(currentOrder.getM_PriceList_ID());
			MProductPrice[] productPrices = priceListVersion.getProductPrice("AND EXISTS("
					+ "SELECT 1 "
					+ "FROM C_OrderLine ol "
					+ "WHERE ol.C_Order_ID = " + currentOrder.getC_Order_ID() + " "
					+ "AND ol.M_Product_ID = M_ProductPrice.M_Product_ID)");
			//	Update Lines
			MOrderLine[] lines = currentOrder.getLines();
			//	Delete if not exist in price list
			for (MOrderLine line : lines) {
				//	Verify if exist
				if(existInPriceList(line.getM_Product_ID(), productPrices)) {
					line.setC_BPartner_ID(partner.getC_BPartner_ID());
					line.setC_BPartner_Location_ID(currentOrder.getC_BPartner_Location_ID());
					line.setPrice();
					line.setTax();
					line.saveEx();
				} else {
					line.deleteEx(true);
				}
			}
		}
	}
	
	/**
	 * Verify if exist in price list
	 * @param productId
	 * @param productPrices
	 * @return boolean
	 */
	private boolean existInPriceList(int productId, MProductPrice[] productPrices) {
		for(MProductPrice productPrice : productPrices) {
			if(productId == productPrice.getM_Product_ID()) {
				return true;
			}
		}
		//	Default Return
		return false;
	}
	
	/**
	 * 	Get POSs for specific Sales Rep or all
	 *	@return array of POS
	 */
	public List<MPOS> getPOSs (int salesRepId) {
		String searchBy = MPOS.COLUMNNAME_SalesRep_ID;
		int id = salesRepId;
		if (salesRepId == 100) {
			searchBy = MPOS.COLUMNNAME_AD_Client_ID;
			id = Env.getAD_Client_ID(ctx);
		}
		return MPOS.getAll(ctx, searchBy, id , null);
	}	//	getPOSs


	/**
	 * 	Get POSs for specific Sales Rep or all
	 *	@return array of POS
	 */
	public List<MPOS> getPOSByOrganization (int orgId) {
		return MPOS.getByOrganization(ctx, orgId, null);
	}

	/**************************************************************************
	 * 	Get Today's date
	 *	@return date
	 */
	public Timestamp getToday() {
		return today;
	}	//	getToday
	
	/**
	 * @param orderId
	 */
	public void setOrder(int orderId) {
		currentOrder = new MOrder(ctx, orderId, null);
		if (orderId != 0) {
			loadPriceListVersion(currentOrder.getM_PriceList_ID());
		}
		//	
		reloadOrder();
	}
	
	/**
	 * Update Line
	 * @param orderLineId
	 * @param qtyOrdered
	 * @param priceLimit
	 * @param priceEntered
	 * @param priceList
     * @param discountPercentage
	 * @return
     */
	public BigDecimal [] updateLine(int orderLineId,
									BigDecimal qtyOrdered,
									BigDecimal priceLimit,
									BigDecimal priceEntered,
									BigDecimal priceList, BigDecimal discountPercentage) {
		//	Valid if has a Order
		if(!isDrafted())
			return null;
		//	
		MOrderLine[] mOrderLines = currentOrder.getLines("AND C_OrderLine_ID = " + orderLineId, "Line");
		BigDecimal lineNetAmt = Env.ZERO;
		BigDecimal taxRate = Env.ZERO;
		BigDecimal grandTotal = Env.ZERO;
		
		//	Search Line
		for(MOrderLine orderLine : mOrderLines) {
			//	Valid No changes
			if(qtyOrdered.compareTo(orderLine.getQtyOrdered()) == 0
			&& priceEntered.compareTo(orderLine.getPriceEntered()) == 0
			&& discountPercentage.compareTo(orderLine.getDiscount()) == 0 ) {
				return null;
			}

			if (discountPercentage.compareTo(orderLine.getDiscount()) != 0) {
				BigDecimal discountAmount = orderLine.getPriceList().multiply(discountPercentage.divide(Env.ONEHUNDRED));
				priceEntered = orderLine.getPriceList().subtract(discountAmount);
			}

			orderLine.setPrice(priceEntered);
			orderLine.setQty(qtyOrdered);
			orderLine.setTax();
			orderLine.saveEx();
			//	Set Values for Grand Total
			lineNetAmt = orderLine.getLineNetAmt();
			taxRate = MTax.get(ctx, orderLine.getC_Tax_ID()).getRate();
			if(taxRate == null) {
				taxRate = Env.ZERO;
			} else {
				taxRate = taxRate
						.divide(Env.ONEHUNDRED);
			}
			//	Calculate Total
			grandTotal = lineNetAmt
						.add(lineNetAmt
								.multiply(taxRate));
		}
		//	Return Value
		return new BigDecimal[]{lineNetAmt, taxRate, grandTotal};
	}

	/**
	 * Create new Line
	 * @param product
	 * @param qtyOrdered
	 * @param warehousePrice
     * @return
     */
	public MOrderLine createLine(MProduct product, BigDecimal qtyOrdered, MWarehousePrice warehousePrice) {
		//	Valid Complete
		if (!isDrafted())
			return null;
		// catch Exceptions at order.getLines()
		MOrderLine[] lines = currentOrder.getLines(true, "Line");
		for (MOrderLine line : lines) {
			if (line.getM_Product_ID() == product.getM_Product_ID()) {
				//increase qty
				BigDecimal currentQty = line.getQtyEntered();
				BigDecimal currentPrice = line.getPriceEntered();
				BigDecimal totalQty = currentQty.add(qtyOrdered);
				line.setQty(totalQty);
				line.setPrice(currentPrice); //	sets List/limit
				line.saveEx();
				return line;
			}
		}
        //create new line
		MOrderLine line = new MOrderLine(currentOrder);
		line.setProduct(product);
		line.setQty(qtyOrdered);
		//	
		line.setPrice(); //	sets List/limit
		if ( warehousePrice.getPriceStd().signum() > 0 ) {
			line.setPriceLimit(warehousePrice.getPriceLimit());
			line.setPrice(warehousePrice.getPriceStd());
			line.setPriceList(warehousePrice.getPriceList());
			setPriceLimit(warehousePrice.getPriceLimit());
			//setPrice(warehousePrice.getPriceStd());
			setPriceList(warehousePrice.getPriceList());
			BigDecimal percentageDiscount = line.getDiscount();
			setDiscountPercentage(percentageDiscount);
		}
		//	Save Line
		line.saveEx();
		return line;
			
	} //	createLine

	/**
	 *  Save Line
	 * @param productId
	 * @param qtyOrdered
     * @return
     */
	public String add(int productId, BigDecimal qtyOrdered) {
		String errorMessage = null;
		try {
			MProduct product = MProduct.get(ctx, productId);
			if (product == null)
				return "@No@ @InfoProduct@";

			MWarehousePrice warehousePrice = MWarehousePrice.get(product,getM_PriceList_Version_ID(),getM_Warehouse_ID(),null);
			if (warehousePrice == null)
					throw new AdempiereException("@Price@ @NotFound@");

			//	Validate if exists a order
			if (hasOrder()) {
				createLine(product, qtyOrdered, warehousePrice);
			} else {
				return "@POS.MustCreateOrder@";
			}
		} catch (Exception e) {
			errorMessage = e.getMessage();
		}
		//	
		return errorMessage;
	} //	saveLine
	
	/**
	 * 	Call Order void process 
	 *  Only if Order is "Drafted", "In Progress" or "Completed"
	 * 
	 *  @return true if order voided; false otherwise
	 */
	private boolean voidOrder() {
		if (!(currentOrder.getDocStatus().equals(MOrder.STATUS_Drafted)
				|| currentOrder.getDocStatus().equals(DocAction.STATUS_InProgress)
				|| currentOrder.getDocStatus().equals(DocAction.STATUS_Completed)))
			return false;
		
		// Standard way of voiding an order
		currentOrder.setDocAction(MOrder.DOCACTION_Void);
		if (currentOrder.processIt(MOrder.DOCACTION_Void) ) {
			currentOrder.setDocAction(MOrder.DOCACTION_None);
			currentOrder.setDocStatus(MOrder.STATUS_Voided);
			currentOrder.saveEx();
			return true;
		} else {
			return false;
		}
	} // cancelOrder
	
	/**
	 * Execute deleting an order
	 * If the order is in drafted status -> ask to delete it
	 * If the order is in completed status -> ask to void it it
	 * Otherwise, it must be done outside this class.
	 */
	public String cancelOrder() {
		String errorMsg = null;
		try {
			//	Get Index
			int currentIndex = orderList.indexOf(currentOrder.getC_Order_ID());
			if (!hasOrder()) {
				throw new AdempierePOSException("@POS.MustCreateOrder@");
			} else if (!isCompleted()) {
				//	Delete Order
				currentOrder.deleteEx(true);
			} else if (isCompleted()) {	
				voidOrder();
			} else {
				throw new AdempierePOSException("@POS.OrderIsNotProcessed@");
			}
			//	Remove from List
			if(currentIndex >= 0) {
				orderList.remove(currentIndex);
			}
			//	
			currentOrder = null;
			//	Change to Next
			if(hasRecord()){
				if(isFirstRecord()) {
					firstRecord();
				} else if(isLastRecord()) {
					lastRecord();
				} else {
					previousRecord();
				}
			}
		} catch(Exception e) {
			errorMsg = e.getMessage();
		}
		//	Default Return
		return errorMsg;
	} // cancelOrder
	
	/** 
	 * Delete one order line
	 * To erase one line from order
	 * 
	 */
	public void deleteLine (int orderLineId) {
		if ( orderLineId != -1 && currentOrder != null ) {
			for ( MOrderLine line : currentOrder.getLines(true, I_C_OrderLine.COLUMNNAME_M_Product_ID) ) {
				if ( line.getC_OrderLine_ID() == orderLineId ) {
					line.deleteEx(true);	
				}
			}
		}
	} //	deleteLine

	/**
	 * Get Data List Order
	 */
	public void listOrder() {
		String sql = new String("SELECT o.C_Order_ID "
					+ "FROM C_Order o "
					+ "WHERE o.IsSOTrx='Y' "
					+ "AND o.Processed = 'N' "
					+ "AND o.AD_Client_ID = ? "
					+ "AND o.C_POS_ID = ? "
					+ "AND o.SalesRep_ID = ? "
					+ "ORDER BY o.Updated");
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		orderList = new ArrayList<Integer>();
		try {
			//	Set Parameter
			preparedStatement= DB.prepareStatement(sql, null);
			preparedStatement.setInt (1, Env.getAD_Client_ID(Env.getCtx()));
			preparedStatement.setInt (2, getC_POS_ID());
			preparedStatement.setInt (3, getSalesRep_ID());
			//	Execute
			resultSet = preparedStatement.executeQuery();
			//	Add to List
			while(resultSet.next()){
				orderList.add(resultSet.getInt(1));
			}
		} catch(Exception e) {
			log.severe("SubOrder.listOrder: " + e + " -> " + sql);
		} finally {
			DB.close(resultSet);
			DB.close(preparedStatement);
		}
		//	Seek Position
		if(hasRecord())
			recordPosition = orderList.size() -1;
		else 
			recordPosition = -1;
	}
	
	/**
	 * Verify if has record in list
	 * @return
	 * @return boolean
	 */
	public boolean hasRecord(){
		return !orderList.isEmpty();
	}
	
	/**
	 * Verify if is first record in list
	 * @return
	 * @return boolean
	 */
	public boolean isFirstRecord() {
		return recordPosition == 0;
	}
	
	/**
	 * Verify if is last record in list
	 * @return
	 * @return boolean
	 */
	public boolean isLastRecord() {
		return recordPosition == orderList.size() - 1;
	}
	
	/**
	 * Previous Record Order
	 */
	public void previousRecord() {
		if(recordPosition > 0) {
			setOrder(orderList.get(--recordPosition));
		}
	}

	/**
	 * Next Record Order
	 */
	public void nextRecord() {
		if(recordPosition < orderList.size() - 1) {
			setOrder(orderList.get(++recordPosition));
		}
	}
	
	/**
	 * Reload List Index
	 * @param orderId
	 * @return void
	 */
	public void reloadIndex(int orderId) {
		int position = orderList.indexOf(orderId);
		if(position >= 0) {
			recordPosition = position;
		}
	}
	
	/**
	 * Seek to last record
	 * @return void
	 */
	public void lastRecord() {
		recordPosition = orderList.size();
		if(recordPosition != 0) {
			--recordPosition;
		}
	}
	
	/**
	 * Seek to first record
	 * @return void
	 */
	public void firstRecord() {
		recordPosition = orderList.size();
		if(recordPosition != 0) {
			recordPosition = 0;
		}
	}
	
	/**
	 * Process Order
	 * For status "Drafted" or "In Progress": process order
	 * For status "Completed": do nothing as it can be pre payment or payment on credit
	 * @param trxName
	 * @param isPrepayment
	 * @param isPaid
	 * @return true if order processed or pre payment/on credit; otherwise false
	 * 
	 */
	public boolean processOrder(String trxName, boolean isPrepayment, boolean isPaid) {
		//Returning orderCompleted to check for order completeness
		boolean orderCompleted = false;
		// check if order completed OK
		if (!isCompleted()) {	//	Complete Order
			//	Replace
			if(trxName == null) {
				trxName = currentOrder.get_TrxName();
			} else {
				currentOrder.set_TrxName(trxName);
			}
			isToPrint = true;
			//	Get value for Standard Order
			if(isPrepayment) {
				//	Set Document Type
				currentOrder.setC_DocTypeTarget_ID(MOrder.DocSubTypeSO_Standard);
				isToPrint = false;
			}
			
			//	Force Delivery for POS not for Standard Order
			if(!currentOrder.getC_DocTypeTarget().getDocSubTypeSO()
				.equals(MOrder.DocSubTypeSO_Standard)) {				
				currentOrder.setDeliveryRule(X_C_Order.DELIVERYRULE_Force);
				currentOrder.setInvoiceRule(X_C_Order.INVOICERULE_AfterDelivery);
			}
				
			currentOrder.setDocAction(DocAction.ACTION_Complete);
			if (currentOrder.processIt(DocAction.ACTION_Complete) ) {
				currentOrder.saveEx();
				orderCompleted = true;
			} else {
				log.info( "Process Order FAILED " + currentOrder.getProcessMsg());
			}
		} else {	//	Default nothing
			orderCompleted = isCompleted();
			isToPrint = false;
		}
		
		//	Validate for Invoice and Shipment generation (not for Standard Orders)
		if(isPaid && !getDocSubTypeSO().equals(MOrder.DocSubTypeSO_Standard)) {	
			if(!isDelivered())
				generateShipment(trxName);

			if(!isInvoiced() && !getDocSubTypeSO().equals(MOrder.DocSubTypeSO_Warehouse)) {
				generateInvoice(trxName);
				isToPrint = true;				
			}
			//	
			orderCompleted = true;
		}
		return orderCompleted;
	}	// processOrder
	
	/**
	 * Generate Shipment
	 * @param trxName
	 * @return void
	 */
	private void generateShipment(String trxName) {
		int processId = 199;  // HARDCODED    M_InOut_Generate - org.compiere.process.InOutGenerate
		MPInstance instance = new MPInstance(Env.getCtx(), processId, 0);
		if (!instance.save()) {
			throw new AdempiereException("ProcessNoInstance");
		}
		//	Insert Values
		DB.executeUpdateEx("INSERT INTO T_SELECTION(AD_PINSTANCE_ID, T_SELECTION_ID) Values(?, ?)", 
				new Object[]{instance.getAD_PInstance_ID(), getC_Order_ID()}, trxName);
		//	Add Lines
		ProcessInfo processInfo = new ProcessInfo ("VInOutGen", processId);
		processInfo.setAD_PInstance_ID (instance.getAD_PInstance_ID());
		processInfo.setClassName("org.compiere.process.InOutGenerate");

		//	Add Is Selection
		MPInstancePara para = new MPInstancePara(instance, 10);
		para.setParameter("Selection", "Y");
		if (!para.save()) {
			String msg = "No Selection Parameter added";  //  not translated
			log.log(Level.SEVERE, msg);
			throw new AdempiereException(msg);
		}
		//	Add Warehouse
		para = new MPInstancePara(instance, 20);
		para.setParameter("M_Warehouse_ID", getM_Warehouse_ID());
		if (!para.save()) {
			String msg = "No Selection Parameter added";  //  not translated
			log.log(Level.SEVERE, msg);
			throw new AdempiereException(msg);
		}
		//	Create Trx
		Trx trx = Trx.get(trxName, false);
		//	Start Process
		ProcessUtil.startJavaProcess(Env.getCtx(), processInfo, trx, false);
		if(processInfo.isError()) {
			throw new AdempiereException(processInfo.getSummary());
		}
	}
	
	/**
	 * Generate Invoice
	 * @param trxName
	 * @return void
	 */
	private void generateInvoice(String trxName) {
		int processId = 134;  // HARDCODED    C_InvoiceCreate - org.compiere.process.InvoiceGenerate
		MPInstance instance = new MPInstance(Env.getCtx(), processId, 0);
		if (!instance.save()) {
			throw new AdempiereException("ProcessNoInstance");
		}
		//	Insert Values
		DB.executeUpdateEx("INSERT INTO T_SELECTION(AD_PINSTANCE_ID, T_SELECTION_ID) Values(?, ?)", 
				new Object[]{instance.getAD_PInstance_ID(), getC_Order_ID()}, trxName);
		//	Add Lines
		ProcessInfo processInfo = new ProcessInfo ("", processId);
		processInfo.setAD_PInstance_ID (instance.getAD_PInstance_ID());
		processInfo.setClassName("org.compiere.process.InvoiceGenerate");

		//	Add Is Selection
		MPInstancePara para = new MPInstancePara(instance, 10);
		para.setParameter("Selection", "Y");
		if (!para.save()) {
			String msg = "No Selection Parameter added";  //  not translated
			log.log(Level.SEVERE, msg);
			throw new AdempiereException(msg);
		}
		//	For Document Action
		para = new MPInstancePara(instance, 20);
		para.setParameter("DocAction", "CO");
		if (!para.save())
		{
			String msg = "No DocAction Parameter added";  //  not translated
			log.log(Level.SEVERE, msg);
			throw new AdempiereException(msg);
		}
		//	Create Trx
		Trx trx = Trx.get(trxName, false);
		//	Start Process
		ProcessUtil.startJavaProcess(Env.getCtx(), processInfo, trx, false);
		if(processInfo.isError()) {
			throw new AdempiereException(processInfo.getSummary());
		}
	}
	
	/**
	 * Get Process Message
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return String
	 */
	public String getProcessMsg() {
		return currentOrder.getProcessMsg();
	}

	/**
	 * Set Payment Term and save orders
	 * @param paymentTermId
	 * @return void
	 */
	public void setC_PaymentTerm_ID(int paymentTermId) {
		if(paymentTermId != 0
				&& hasOrder()
				&& !isCompleted()
				&& !isVoided()) {
			currentOrder.setC_PaymentTerm_ID(paymentTermId);
		}
	}
	
	/**
	 * Get Payment term from order
	 * @return
	 * @return int
	 */
	public int getC_PaymentTerm_ID() {
		if(hasOrder()) {
			return currentOrder.getC_PaymentTerm_ID();
		}
		//	Default
		return 0;
	}

	/**
	 * 	Gets Tax Amt from Order
	 * 
	 */
	public BigDecimal getTaxAmt() {
		BigDecimal taxAmt = Env.ZERO;
		for (MOrderTax tax : currentOrder.getTaxes(true)) {
			taxAmt = taxAmt.add(tax.getTaxAmt());
		}
		return taxAmt;
	}
	
	/**
	 * 	Gets Subtotal from Order
	 * 
	 */
	public BigDecimal getTotalLines() {
		return currentOrder.getGrandTotal().subtract(getTaxAmt());
	}
	
	/**
	 * Get Grand Total for current Order
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return BigDecimal
	 */
	public BigDecimal getGrandTotal() {
		return currentOrder.getGrandTotal();
	}
	
	/**
	 * Get Document No
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return String
	 */
	public String getDocumentNo() {
		return currentOrder.getDocumentNo();
	}
	
	/**
	 * Get Open Amount
	 * @return
	 * @return BigDecimal
	 */
	public BigDecimal getOpenAmt() {
		BigDecimal received = getPaidAmt();	
		return currentOrder.getGrandTotal().subtract(received);
	}
	
	/**
	 * Verify if is Paid
	 * @return
	 * @return boolean
	 */
	public boolean isPaid() {
		return getOpenAmt().doubleValue() == 0;
	}
	
	/**
	 * 	Gets Amount Paid from Order
	 * 	It takes the allocated amounts, including Credit Notes
	 */
	public BigDecimal getPaidAmt() {
		String sql = "SELECT sum(amount) FROM C_AllocationLine al " +
				"INNER JOIN C_AllocationHdr alh on (al.C_AllocationHdr_ID=alh.C_AllocationHdr_ID) " +
				"WHERE (al.C_Invoice_ID = ? OR al.C_Order_ID = ?) AND alh.DocStatus IN ('CO','CL')";
		BigDecimal received = DB.getSQLValueBD(null, sql, currentOrder.getC_Invoice_ID(), currentOrder.getC_Order_ID());
		if ( received == null )
			received = Env.ZERO;
		
		sql = "SELECT sum(Amount) FROM C_CashLine WHERE C_Invoice_ID = ? ";
		BigDecimal cashLineAmount = DB.getSQLValueBD(null, sql, currentOrder.getC_Invoice_ID());
		if ( cashLineAmount != null )
			received = received.add(cashLineAmount);
		
		return received;
	}
	
	/**
	 * 	Load Order
	 */
	public void reloadOrder() {
		if (currentOrder == null) {
			
			if(recordPosition != -1
					&& recordPosition < orderList.size()) {
				setOrder(orderList.get(recordPosition));
			}
			//	
			return;
		}
		currentOrder.load(currentOrder.get_TrxName());
		currentOrder.getLines(true, "");
		partner = MBPartner.get(getCtx(), currentOrder.getC_BPartner_ID());
	}
	
	/**
	 * 	Get M_PriceList_Version_ID.
	 * 	Set Currency
	 *	@return plv
	 */
	public int getM_PriceList_Version_ID() {
		return priceListVersionId;
	}	//	getM_PriceList_Version_ID
	
	/**
	 * Load Price List Version from Price List
	 * @param priceListId
	 * @return
	 * @return MPriceListVersion
	 */
	protected MPriceListVersion loadPriceListVersion(int priceListId) {
		priceListVersionId = 0;
		MPriceList priceList = MPriceList.get(ctx, priceListId, null);
		//
		MPriceListVersion priceListVersion = priceList.getPriceListVersion (getToday());
		if (priceListVersion != null
				&& priceListVersion.getM_PriceList_Version_ID() != 0) {
			priceListVersionId = priceListVersion.getM_PriceList_Version_ID();
		}
		//	Default Return
		return priceListVersion;
	}
	
	/**
	 * Get Warehouse Identifier
	 * @return
	 * @return int
	 */
	public int getM_Warehouse_ID() {
		return entityPOS.getM_Warehouse_ID();
	}
	
	/**
	 * Valid Locator
	 * @return
	 * @return String
	 */
	public void validLocator() {
		MWarehouse warehouse = MWarehouse.get(ctx, getM_Warehouse_ID());
		MLocator[] locators = warehouse.getLocators(true);
		for (MLocator mLocator : locators) {
			if (mLocator.isDefault()) {
				return ;
			}
		}

		throw new AdempierePOSException("@M_Locator_ID@ @default@ "
				+ "@not.found@ @M_Warehouse_ID@: " 
				+ warehouse.getName());
	}
	
	/**
	 * Get Warehouse Name
	 * @return
	 * @return String
	 */
	public String getWarehouseName() {
		if(getM_Warehouse_ID() > 0) {
			MWarehouse.get(ctx, getM_Warehouse_ID()).getName();
		}
		//	Default
		return "";
	}
	
	/**
	 * Get Document Type Name
	 * @return
	 * @return String
	 */
	public String getDocumentTypeName() {
		if(hasOrder()) {
			MDocType m_DocType = MDocType.get(getCtx(), currentOrder.getC_DocTypeTarget_ID());
			if(m_DocType != null) {
				return m_DocType.getName();
			}
		}
		//	Default None
		return "";
	}
	
	/**
	 * Get Currency Symbol
	 * @return
	 * @return String
	 */
	public String getCurSymbol() {
		int currencyId = getC_Currency_ID();
		if(currencyId > 0) {
			MCurrency currency = MCurrency.get(getCtx(), currencyId);
			if(currency != null) {
				return currency.getCurSymbol();
			}
		}
		//	Default
		return "";
	}
	
	/**
	 * Duplicated from MPayment
	 * 	Get Accepted Credit Cards for amount
	 *	@param amt trx amount
	 *	@return credit cards
	 */
	public ValueNamePair[] getCreditCards (BigDecimal amt) {
		try {
			MPaymentProcessor[] paymentProcessors = MPaymentProcessor.find (Env.getCtx(), null, null,
					currentOrder.getAD_Client_ID (), currentOrder.getAD_Org_ID(), currentOrder.getC_Currency_ID (), amt, currentOrder.get_TrxName());
			//
			HashMap<String,ValueNamePair> map = new HashMap<String,ValueNamePair>(); //	to eliminate duplicates
			for (int i = 0; i < paymentProcessors.length; i++) {
				if (paymentProcessors[i].isAcceptAMEX ())
					map.put (MPayment.CREDITCARDTYPE_Amex, getCreditCardPair (MPayment.CREDITCARDTYPE_Amex));
				if (paymentProcessors[i].isAcceptDiners ())
					map.put (MPayment.CREDITCARDTYPE_Diners, getCreditCardPair (MPayment.CREDITCARDTYPE_Diners));
				if (paymentProcessors[i].isAcceptDiscover ())
					map.put (MPayment.CREDITCARDTYPE_Discover, getCreditCardPair (MPayment.CREDITCARDTYPE_Discover));
				if (paymentProcessors[i].isAcceptMC ())
					map.put (MPayment.CREDITCARDTYPE_MasterCard, getCreditCardPair (MPayment.CREDITCARDTYPE_MasterCard));
				if (paymentProcessors[i].isAcceptCorporate ())
					map.put (MPayment.CREDITCARDTYPE_PurchaseCard, getCreditCardPair (MPayment.CREDITCARDTYPE_PurchaseCard));
				if (paymentProcessors[i].isAcceptVisa ())
					map.put (MPayment.CREDITCARDTYPE_Visa, getCreditCardPair (MPayment.CREDITCARDTYPE_Visa));
			} //	for all payment processors
			//
			ValueNamePair[] retValue = new ValueNamePair[map.size ()];
			map.values ().toArray (retValue);
			log.fine("getCreditCards - #" + retValue.length + " - Processors=" + paymentProcessors.length);
			return retValue;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}	//	getCreditCards


	/**
	 * 	Get Credit Notes
	 * 
	 */
	public ValueNamePair[] getCreditNotes () {
		try {
			String whereClause = "C_BPartner_ID = ? "
					+ "AND IsPaid ='N' "
					+ "AND EXISTS(SELECT 1 "
					+ "				FROM C_DocType dt "
					+ "				WHERE dt.C_DocType_ID = C_Invoice.C_DocType_ID "
					+ "				AND dt.DocBaseType ='ARC'"
					+ ")";
			List<MInvoice> invoiceList = new Query(Env.getCtx(), MInvoice.Table_Name, whereClause, null)
				.setParameters(currentOrder.getC_BPartner_ID())
				.list();
			//
			HashMap<String,ValueNamePair> map = new HashMap<String,ValueNamePair>(); //	to eliminate duplicates
			ValueNamePair valueNamePair;
			for (MInvoice invoice : invoiceList) {
				Integer id = invoice.getC_Invoice_ID();
				valueNamePair = new ValueNamePair(id.toString(), invoice.getDocumentNo() + " " + invoice.getOpenAmt().toString());
					map.put (invoice.getDocumentNo(), valueNamePair);
			} //	for all payment processors
			//
			ValueNamePair[] retValue = new ValueNamePair[map.size ()];
			map.values ().toArray (retValue);
			return retValue;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}	//	getCreditCards
	
	/**
	 * 
	 * Duplicated from MPayment
	 * 	Get Type and name pair
	 *	@param CreditCardType credit card Type
	 *	@return pair
	 */
	private ValueNamePair getCreditCardPair (String CreditCardType) {
		return new ValueNamePair (CreditCardType, getCreditCardName(CreditCardType));
	}	//	getCreditCardPair

	/**
	 * 
	 * Duplicated from MPayment
	 *	Get Name of Credit Card
	 * 	@param CreditCardType credit card type
	 *	@return Name
	 */
	public String getCreditCardName(String CreditCardType) {
		if (CreditCardType == null)
			return "--";
		else if (MPayment.CREDITCARDTYPE_MasterCard.equals(CreditCardType))
			return "MasterCard";
		else if (MPayment.CREDITCARDTYPE_Visa.equals(CreditCardType))
			return "Visa";
		else if (MPayment.CREDITCARDTYPE_Amex.equals(CreditCardType))
			return "Amex";
		else if (MPayment.CREDITCARDTYPE_ATM.equals(CreditCardType))
			return "ATM";
		else if (MPayment.CREDITCARDTYPE_Diners.equals(CreditCardType))
			return "Diners";
		else if (MPayment.CREDITCARDTYPE_Discover.equals(CreditCardType))
			return "Discover";
		else if (MPayment.CREDITCARDTYPE_PurchaseCard.equals(CreditCardType))
			return "PurchaseCard";
		return "?" + CreditCardType + "?";
	}	//	getCreditCardName
	
	/**
	 * Get Context
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> Aug 31, 2015, 8:23:54 PM
	 * @return
	 * @return Properties
	 */
	public Properties getCtx() {
		return ctx;
	}
	
	/**
	 * Get POS Key Layout Identifier
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getOSKeyLayout_ID() {
		if(entityPOS != null) {
			return entityPOS.getOSK_KeyLayout_ID();
		}
		//	Default Return
		return 0;
	}
	
	/**
	 * Get Key Layout
	 * @return
	 * @return int
	 */
	public int getC_POSKeyLayout_ID() {
		if(entityPOS != null) {
			return entityPOS.getC_POSKeyLayout_ID();
		}
		//	Default Return
		return 0;
	}
	
	/**
	 * Verify if can modify price
	 * @return
	 * @return boolean
	 */
	public boolean isModifyPrice() {
		return entityPOS.isModifyPrice();
	}
	
	/**
	 * Get Order Identifier
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
	 * @return
	 * @return int
	 */
	public int getC_Order_ID() {
		int m_C_Order_ID = 0;
		if(hasOrder()) {
			m_C_Order_ID = currentOrder.getC_Order_ID();
		}
		//	Default
		return m_C_Order_ID;
	}
	
	/**
	 * Save Current Next Sequence
	 * @param trxName
	 * @return void
	 */
	public void saveNextSeq(String trxName){
		int next = documentSequence.getCurrentNext() + documentSequence.getIncrementNo();
		documentSequence.setCurrentNext(next);
		documentSequence.saveEx(trxName);
	}
	
	/**
	 * Get Sequence Document
	 * @param trxName
	 * @return String
	 */
	public String getSequenceDoc(String trxName){
		documentSequence = new MSequence(Env.getCtx(), getAD_Sequence_ID() , trxName);
		return documentSequence.getPrefix() + documentSequence.getCurrentNext();
	}
	
	/**
	 * Set Purchase Order Reference 
	 * @param documentNo
	 * @return void
	 */
	public void setPOReference(String documentNo) {
		String trxName = currentOrder.get_TrxName();
		Trx trx = Trx.get(trxName, true);
		currentOrder.setPOReference(documentNo);
		currentOrder.saveEx(trx.getTrxName());
		trx.close();
		
	}

	/**
	 * Get Quantity of Product
	 * @return quantity
	 */
	public BigDecimal getQty() {
		return quantity;
	}

	/**
	 * Set Quantity of Product
	 * @param qty
	 */
	public void setQuantity(BigDecimal qty) {
		this.quantity = qty;
	}

	/**
	 * Get Price of Product
	 * @return price
	 */
	public BigDecimal getPrice() {
		return this.price;
	}

	/**
	 * Get Price of Product
	 * @return price
	 */
	public BigDecimal getDiscountPercentage() {
		return this.discountPercentage;
	}


	public void setDiscountPercentage(BigDecimal discountPercentage)
	{
		this.discountPercentage = discountPercentage;
	}

	public void setPriceLimit(BigDecimal priceLimit)
	{
		this.priceLimit = priceLimit;
	}

	public BigDecimal getPriceLimit() {
		return this.priceLimit;
	}

	public BigDecimal getPriceList() {
		return this.priceList;
	}

	public void setPriceList(BigDecimal priceList) {
		this.priceList = priceList;
	}

	/**
	 * Set Price of Product
	 * @param price
	 */
	public void setPrice(BigDecimal price)
	{
		this.price = price;
	}

	public void setPrice (MWarehousePrice warehousePrice)
	{
		setPriceLimit(warehousePrice.getPriceLimit());
		setPrice(warehousePrice.getPriceStd());
		setPriceList(warehousePrice.getPriceList());
	}

	public String getElectronicScales()
	{
		return entityPOS.getElectronicScales();
	}

	public String getMeasureRequestCode()
	{
		return entityPOS.getMeasureRequestCode();
	}

	public boolean isPresentElectronicScales()
	{
		if (getElectronicScales() != null && getElectronicScales().length() > 0)
			return true;
		else
			return false;
	}

	public boolean IsShowLineControl() {
		return true;
	}

	/**
	 * get if POS using a virtual Keyboard
	 * @return
	 */
	public boolean isVirtualKeyboard()
	{
		if (getOSKeyLayout_ID() > 0)
			return true;

		return false;
	}

	/**
	 * Get Product Image
	 * Right now, it is only possible a two-stage lookup.
	 * A general lookup has to be implemented, where more than 2 stages are considered.
	 * @param productId
	 * @param posKeyLayoutId
	 * @return int
	 */
	public int getProductImageId(int productId , int posKeyLayoutId) {
		int imageId = 0;

		//	Valid Product
		if(productId == 0)
			return imageId;

		//	Get POS Key
		int m_C_POSKey_ID = DB.getSQLValue(null, "SELECT pk.C_POSKey_ID "
				+ "FROM C_POSKey pk "
				+ "WHERE pk.C_POSKeyLayout_ID = ? "
				+ "AND pk.M_Product_ID = ? "
				+ "AND pk.IsActive = 'Y'", posKeyLayoutId , productId);
		
		if(m_C_POSKey_ID > 0) {
			//	Valid POS Key
			MPOSKey key =  new MPOSKey(ctx, m_C_POSKey_ID, null);
			imageId = key.getAD_Image_ID();
		}
		else  {
			//	No record has been found for a product in the current Key Layout. Try it in the Subkey Layout.
			m_C_POSKey_ID = DB.getSQLValue(null, "SELECT pk2.C_POSKey_ID "
					+ "FROM C_POSKey pk1 "
					+ "INNER JOIN C_POSKey pk2 ON pk1.subkeylayout_id=pk2.c_poskeylayout_id AND pk1.subkeylayout_id IS NOT NULL "
					+ "WHERE pk2.M_Product_ID = ? "
					+ "AND pk1.IsActive = 'Y' AND pk2.IsActive = 'Y'", productId);
			//	Valid POS Key
			if(m_C_POSKey_ID > 0) {
				MPOSKey key =  new MPOSKey(ctx, m_C_POSKey_ID, null);
				imageId = key.getAD_Image_ID();
			}
		}
		return imageId;
	}

	public int getM_Product_ID(int orderLineId)
	{
		return DB.getSQLValue(null, "SELECT ol.M_Product_ID "
				+ "FROM C_OrderLine ol "
				+ "WHERE ol.C_OrderLine_ID = ?", orderLineId);

	}

	/**
	 * Validate User PIN
	 * @param userPin
     */
	public boolean isValidUserPin(char[] userPin)
	{
		MUser user = MUser.get(getCtx() ,getAD_User_ID());
		I_AD_User supervior = user.getSupervisor();
		if (supervior == null || supervior.getAD_User_ID() <= 0)
			throw new AdempierePOSException("@Supervisor@ @NotFound@");
		if (supervior.getUserPIN() == null || supervior.getUserPIN().isEmpty())
			throw new AdempierePOSException("@Supervisor@ " + supervior.getName() + " @NotFound@ @UserPIN@");

		char[] correctPassword = supervior.getUserPIN().toCharArray();
		boolean isCorrect = true;
		if (userPin.length != correctPassword.length) {
			isCorrect = false;
		} else {
			for (int i = 0; i < userPin.length; i++) {
				if (userPin[i] != correctPassword[i]) {
					isCorrect = false;
				}
			}
		}
		//Zero out the password.
		for (int i = 0; i < correctPassword.length; i++) {
			correctPassword[i] = 0;
		}

		return isCorrect;
	}
}
