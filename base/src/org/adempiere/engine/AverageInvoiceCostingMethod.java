/**
 * 
 */
package org.adempiere.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Properties;

import org.compiere.model.MAcctSchema;
import org.compiere.model.MCost;
import org.compiere.model.MCostDetail;
import org.compiere.model.MCostElement;
import org.compiere.model.MCostType;
import org.compiere.model.MDocType;
import org.compiere.model.MInOutLine;
import org.compiere.model.MLandedCostAllocation;
import org.compiere.model.MMatchInv;
import org.compiere.model.MMatchPO;
import org.compiere.model.MPeriod;
import org.compiere.model.MProduct;
import org.compiere.model.MTransaction;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.eevolution.model.MPPCostCollector;

/**
 * @author victor.perez@e-evolution.com, www.e-evolution.com
 * 
 */
public class AverageInvoiceCostingMethod extends AbstractCostingMethod
		implements ICostingMethod {

    /**
     * Constructor for Cost Engine
     * @param accountSchema
     * @param transaction
     * @param model
     * @param dimension
     * @param costThisLevel
     * @param costLowLevel
     * @param isSalesTransaction
     */
	public void setCostingMethod(MAcctSchema accountSchema, MTransaction transaction, IDocumentLine model,
                                 MCost dimension, BigDecimal costThisLevel,
                                 BigDecimal costLowLevel, Boolean isSalesTransaction) {
		this.accountSchema = accountSchema;
		this.transaction = transaction;
		this.dimension = dimension;
		this.costThisLevel = (costThisLevel == null ? Env.ZERO : costThisLevel);
		this.costLowLevel = (costLowLevel == null ? Env.ZERO : costLowLevel);
		this.isSalesTransaction = isSalesTransaction;
		this.model = model;
		this.costingLevel = MProduct.get(this.transaction.getCtx(), this.transaction.getM_Product_ID())
				.getCostingLevel(accountSchema, transaction.getAD_Org_ID());
		// find if this transaction exist into cost detail
		this.costDetail = MCostDetail.getByTransaction(this.model, this.transaction,
				this.accountSchema.getC_AcctSchema_ID(), this.dimension.getM_CostType_ID(),
				this.dimension.getM_CostElement_ID());
                log.fine("costDetail =" + this.costDetail);

        //Setting Accounting period status
        MDocType documentType = new MDocType(transaction.getCtx(), transaction.getDocumentLine().getC_DocType_ID(), transaction.get_TrxName());
        this.isOpenPeriod = MPeriod.isOpen(transaction.getCtx(), model.getDateAcct() , documentType.getDocBaseType(), transaction.getAD_Org_ID());

        //Setting Date Accounting based on Open Period
        if (this.isOpenPeriod)
            this.dateAccounting = model.getDateAcct();
        else if (model instanceof MLandedCostAllocation || model instanceof MMatchInv) {
                this.dateAccounting = ((MLandedCostAllocation) model).getC_InvoiceLine().getC_Invoice().getDateAcct();
        }
        else
            this.dateAccounting = null; // Is Necessary define that happen in this case when period is close

        this.movementQuantity = transaction.getMovementQty();
	}

	public void calculate() {
		// If model is reversal then no calculate cost
		//Validate if model have a reverses and processing of reverse
		if (model.getReversalLine_ID() > 0
			&& costDetail == null)
			return;
		else if( costDetail != null
			&& costDetail.isReversal()
			&& model.getReversalLine_ID() > 0) {	
                        setReversalCostDetail();		
                        return;
                }	

		// try find the last cost detail transaction
		lastCostDetail = MCostDetail.getLastTransaction(model, transaction,
				accountSchema.getC_AcctSchema_ID(), dimension.getM_CostType_ID(),
				dimension.getM_CostElement_ID(),dateAccounting,
				costingLevel);

		// created a new instance cost detail to process calculated cost
		if (lastCostDetail == null) {
			//  There was no cost detail history. This may be the first entry.
			//  Also means there is no accumulated costs or quantities
			log.fine("lastCostDetail was null. Creating a new instance.");
			lastCostDetail = new MCostDetail(transaction,
					accountSchema.getC_AcctSchema_ID(), dimension.getM_CostType_ID(),
					dimension.getM_CostElement_ID(), Env.ZERO, Env.ZERO,
					Env.ZERO, transaction.get_TrxName());
			lastCostDetail.setDateAcct(dateAccounting);
			lastCostDetail.setSeqNo(0);
			//lastCostDetail.saveEx();  // Don't save this instance.
		}
		log.fine("lastCostDetail: " + lastCostDetail.toString());
			
		BigDecimal quantityOnHand = getNewAccumulatedQuantity(lastCostDetail);		
		log.fine("Quantity on hand: " + quantityOnHand);

		// Create a new instance cost detail to process calculated cost

		// If the cost detail was already created for this transaction, then it is necessary to 
		// generate an adjustment and update the costs.	
		log.fine("Transaction ID: " + transaction.getM_Transaction_ID() + " lastCostDetail transaction ID: " + lastCostDetail.getM_Transaction_ID());
		if (transaction.getM_Transaction_ID() == lastCostDetail.getM_Transaction_ID()) {
			
			//Processing provision of purchase cost  
			//Provision is calculated when the last cost detail  is a material receipt and not exist of invoice line
			//if an invoice line exist for this cost detail then an invoice line was processed for this material receipt 
			//and not exist different between purchase cost and invoice cost, this logic was implemented to prevent 
			//that a provision of purchase cost decreases more than one times in a cost adjustment
			BigDecimal provisionOfPurchaseCost = BigDecimal.ZERO;
			BigDecimal provisionOfPurchaseCostLL = BigDecimal.ZERO;
                        // Quantity accumulated from last cost transaction
                        accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(transaction.getMovementQty());
                        log.fine("Transaction movement qty: " + transaction.getMovementQty() + ". New accumulatedQuantity: " + accumulatedQuantity);

			if (model instanceof MMatchInv && lastCostDetail.getC_InvoiceLine_ID() == 0)
			{
                                provisionOfPurchaseCost = lastCostDetail.getCostAmt();
				provisionOfPurchaseCostLL =  lastCostDetail.getCostAmtLL();
				MMatchInv iMatch =  (MMatchInv) model;
				lastCostDetail.setC_InvoiceLine_ID(iMatch.getC_InvoiceLine_ID());
				lastCostDetail.saveEx();
                                // reset the accumulated quantity with last cost detail
                                if (lastCostDetail != null && lastCostDetail.getM_CostDetail_ID() > 0)
                                accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail);
			}	
				
			adjustCost = transaction.getMovementQty().multiply(costThisLevel).subtract(provisionOfPurchaseCost);
			adjustCostLowerLevel =  transaction.getMovementQty().multiply(costLowLevel).subtract(provisionOfPurchaseCostLL);

			
			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
			accumulatedAmount = accumulatedQuantity.signum() > 0 ? accumulatedAmount.add(adjustCost) : accumulatedAmount.add(adjustCost.negate());
			
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
			accumulatedAmountLowerLevel =  accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(adjustCostLowerLevel) : 
				accumulatedAmountLowerLevel.add(adjustCostLowerLevel.negate());
					
			
			currentCostPrice = getNewCurrentCostPrice(lastCostDetail, 
					accountSchema.getCostingPrecision(), BigDecimal.ROUND_HALF_UP);
			currentCostPriceLowerLevel = getNewCurrentCostPriceLowerLevel(lastCostDetail, 
					accountSchema.getCostingPrecision(), BigDecimal.ROUND_HALF_UP);

			// If there are no adjustments to apply or the costDetail has not been created, we are done here.
			if(adjustCost.add(adjustCostLowerLevel).signum() == 0 || costDetail == null)
				return;
						
			// reset with the current values
			costDetail.setCostAdjustment(adjustCost);
			costDetail.setAmt(costDetail.getCostAmt().add(
					costDetail.getCostAdjustment()));
			costDetail.setCostAdjustmentLL(adjustCostLowerLevel);
			costDetail.setAmtLL(costDetail.getCostAmtLL().add(
					costDetail.getCostAdjustmentLL()));

			updateAmountCost();
                        updateRelatedRecordIDs();

			return;
		}
		
		// This is a new transaction.  Calculate costing
		if (transaction.getMovementType().endsWith("+"))
		{
			// Project cost: if a cost was entered as zero, then the inventory is revalued using the first cost
			// Example ; Quantity On hand 2.00 , Total Cost : 0.00 , Transaction Quantity 4.00 , Cost Total Transaction 17.8196
			// cost This Level = ( (17.8196 / 4)  * 6 ) / 4 | (costThisLevel * costThisLevel) / lastCostDetail.getQty()
			
			// Detect inventory with stock but zero value
			// Create a cost adjustment to correct the value
			if (quantityOnHand.signum() != 0
					&& getNewAccumulatedAmount(lastCostDetail).signum() == 0
					&& costThisLevel.signum() != 0) {
				// The adjustment is the value of the inventory
				adjustCost = quantityOnHand.multiply(costThisLevel);
				
			} else if (quantityOnHand.add(transaction.getMovementQty()).signum() < 0
					&& getNewCurrentCostPrice(lastCostDetail, accountSchema
						.getCostingPrecision(),  BigDecimal.ROUND_HALF_UP).signum() != 0
					&& costThisLevel.signum() == 0  )

			{
                                // Logic to calculate adjustment when inventory is negative
				currentCostPrice = getNewCurrentCostPrice(lastCostDetail, accountSchema
						.getCostingPrecision(),  BigDecimal.ROUND_HALF_UP);
				adjustCost = currentCostPrice.multiply(movementQuantity);
			}
            // If period is not open then an adjustment cost is create based on quantity on hand of attribute instance
            // the amount difference is apply to adjustment cost account, the reason is because is import distribute
            // proportionally
			if (model instanceof MLandedCostAllocation || model instanceof MMatchInv)
			{
                if (!isOpenPeriod) {
                    MLandedCostAllocation costAllocation = (MLandedCostAllocation) this.model;
                    this.movementQuantity = MCostDetail.getQtyOnHandByASIAndSeqNo(
                            transaction.getCtx(),
                            transaction.getM_Product_ID(),
                            dimension.getM_CostType_ID(),
                            dimension.getM_CostElement_ID(),
                            costAllocation.getM_InOutLine().getM_AttributeSetInstance_ID(),
                            lastCostDetail.getSeqNo(),
                            transaction.get_TrxName());

                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail);
                    currentCostPrice = movementQuantity.multiply(costThisLevel);
                    currentCostPriceLowerLevel = movementQuantity.multiply(costLowLevel);
                    adjustCost = currentCostPrice;
                    adjustCostLowerLevel = currentCostPriceLowerLevel;
                }
			}
			else
			{
                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(movementQuantity);
                    currentCostPrice = costThisLevel;
                    currentCostPriceLowerLevel = costLowLevel;
			}

            amount = movementQuantity.multiply(costThisLevel);
            amountLowerLevel = movementQuantity.multiply(costLowLevel);

//			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
//			accumulatedAmount = accumulatedQuantity.signum() > 0 ? accumulatedAmount.add(amount) : accumulatedAmount.add(amount.negate());
			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail).add(amount);
			
