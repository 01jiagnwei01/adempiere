/** ****************************************************************************
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
 * Copyright (C) 2003-2016 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 * ****************************************************************************/


package org.adempiere.pos;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pos.command.CommandManager;
import org.adempiere.pos.command.Command;
import org.adempiere.pos.command.CommandReceiver;
import org.adempiere.pos.search.POSQuery;
import org.adempiere.pos.search.QueryBPartner;
import org.adempiere.pos.service.I_POSQuery;
import org.adempiere.pos.service.POSQueryListener;
import org.compiere.apps.ADialog;
import org.compiere.apps.AEnv;
import org.compiere.apps.Waiting;
import org.compiere.model.MBPartner;
import org.compiere.model.MOrder;
import org.compiere.process.ProcessInfo;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Optional;

/**
 * Class that execute business logic from POS
 * eEvolution author Victor Perez <victor.perez@e-evolution.com>, Created by e-Evolution on 29/12/15.
 */
public class POSActionMenu implements  ActionListener , POSQueryListener{

    private VPOS pos;
    private Integer partnerId;
    private POSQuery queryPartner;
    private JPopupMenu popupMenu;
    private CommandManager commandManager;
    private Command currentCommand;

    public POSActionMenu(VPOS pos)
    {
        this.pos = pos;
        this.popupMenu =  new JPopupMenu();
        commandManager =  new CommandManager();
        for (Map.Entry<String, CommandReceiver> receivers: commandManager.getCommandReceivers().entrySet()) {
            CommandReceiver commandReceiver = receivers.getValue();
            addMenuItem(commandReceiver.getEvent());
        }

    }

