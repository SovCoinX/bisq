/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.arbitration;

import io.bitsquare.app.Version;
import io.bitsquare.arbitration.messages.DisputeDirectMessage;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Contract;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Dispute implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(Dispute.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String tradeId;
    private final String id;
    private final int traderId;
    private final boolean disputeOpenerIsBuyer;
    private final boolean disputeOpenerIsOfferer;
    private final long openingDate;
    private final PubKeyRing traderPubKeyRing;
    private final long tradeDate;
    private final Contract contract;
    private final byte[] contractHash;
    @Nullable
    private final byte[] depositTxSerialized;
    @Nullable
    private final byte[] payoutTxSerialized;
    @Nullable
    private final String depositTxId;
    @Nullable
    private final String payoutTxId;
    private final String contractAsJson;
    private final String offererContractSignature;
    private final String takerContractSignature;
    private final PubKeyRing arbitratorPubKeyRing;
    private final boolean isSupportTicket;

    private final List<DisputeDirectMessage> disputeDirectMessages = new ArrayList<>();

    private boolean isClosed;
    private DisputeResult disputeResult;
    private Transaction disputePayoutTx;

    transient private Storage<DisputeList<Dispute>> storage;
    transient private ObservableList<DisputeDirectMessage> disputeDirectMessagesAsObservableList = FXCollections.observableArrayList(disputeDirectMessages);
    transient private BooleanProperty isClosedProperty = new SimpleBooleanProperty(isClosed);
    transient private ObjectProperty<DisputeResult> disputeResultProperty = new SimpleObjectProperty<>(disputeResult);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(Storage<DisputeList<Dispute>> storage,
                   String tradeId,
                   int traderId,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsOfferer,
                   PubKeyRing traderPubKeyRing,
                   Date tradeDate,
                   Contract contract,
                   byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   String offererContractSignature,
                   String takerContractSignature,
                   PubKeyRing arbitratorPubKeyRing,
                   boolean isSupportTicket) {
        this.storage = storage;
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeOpenerIsBuyer = disputeOpenerIsBuyer;
        this.disputeOpenerIsOfferer = disputeOpenerIsOfferer;
        this.traderPubKeyRing = traderPubKeyRing;
        this.tradeDate = tradeDate.getTime();
        this.contract = contract;
        this.contractHash = contractHash;
        this.depositTxSerialized = depositTxSerialized;
        this.payoutTxSerialized = payoutTxSerialized;
        this.depositTxId = depositTxId;
        this.payoutTxId = payoutTxId;
        this.contractAsJson = contractAsJson;
        this.offererContractSignature = offererContractSignature;
        this.takerContractSignature = takerContractSignature;
        this.arbitratorPubKeyRing = arbitratorPubKeyRing;
        this.isSupportTicket = isSupportTicket;
        this.openingDate = new Date().getTime();

        id = tradeId + "_" + traderId;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            disputeDirectMessagesAsObservableList = FXCollections.observableArrayList(disputeDirectMessages);
            disputeResultProperty = new SimpleObjectProperty<>(disputeResult);
            isClosedProperty = new SimpleBooleanProperty(isClosed);
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    public void addDisputeMessage(DisputeDirectMessage disputeDirectMessage) {
        if (!disputeDirectMessages.contains(disputeDirectMessage)) {
            disputeDirectMessages.add(disputeDirectMessage);
            disputeDirectMessagesAsObservableList.add(disputeDirectMessage);
            storage.queueUpForSave();
        } else {
            log.error("disputeDirectMessage already exists");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // In case we get the object via the network storage is not set as its transient, so we need to set it.
    public void setStorage(Storage<DisputeList<Dispute>> storage) {
        this.storage = storage;
    }

    public void setIsClosed(boolean isClosed) {
        this.isClosed = isClosed;
        isClosedProperty.set(isClosed);
        storage.queueUpForSave();
    }

    public void setDisputeResult(DisputeResult disputeResult) {
        this.disputeResult = disputeResult;
        disputeResultProperty.set(disputeResult);
        storage.queueUpForSave();
    }

    public void setDisputePayoutTx(Transaction disputePayoutTx) {
        this.disputePayoutTx = disputePayoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getShortTradeId() {
        return tradeId.substring(0, 8);
    }

    public int getTraderId() {
        return traderId;
    }

    public boolean isDisputeOpenerIsBuyer() {
        return disputeOpenerIsBuyer;
    }

    public boolean isDisputeOpenerIsOfferer() {
        return disputeOpenerIsOfferer;
    }

    public Date getOpeningDate() {
        return new Date(openingDate);
    }

    public PubKeyRing getTraderPubKeyRing() {
        return traderPubKeyRing;
    }

    public Contract getContract() {
        return contract;
    }

    @Nullable
    public byte[] getDepositTxSerialized() {
        return depositTxSerialized;
    }

    @Nullable
    public byte[] getPayoutTxSerialized() {
        return payoutTxSerialized;
    }

    @Nullable
    public String getDepositTxId() {
        return depositTxId;
    }

    @Nullable
    public String getPayoutTxId() {
        return payoutTxId;
    }

    public String getContractAsJson() {
        return contractAsJson;
    }

    public String getOffererContractSignature() {
        return offererContractSignature;
    }

    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public ObservableList<DisputeDirectMessage> getDisputeDirectMessagesAsObservableList() {
        return disputeDirectMessagesAsObservableList;
    }

    public boolean isClosed() {
        return isClosedProperty.get();
    }

    public ReadOnlyBooleanProperty isClosedProperty() {
        return isClosedProperty;
    }

    public PubKeyRing getArbitratorPubKeyRing() {
        return arbitratorPubKeyRing;
    }

    public ObjectProperty<DisputeResult> disputeResultProperty() {
        return disputeResultProperty;
    }

    public boolean isSupportTicket() {
        return isSupportTicket;
    }

    public byte[] getContractHash() {
        return contractHash;
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Transaction getDisputePayoutTx() {
        return disputePayoutTx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dispute)) return false;

        Dispute dispute = (Dispute) o;

        if (traderId != dispute.traderId) return false;
        if (disputeOpenerIsBuyer != dispute.disputeOpenerIsBuyer) return false;
        if (disputeOpenerIsOfferer != dispute.disputeOpenerIsOfferer) return false;
        if (openingDate != dispute.openingDate) return false;
        if (tradeDate != dispute.tradeDate) return false;
        if (isSupportTicket != dispute.isSupportTicket) return false;
        if (isClosed != dispute.isClosed) return false;
        if (tradeId != null ? !tradeId.equals(dispute.tradeId) : dispute.tradeId != null) return false;
        if (traderPubKeyRing != null ? !traderPubKeyRing.equals(dispute.traderPubKeyRing) : dispute.traderPubKeyRing != null)
            return false;
        if (contract != null ? !contract.equals(dispute.contract) : dispute.contract != null) return false;
        if (!Arrays.equals(contractHash, dispute.contractHash)) return false;
        if (!Arrays.equals(depositTxSerialized, dispute.depositTxSerialized)) return false;
        if (!Arrays.equals(payoutTxSerialized, dispute.payoutTxSerialized)) return false;
        if (depositTxId != null ? !depositTxId.equals(dispute.depositTxId) : dispute.depositTxId != null) return false;
        if (payoutTxId != null ? !payoutTxId.equals(dispute.payoutTxId) : dispute.payoutTxId != null) return false;
        if (contractAsJson != null ? !contractAsJson.equals(dispute.contractAsJson) : dispute.contractAsJson != null)
            return false;
        if (offererContractSignature != null ? !offererContractSignature.equals(dispute.offererContractSignature) : dispute.offererContractSignature != null)
            return false;
        if (takerContractSignature != null ? !takerContractSignature.equals(dispute.takerContractSignature) : dispute.takerContractSignature != null)
            return false;
        if (arbitratorPubKeyRing != null ? !arbitratorPubKeyRing.equals(dispute.arbitratorPubKeyRing) : dispute.arbitratorPubKeyRing != null)
            return false;
        if (disputeDirectMessages != null ? !disputeDirectMessages.equals(dispute.disputeDirectMessages) : dispute.disputeDirectMessages != null)
            return false;
        return !(disputeResult != null ? !disputeResult.equals(dispute.disputeResult) : dispute.disputeResult != null);

    }

    @Override
    public int hashCode() {
        int result = tradeId != null ? tradeId.hashCode() : 0;
        result = 31 * result + traderId;
        result = 31 * result + (disputeOpenerIsBuyer ? 1 : 0);
        result = 31 * result + (disputeOpenerIsOfferer ? 1 : 0);
        result = 31 * result + (int) (openingDate ^ (openingDate >>> 32));
        result = 31 * result + (traderPubKeyRing != null ? traderPubKeyRing.hashCode() : 0);
        result = 31 * result + (int) (tradeDate ^ (tradeDate >>> 32));
        result = 31 * result + (contract != null ? contract.hashCode() : 0);
        result = 31 * result + (contractHash != null ? Arrays.hashCode(contractHash) : 0);
        result = 31 * result + (depositTxSerialized != null ? Arrays.hashCode(depositTxSerialized) : 0);
        result = 31 * result + (payoutTxSerialized != null ? Arrays.hashCode(payoutTxSerialized) : 0);
        result = 31 * result + (depositTxId != null ? depositTxId.hashCode() : 0);
        result = 31 * result + (payoutTxId != null ? payoutTxId.hashCode() : 0);
        result = 31 * result + (contractAsJson != null ? contractAsJson.hashCode() : 0);
        result = 31 * result + (offererContractSignature != null ? offererContractSignature.hashCode() : 0);
        result = 31 * result + (takerContractSignature != null ? takerContractSignature.hashCode() : 0);
        result = 31 * result + (arbitratorPubKeyRing != null ? arbitratorPubKeyRing.hashCode() : 0);
        result = 31 * result + (isSupportTicket ? 1 : 0);
        result = 31 * result + (disputeDirectMessages != null ? disputeDirectMessages.hashCode() : 0);
        result = 31 * result + (isClosed ? 1 : 0);
        result = 31 * result + (disputeResult != null ? disputeResult.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Dispute{" +
                ", tradeId='" + tradeId + '\'' +
                ", traderId='" + traderId + '\'' +
                ", disputeOpenerIsBuyer=" + disputeOpenerIsBuyer +
                ", disputeOpenerIsOfferer=" + disputeOpenerIsOfferer +
                ", openingDate=" + openingDate +
                ", traderPubKeyRing=" + traderPubKeyRing +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", buyerContractSignature='" + offererContractSignature + '\'' +
                ", sellerContractSignature='" + takerContractSignature + '\'' +
                ", arbitratorPubKeyRing=" + arbitratorPubKeyRing +
                ", disputeDirectMessages=" + disputeDirectMessages +
                ", disputeDirectMessagesAsObservableList=" + disputeDirectMessagesAsObservableList +
                ", isClosed=" + isClosed +
                ", disputeResult=" + disputeResult +
                ", disputeResultProperty=" + disputeResultProperty +
                '}';
    }
}