//			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
//			accumulatedAmountLowerLevel = accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(amountLowerLevel) : accumulatedAmountLowerLevel.add(amountLowerLevel.negate());
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail).add(amountLowerLevel);
		}
		else if (transaction.getMovementType().endsWith("-")) {
			// Use the last current cost price for out transaction			
			if (quantityOnHand.add(movementQuantity).signum() >= 0)
			{
				currentCostPrice = getNewCurrentCostPrice(lastCostDetail, accountSchema
						.getCostingPrecision(), BigDecimal.ROUND_HALF_UP);
				currentCostPriceLowerLevel = getNewCurrentCostPriceLowerLevel(lastCostDetail, accountSchema
                        .getCostingPrecision(), BigDecimal.ROUND_HALF_UP);
			} 
			else
			{
				currentCostPrice = CostEngine.getCostThisLevel(accountSchema, dimension.getM_CostType(), dimension.getM_CostElement(), transaction, model, costingLevel);
			}
		
			amount = transaction.getMovementQty().multiply(currentCostPrice);
			amountLowerLevel = movementQuantity.multiply(currentCostPriceLowerLevel);

			accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(
                    movementQuantity);
			
//			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
//			accumulatedAmount = accumulatedQuantity.signum() > 0 ? accumulatedAmount.add(amount) : accumulatedAmount.add(amount.negate());
			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail).add(amount);
			