    private void addMenuItem(String optionName)
    {
        JMenuItem menuItem =  new JMenuItem(optionName);
        popupMenu.add(menuItem).addActionListener(this);
    }


    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        try {
        popupMenu.setVisible(false);
        currentCommand = commandManager.getCommand(actionEvent.getActionCommand());
        beforeExecutionCommand(currentCommand);
        } catch (AdempiereException exception) {
            ADialog.error(pos.getWindowNo(), pos.getFrame() , exception.getLocalizedMessage());
        }
    }

    private void beforeExecutionCommand(Command command)
    {
        if (command.getCommand() == CommandManager.GENERATE_IMMEDIATE_INVOICE)
        {
            if (pos.isCompleted()) {
                queryPartner = new QueryBPartner(pos);
                queryPartner.setModal(true);
                queryPartner.addOptionListener(this);
                queryPartner.showView();
                return;
            }
        }
        if (command.getCommand() == CommandManager.GENERATE_REVERSE_SALES)
        {
            if (pos.isCompleted())
               executeCommand(command);
        }

        if (command.getCommand() == CommandManager.GENERATE_RETURN)
        {
            executeCommand(command);
            return;
        }

        if (command.getCommand() == CommandManager.COMPLETE_DOCUMENT
        		&& (pos.isDrafted() || pos.isInProgress() || pos.isInvalid())) {
        	Trx.run(new TrxRunnable() {
        		public void run(String trxName) {
        			if (!pos.processOrder(trxName, false, false)) {
        				String errorMessage = Msg.parseTranslation(pos.getCtx(), " @ProcessRunError@. " 
        						+ "@order.no@: " + pos.getDocumentNo()+ ". @Process@: " + CommandManager.COMPLETE_DOCUMENT);
        				throw new AdempierePOSException(errorMessage);
        			}
        			pos.refreshHeader();
        		}
        	});
        	
        	// For certain documents, there is no further processing
        	String docSubTypeSO = pos.getM_Order().getC_DocTypeTarget().getDocSubTypeSO();
        	if((docSubTypeSO.equals(MOrder.DocSubTypeSO_Standard) ||
        		docSubTypeSO.equals(MOrder.DocSubTypeSO_OnCredit) ||
        		docSubTypeSO.equals(MOrder.DocSubTypeSO_Warehouse)) 
        		&& pos.getM_Order().getDocStatus().equals(MOrder.DOCSTATUS_Completed)) {        		
        		String message = Msg.parseTranslation(pos.getCtx(), " @DocProcessed@. " 
        				+ "@order.no@: " + pos.getDocumentNo()+ ". @Process@: " + CommandManager.COMPLETE_DOCUMENT);
        		ADialog.info(pos.getWindowNo(), popupMenu ,"DocProcessed",  message );
        	}
        	else
        		executeCommand(command);
        }            
        else
        	ADialog.info(pos.getWindowNo(), popupMenu, "DocProcessed", pos.getDocumentNo());
    }

    private void afterExecutionCommand(Command command)
    {
    }

    private void executeCommand(Command command)
    {
        try {
            CommandReceiver receiver = commandManager.getCommandReceivers(command.getEvent());
            if (command.getCommand() == CommandManager.GENERATE_IMMEDIATE_INVOICE
                    && pos.getC_Order_ID() > 0
                    && pos.isCompleted()) {
                receiver.setCtx(pos.getCtx());
                receiver.setPartnerId(queryPartner.getRecord_ID());
                receiver.setOrderId(pos.getC_Order_ID());
                MBPartner partner = MBPartner.get(pos.getCtx(), receiver.getPartnerId());
                Optional<String> taxId = Optional.ofNullable(partner.getTaxID());
                String processMessage = receiver.getName()
                        + " @DisplayDocumentInfo@ : " + pos.getDocumentNo()
                        + " @To@ @C_BPartner_ID@ : " + partner.getName()
                        + " @TaxID@ : " + taxId.orElse("");
                if (ADialog.ask(pos.getWindowNo(), popupMenu, "StartProcess?", Msg.parseTranslation(pos.getCtx(), processMessage))) {
                    Waiting waiting = new Waiting(pos.getFrame(), "@Processing@", false, 120);
                    AEnv.showCenterScreen(waiting);
                    command.execute(receiver);
                    ProcessInfo processInfo = receiver.getProcessInfo();
                    waiting.setVisible(false);
                    if (processInfo.isError()) {
                        String errorMessage = Msg.parseTranslation(pos.getCtx(), processInfo.getTitle() + " @ProcessRunError@ " + processInfo.getSummary());
                        throw new AdempierePOSException(errorMessage);
                    } else {
                        afterExecutionCommand(command);
                        showOkMessage(processInfo);
                        pos.setOrder(processInfo.getRecord_ID());
                        pos.refreshHeader();
                    }
                    return;
                }
            }
            //Reverse The Sales Transaction
            if (command.getCommand() == CommandManager.GENERATE_REVERSE_SALES
                    && pos.getC_Order_ID() > 0
                    && !pos.isReturnMaterial()
                    && !pos.isVoided()
                    && !pos.isClosed()) {
                receiver.setCtx(pos.getCtx());
                receiver.setOrderId(pos.getC_Order_ID());
                receiver.setPartnerId(pos.getC_BPartner_ID());
                String processMessage = receiver.getName()
                        + " @order.no@ : " + pos.getDocumentNo()
                        + " @To@ @C_BPartner_ID@ : " + pos.getBPName();

                if (ADialog.ask(pos.getWindowNo(), popupMenu, "StartProcess?", Msg.parseTranslation(pos.getCtx(), processMessage))) {
                    Waiting waiting = new Waiting(pos.getFrame(), "@Processing@", false, 120);
                    AEnv.showCenterScreen(waiting);
                    command.execute(receiver);
                    ProcessInfo processInfo = receiver.getProcessInfo();
                    waiting.setVisible(false);
                    if (processInfo.isError()) {
                        showError(processInfo);
                    } else {
                        afterExecutionCommand(command);
                        showOkMessage(processInfo);
                    }
                    return;
                }
            }
            //Return product
            if (command.getCommand() == CommandManager.GENERATE_RETURN && pos.getC_Order_ID() > 0 && !pos.isReturnMaterial()) {
                receiver.setCtx(pos.getCtx());
                receiver.setOrderId(pos.getC_Order_ID());
                receiver.setPartnerId(pos.getC_BPartner_ID());
                String processMessage = receiver.getName()
                        + " @order.no@ : " + pos.getDocumentNo()
                        + " @To@ @C_BPartner_ID@ : " + pos.getBPName();

                if (ADialog.ask(pos.getWindowNo(), popupMenu, "StartProcess?", Msg.parseTranslation(pos.getCtx(), processMessage))) {

                    Waiting waiting = new Waiting(pos.getFrame(), "@Processing@", false, 120);
                    AEnv.showCenterScreen(waiting);
                    command.execute(receiver);
                    ProcessInfo processInfo = receiver.getProcessInfo();
                    waiting.setVisible(false);
                    if (processInfo.isError()) {
                        showError(processInfo);
                    } else {
                        afterExecutionCommand(command);
                        showOkMessage(processInfo);
                        //execute out transaction
                        if (processInfo.getRecord_ID() > 0) {
                            pos.setOrder(processInfo.getRecord_ID());
                            pos.refreshHeader();
                        }
                    }

                    return;
                }
            }
            if (command.getCommand() == CommandManager.COMPLETE_DOCUMENT && pos.getC_Order_ID() > 0) {
                if (pos.isReturnMaterial() && pos.isCompleted()) {
                    receiver.setCtx(pos.getCtx());
                    receiver.setOrderId(pos.getC_Order_ID());
                    receiver.setWarehouseId(pos.getM_Warehouse_ID());
                    Waiting waiting = new Waiting(pos.getFrame(), "@Processing@", false, 120);
                    AEnv.showCenterScreen(waiting);
                    command.execute(receiver);
                    ProcessInfo processInfo = receiver.getProcessInfo();
                    waiting.setVisible(false);
                    if (processInfo.isError()) {
                        showError(processInfo);
                    }
                    afterExecutionCommand(command);
                    showOkMessage(processInfo);
                    pos.setOrder(processInfo.getRecord_ID());
                    pos.refreshHeader();
                }
            }
        }
        catch (AdempierePOSException exception)
        {
            ADialog.error(pos.getWindowNo(), pos.getFrame() , exception.getLocalizedMessage());
        }
    }

    public void show(Component component , int x , int y)
    {
        popupMenu.show(component, x , y);
    }

    @Override
    public void okAction(I_POSQuery query) {
        if (query.getRecord_ID() <= 0)
            return;
        //	For Ticket
        if(query instanceof QueryBPartner) {
            executeCommand(currentCommand);
        }
    }

    @Override
    public void cancelAction(I_POSQuery query) {
    }

    private void showError(ProcessInfo processInfo) throws AdempierePOSException
    {
        Optional<String> summary = Optional.ofNullable(processInfo.getSummary());
        Optional<String> logs = Optional.ofNullable(processInfo.getLogInfo());
        String errorMessage = Msg.parseTranslation(pos.getCtx() , processInfo.getTitle() + " @ProcessRunError@ @Summary@ : " + summary.orElse("") + " @ProcessFailed@ : " + logs.orElse(""));
        throw new AdempierePOSException(errorMessage);
    }

    private void showOkMessage(ProcessInfo processInfo)
    {
        pos.refreshHeader();
        Optional<String> summary = Optional.ofNullable(processInfo.getSummary());
        Optional<String> logs = Optional.ofNullable(processInfo.getLogInfo());
        String okMessage = Msg.parseTranslation(pos.getCtx() , " @AD_Process_ID@ "+ processInfo.getTitle() +" @Summary@ : " + summary.orElse("") + " @ProcessOK@ : " + logs.orElse(""));
        ADialog.info(pos.getWindowNo(), popupMenu ,"ProcessOK",  okMessage );
    }
}