//			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
//			accumulatedAmountLowerLevel = accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(amountLowerLevel) : accumulatedAmountLowerLevel.add(amountLowerLevel.negate());
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail).add(amountLowerLevel);
		
			if(costDetail != null)
			{	
//				costDetail.setAmt(currentCostPrice.multiply(movementQuantity.abs()));
//				costDetail.setAmtLL(currentCostPriceLowerLevel.multiply(movementQuantity).abs());
				costDetail.setAmt(amount);
				costDetail.setAmtLL(amountLowerLevel);
			}
		}
		
		//create new cost
		if (costDetail == null)
			return;
		
		updateAmountCost();
                updateRelatedRecordIDs();
	}

	private void createCostDetail() {

		// Validate if model is a reversal and process the reverseing entry
		if (model.getReversalLine_ID() > 0 && costDetail == null ) {
			createReversalCostDetail();
			return;
		} 
		else if (model.getReversalLine_ID() > 0) {
			// If this is a reversal and the costDetail exists, we don't need to do anything.
			return;
                }
		

		int seqNo = lastCostDetail.getSeqNo() + 10;
		// Create a new cost detail or it is necessary to create a new cost detail for
		// adjustments
		if (costDetail == null && (
				transaction.getM_Transaction_ID() != lastCostDetail.getM_Transaction_ID()
				|| adjustCost.add(adjustCostLowerLevel).signum() != 0)) {
			//
			// If adjustment costs exist for Landed Cost Allocation or Match Inv then set the movement qty to zero
			if (adjustCost.add(adjustCostLowerLevel).signum() != 0
                                || (model instanceof MLandedCostAllocation || model instanceof MMatchInv)) {
				movementQuantity = Env.ZERO;
                        }

			// create new cost detail
			costDetail = new MCostDetail(transaction, accountSchema.getC_AcctSchema_ID(),
					dimension.getM_CostType_ID(),
					dimension.getM_CostElement_ID(), currentCostPrice
							.multiply(movementQuantity),
					currentCostPriceLowerLevel.multiply(movementQuantity),
					movementQuantity, transaction.get_TrxName());
			// set account date for this cost detail
			costDetail.setDateAcct(dateAccounting);
			costDetail.setSeqNo(seqNo);

			// set if transaction is sales order type or not
			if (isSalesTransaction != null)
				costDetail.setIsSOTrx(isSalesTransaction);
			else
				costDetail.setIsSOTrx(model.isSOTrx());

			if (adjustCost.signum() != 0 || adjustCostLowerLevel.signum() != 0) {
				String description = costDetail.getDescription() != null ? costDetail
						.getDescription() + " " : "";
				// update adjustment cost this level
				if (adjustCost.signum() != 0) {
					costDetail.setCostAdjustmentDate(model.getDateAcct());
					costDetail.setCostAdjustment(adjustCost);
					//costDetail.setCostAmt(BigDecimal.ZERO);
					costDetail.setAmt(costDetail.getAmt().add(
							costDetail.getCostAdjustment()));
					costDetail.setDescription(description + "Adjust Cost:"
							+ adjustCost);
				}
				// update adjustment cost lower level
				if (adjustCostLowerLevel.signum() != 0) {
					description = costDetail.getDescription() != null ? costDetail
							.getDescription() + " " : "";
					costDetail.setCostAdjustmentDateLL(model.getDateAcct());
					costDetail.setCostAdjustmentLL(adjustCostLowerLevel);
					//costDetail.setCostAmtLL(BigDecimal.ZERO);
					costDetail.setAmt(costDetail.getCostAmtLL().add(
							costDetail.getCostAdjustmentLL()));
					costDetail.setDescription(description
							+ " Adjust Cost LL:" + adjustCost);
				}
			}

			// TODO: Check if this is necessary or correct?
			if (model instanceof MLandedCostAllocation) {
				costDetail.setProcessed(false);
			}

			updateAmountCost();
			updateRelatedRecordIDs();

			
			costDetail.saveEx(transaction.get_TrxName());
			log.fine("costDetail created: " + costDetail.toString());

			return;
		}
	}

	public MCostDetail process() {		
		calculate();
		createCostDetail();
		updateInventoryValue();
		createCostAdjustment();

		return costDetail;
	}

	public void createCostAdjustment() {
		// only re process cost detail if account schema need adjust cost
		if (!accountSchema.isAdjustCOGS())
			return;
		// void the cycle process, only process the adjustment
		if (costDetail == null || costDetail.isProcessing())
			return;

		// Check if cost detail is an earlier transaction
		// get the cost details that need be re process before this cost
		// transaction
		List<MCostDetail> cds = MCostDetail.getAfterDate(costDetail,
				costingLevel);
		if (cds == null || cds.size() == 0)
			return;
		
		MCostDetail last_cd = costDetail;
		costDetail = null;
		
		 /*System.out.println(
		 "-----------------------------------ADJUSTMENT COST -------------------------------------------------"
		 ); System.out.println(last_cd); System.out.println(
		 "----------------------------------------------------------------------------------------------------"
		 );*/
		 
		//Renumber sequence
		for (MCostDetail cd : cds) {
			cd.setSeqNo(last_cd.getSeqNo() + 10); // remunerate sequence
			cd.setProcessing(true);
			cd.saveEx();
			last_cd = cd;
			// Only uncomment to debug
			// Trx.get(cd.get_TrxName(), false).commit();
		}

		for (MCostDetail cd : cds) {
			adjustCostDetail(cd);
            cd.setProcessing(false);
            cd.saveEx();
 			//clearAccounting(cd);
			// Only uncomment to debug
			// Trx.get(cd.get_TrxName(), false).commit();
		}
	}

	@Override
	public void processCostDetail(MCostDetail costDetail) {
	}

	@Override
	protected List<CostComponent> getCalculatedCosts() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Average Invoice Get the New Current Cost Price This Level
	 * 
	 * @param cd
	 *            Cost Detail
	 * @param scale
	 *            Scale
	 * @param roundingMode
	 *            Rounding Mode
	 * @return New Current Cost Price This Level
	 */
	public BigDecimal getNewCurrentCostPrice(MCostDetail cd, int scale,
			int roundingMode) {
		if (getNewAccumulatedQuantity(cd).signum() != 0
				&& getNewAccumulatedAmount(cd).signum() != 0)
			return getNewAccumulatedAmount(cd).divide(getNewAccumulatedQuantity(cd), scale,
					roundingMode);
		else
			return BigDecimal.ZERO;
	}

	/**
	 * Average Invoice Get the New Cumulated Amt This Level
	 * 
	 * @param cd
	 *            Cost Detail
	 * @return New Cumulated Amt This Level
	 */
	public BigDecimal getNewAccumulatedAmount(MCostDetail cd) {

		BigDecimal accumulatedAmount = Env.ZERO;
//		if (cd.getQty().signum() > 0)
//			accumulatedAmount = cd.getCumulatedAmt().add(cd.getCostAmt())
//					.add(cd.getCostAdjustment());
//		else if (cd.getQty().signum() < 0)
//			accumulatedAmount = cd.getCumulatedAmt().add(cd.getCostAmt().negate())
//					.add(cd.getCostAdjustment().negate());
//		else if (cd.getQty().signum() == 0)
//		{
//			if(getNewAccumulatedQuantity(cd).signum() > 0)
//				accumulatedAmount = cd.getCumulatedAmt().add(cd.getCostAmt())
//				.add(cd.getCostAdjustment());
//			else if (getNewAccumulatedQuantity(cd).signum() < 0)
//				accumulatedAmount = cd.getCumulatedAmt().add(cd.getCostAmt().negate())
//				.add(cd.getCostAdjustment().negate());
//				
//		}
		accumulatedAmount = cd.getCumulatedAmt().add(cd.getCostAmt())
				.add(cd.getCostAdjustment());

		return accumulatedAmount;
	}

	/**
	 * Average Invoice Get the New Current Cost Price low level
	 * 
	 * @param costDetail Cost Detail
	 * @param scale Scale
	 * @param roundingMode Rounding Mode
	 * @return New Current Cost Price low level
	 */
	public BigDecimal getNewCurrentCostPriceLowerLevel(MCostDetail costDetail, int scale,
                                                       int roundingMode) {
		if (getNewAccumulatedQuantity(costDetail).signum() != 0
				&& getNewAccumulatedAmountLowerLevel(costDetail).signum() != 0)
			return getNewAccumulatedAmountLowerLevel(costDetail).divide(getNewAccumulatedQuantity(costDetail),
					scale, roundingMode);
		else
			return BigDecimal.ZERO;
	}

	/**
	 * Average Invoice Get the new Cumulated Amt Low Level
	 * 
	 * @param costDetail MCostDetail
	 * @return New Cumulated Am Low Level
	 */
	public BigDecimal getNewAccumulatedAmountLowerLevel(MCostDetail costDetail) {
		BigDecimal accumulatedAmountLowerLevel = Env.ZERO;
		if (costDetail.getQty().signum() >= 0)
			accumulatedAmountLowerLevel = costDetail.getCumulatedAmtLL().add(costDetail.getCostAmtLL())
					.add(costDetail.getCostAdjustmentLL());
		else
			accumulatedAmountLowerLevel = costDetail.getCumulatedAmtLL()
					.add(costDetail.getCostAmtLL().negate())
					.add(costDetail.getCostAdjustmentLL().negate());
		return accumulatedAmountLowerLevel;
	}

	/**
	 * Average Invoice Get the new Cumulated Qty
	 * @param costDetail Cost Detail
	 * @return New Accumulated Quantity
	 */
	public BigDecimal getNewAccumulatedQuantity(MCostDetail costDetail) {
		    return costDetail.getCumulatedQty().add(costDetail.getQty());
	}

	/**
	 * Update Cost Amt
	 */
	public void updateAmountCost() {
		
		costDetail.setCostAmt(costDetail.getAmt().subtract(
				costDetail.getCostAdjustment()));
		costDetail.setCostAmtLL(costDetail.getAmtLL().subtract(
				costDetail.getCostAdjustmentLL()));

                costDetail.setCumulatedQty(getNewAccumulatedQuantity(lastCostDetail));
                costDetail.setCumulatedAmt(getNewAccumulatedAmount(lastCostDetail));
                costDetail.setCurrentCostPrice(currentCostPrice);
                costDetail.setCurrentCostPriceLL(currentCostPriceLowerLevel);
       
                // Update the currentCostPrice to refelect the average cost of the costDetail
                currentCostPrice = getNewCurrentCostPrice(costDetail, 
				accountSchema.getCostingPrecision(), BigDecimal.ROUND_HALF_UP);
		currentCostPriceLowerLevel = getNewCurrentCostPriceLowerLevel(costDetail, 
				accountSchema.getCostingPrecision(), BigDecimal.ROUND_HALF_UP);

	}

    public void updateInventoryValue() {
        dimension.setCurrentCostPrice(currentCostPrice);
        dimension.setCurrentCostPriceLL(currentCostPriceLowerLevel);
        dimension.setCumulatedAmt(accumulatedAmount);
        dimension.setCumulatedAmtLL(accumulatedAmountLowerLevel);
        dimension.setCumulatedQty(accumulatedQuantity);
        dimension.setCurrentQty(accumulatedQuantity);
        dimension.saveEx();
        log.fine(dimension.toString());
    }


    /**
	 * Recalculate Cost Detail
	 * @param costDetail
     */
	public void adjustCostDetail(MCostDetail costDetail) {

        Properties ctx =  costDetail.getCtx();
        String trxName = costDetail.get_TrxName();
        int transactionId = costDetail.getM_Transaction_ID();
        int clientId = costDetail.getAD_Client_ID();

		MTransaction transaction = new MTransaction(ctx, transactionId, trxName);

		MCostType costType = (MCostType) costDetail.getM_CostType();
		MCostElement costElement = (MCostElement) costDetail.getM_CostElement();
		MAcctSchema accountSchema = (MAcctSchema) costDetail.getC_AcctSchema();

        CostEngineFactory.getCostEngine(accountSchema.getAD_Client_ID())
                .clearAccounting(accountSchema, transaction);

		if (MTransaction.MOVEMENTTYPE_VendorReceipts.equals(transaction.getMovementType()))
		{
			MInOutLine line = (MInOutLine) transaction.getDocumentLine();
			if (MCostElement.COSTELEMENTTYPE_Material.equals(costElement.getCostElementType()))
			{
                if (costDetail.getM_InOutLine_ID() > 0 && costDetail.getQty().signum() !=  0 )
                {
                    CostEngineFactory.getCostEngine(clientId).createCostDetail(
                            accountSchema, costType, costElement, transaction, line, true);
                }
                else if (costDetail.getM_InOutLine_ID() > 0 && costDetail.getQty().signum() != 0 && costDetail.getC_OrderLine_ID() > 0) {
                    List<MMatchPO> orderMatches = MMatchPO.getInOutLine(line);
                    for (MMatchPO match : orderMatches) {
                        if (match.getM_InOutLine_ID() == line.getM_InOutLine_ID()
                                && match.getM_Product_ID() == transaction.getM_Product_ID()) {
                            CostEngineFactory.getCostEngine(clientId)
                                    .createCostDetail(accountSchema, costType, costElement, transaction, match, true);
                        }
                    }
                }
                else if (costDetail.getM_InOutLine_ID() > 0 && costDetail.getQty().signum() == 0 && costDetail.getC_InvoiceLine_ID() > 0 ) {
                    List<MMatchInv> invoiceMatches = MMatchInv
                            .getInOutLine(line);
                    for (MMatchInv match : invoiceMatches) {
                        if (match.getM_Product_ID() == transaction.getM_Product_ID()) {
                            CostEngineFactory.getCostEngine(clientId)
                                    .createCostDetail(accountSchema, costType, costElement, transaction, match, true);
                        }
                    }
                }
			}

			//get landed allocation cost
			for (MLandedCostAllocation allocation : 
				MLandedCostAllocation.getOfInOuline(line,
							costElement.getM_CostElement_ID()))
			{
				//System.out.println("Allocation : " + allocation.getC_LandedCostAllocation_ID() +  " Amount:" +  allocation.getAmt());
				CostEngineFactory
				.getCostEngine(clientId)
				.createCostDetail(accountSchema, costType, costElement, transaction, allocation, true);
			}
		}
        else
            CostEngineFactory.getCostEngine(clientId).createCostDetail(
                    accountSchema, costType, costElement, transaction, transaction.getDocumentLine(), true);
	}	
	

	public void createUpdateAverageCostDetail(MPPCostCollector costCollectorVariance,
			BigDecimal costVarianceThisLevel, BigDecimal costVarianceLowLevel,
			MProduct product,
			MAcctSchema acctSchema, MCostType costType, MCostElement costElement) {

		String whereClause = " exists (select 1 from pp_cost_collector pc" +
				" where pc.pp_cost_collector_ID=m_transaction.pp_Cost_collector_ID and costcollectortype =? " +
				" and pc.pp_order_ID=?)";
		MTransaction mtrx =  new Query(costCollectorVariance.getCtx(), MTransaction.Table_Name, whereClause, costCollectorVariance.get_TrxName())
		.setParameters(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt,costCollectorVariance.getPP_Order_ID())
		.setOrderBy("M_Transaction_ID desc")
		.first();

		BigDecimal costThisLevel = Env.ZERO;
		BigDecimal costLowLevel = Env.ZERO;
		String costingLevel = MProduct.get(mtrx.getCtx(),
				mtrx.getM_Product_ID()).getCostingLevel(acctSchema,
						mtrx.getAD_Org_ID());
		costCollectorVariance.set_ValueOfColumn("Cost", costVarianceThisLevel.compareTo(Env.ZERO) != 0 ? costVarianceThisLevel : costVarianceLowLevel);
		costCollectorVariance.saveEx();
		IDocumentLine model = costCollectorVariance;

		MCost cost = MCost.validateCostForCostType(acctSchema, costType, costElement,product.getM_Product_ID(),
				0, 0, 0, mtrx.get_TrxName());
		final ICostingMethod method = CostingMethodFactory.get()
				.getCostingMethod(costType.getCostingMethod());
		method.setCostingMethod(acctSchema, mtrx, model, cost, costThisLevel,
				costLowLevel, model.isSOTrx());
		method.process();
	}

	public BigDecimal getResourceActualCostRate(MPPCostCollector costCollector,
			int resourceId, CostDimension costDimension, String trxName) {
		if (resourceId <= 0)
			return Env.ZERO;
		final MProduct resourceProduct = MProduct.forS_Resource_ID(
				Env.getCtx(), resourceId, null);
		return getProductActualCostPrice(costCollector, resourceProduct,
				MAcctSchema.get(Env.getCtx(), costDimension.getC_AcctSchema_ID()),
				MCostElement.get(Env.getCtx(), costDimension.getM_CostElement_ID()),
				trxName);
	}
	

	public BigDecimal getProductActualCostPrice(MPPCostCollector costCollector, MProduct product, MAcctSchema acctSchema, MCostElement costElement, String trxName)
	{
		String CostingLevel = product.getCostingLevel(acctSchema);
		// Org Element
		int orgId = 0;
		int warehouseId = 0;
		if (product.getS_Resource_ID() != 0){
			orgId = product.getS_Resource().getAD_Org_ID();
			warehouseId = product.getS_Resource().getM_Warehouse_ID();
		}
			
		else 
		{
			orgId = (costCollector == null)? costElement.getAD_Org_ID():costCollector.getAD_Org_ID();
			warehouseId = (costCollector == null)? 0:costCollector.getM_Warehouse_ID();
		}
		int attributeSetInstanceId = (costCollector == null)? 0:costCollector.getM_AttributeSetInstance_ID();
		if (MAcctSchema.COSTINGLEVEL_Client.equals(CostingLevel)) {
			orgId = 0;
			attributeSetInstanceId = 0;
			warehouseId = 0;
		} 
		else if (MAcctSchema.COSTINGLEVEL_Organization.equals(CostingLevel))
			attributeSetInstanceId = 0;
		else if (MAcctSchema.COSTINGLEVEL_BatchLot.equals(CostingLevel))
			orgId = 0;
		CostDimension costDimension = new CostDimension(product,
				acctSchema, acctSchema.getM_CostType_ID(),
				orgId,
				attributeSetInstanceId,
				warehouseId, //warehouse
				costElement.getM_CostElement_ID());
		MCost cost = costDimension.toQuery(MCost.class, trxName).firstOnly();

		if (cost == null)
			return Env.ZERO;
		BigDecimal price = cost.getCurrentCostPrice().add(
                cost.getCurrentCostPriceLL());
		return roundCost(price, acctSchema.getC_AcctSchema_ID());
	}

	protected BigDecimal roundCost(BigDecimal price, int accountSchemaId) {
		// Fix Cost Precision
		int precision = MAcctSchema.get(Env.getCtx(), accountSchemaId)
				.getCostingPrecision();
		BigDecimal priceRounded = price;
		if (priceRounded.scale() > precision) {
			priceRounded = priceRounded.setScale(precision,
					RoundingMode.HALF_UP);
		}
		return priceRounded;
	}
	

	public BigDecimal getResourceFutureCostRate(MPPCostCollector costCollector,
			int resourceId, CostDimension costDimension, String trxName) {
		if (resourceId <= 0)
			return Env.ZERO;
		final MProduct resourceProduct = MProduct.forS_Resource_ID(
				Env.getCtx(), resourceId, null);
		return getProductFutureCostPrice(costCollector, resourceProduct,
				MAcctSchema.get(Env.getCtx(), costDimension.getC_AcctSchema_ID()),
				MCostElement.get(Env.getCtx(), costDimension.getM_CostElement_ID()),
				trxName);
	}
	

	public BigDecimal getProductFutureCostPrice(MPPCostCollector costCollector, MProduct product, MAcctSchema acctSchema, MCostElement costElement, String trxName)
	{
		String CostingLevel = product.getCostingLevel(acctSchema);
		// Org Element
		int orgId = 0;
		int warehouseId = 0;
		if (product.getS_Resource_ID() != 0){
			orgId = product.getS_Resource().getAD_Org_ID();
			warehouseId = product.getS_Resource().getM_Warehouse_ID();
		}
			
		else 
		{
			orgId = (costCollector == null)? costElement.getAD_Org_ID():costCollector.getAD_Org_ID();
			warehouseId = (costCollector == null)? 0:costCollector.getM_Warehouse_ID();
		}
		int attributeSetInstanceId = (costCollector == null)? 0:costCollector.getM_AttributeSetInstance_ID();
		if (MAcctSchema.COSTINGLEVEL_Client.equals(CostingLevel)) {
			orgId = 0;
			attributeSetInstanceId = 0;
			warehouseId = 0;
		} 
		else if (MAcctSchema.COSTINGLEVEL_Organization.equals(CostingLevel))
			attributeSetInstanceId = 0;
		else if (MAcctSchema.COSTINGLEVEL_BatchLot.equals(CostingLevel))
			orgId = 0;
		CostDimension d = new CostDimension(product,
				acctSchema, acctSchema.getM_CostType_ID(),
				orgId,
				attributeSetInstanceId,
				warehouseId, //warehouse
				costElement.getM_CostElement_ID());
		MCost cost = d.toQuery(MCost.class, trxName).firstOnly();

		if (cost == null)
			return Env.ZERO;
		BigDecimal price = cost.getFutureCostPrice().add(
                cost.getFutureCostPriceLL());
		return roundCost(price, acctSchema.getC_AcctSchema_ID());
	}
}
