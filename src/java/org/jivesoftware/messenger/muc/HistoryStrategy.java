/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.muc;

import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.JiveGlobals;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * <p>Multi-User Chat rooms may cache history of the conversations in the room in order to
 * play them back to newly arriving members.</p>
 * 
 * <p>This class is an internal component of MUCRoomHistory that describes the strategy that can 
 * be used, and provides a method of administering the history behavior.</p>
 *
 * @author Gaston Dombiak
 * @author Derek DeMoro
 */
public class HistoryStrategy {

    /**
     * The type of strategy being used.
     */
    private Type type = Type.number;

    /**
     * List containing the history of messages.
     */
    private LinkedList history = new LinkedList();
    /**
     * Default max number.
     */
    private static final int DEFAULT_MAX_NUMBER = 25;
    /**
     * The maximum number of chat history messages stored for the room.
     */
    private int maxNumber;
    /**
     * The parent history used for default settings, or null if no parent
     * (chat server defaults).
     */
    private HistoryStrategy parent;
    /**
     * Track the latest room subject change or null if none exists yet.
     */
    private Message roomSubject = null;
    /**
     * The string prefix to be used on the context property names
     * (do not include trailing dot).
     */
    private String contextPrefix = null;

    /**
     * Create a history strategy with the given parent strategy (for defaults) or null if no 
     * parent exists.
     *
     * @param parentStrategy The parent strategy of this strategy or null if none exists.
     */
    public HistoryStrategy(HistoryStrategy parentStrategy) {
        this.parent = parentStrategy;
        if (parent == null) {
            maxNumber = DEFAULT_MAX_NUMBER;
        }
        else {
            type = Type.defaulType;
            maxNumber = parent.getMaxNumber();
        }
    }

    /**
     * Obtain the maximum number of messages for strategies using message number limitations.
     *
     * @return The maximum number of messages to store in applicable strategies.
     */
    public int getMaxNumber() {
        return maxNumber;
    }

    /**
     * Set the maximum number of messages for strategies using message number limitations.
     *
     * @param max The maximum number of messages to store in applicable strategies.
     */
    public void setMaxNumber(int max) {
        this.maxNumber = max;
        if (contextPrefix != null){
            JiveGlobals.setProperty(contextPrefix + ".maxNumber",
                                    Integer.toString(maxNumber));
        }
    }

    /**
     * Set the type of history strategy being used.
     *
     * @param newType The new type of chat history to use.
     */
    public void setType(Type newType){
        if (newType != null){
            type = newType;
        }
        if (contextPrefix != null){
            JiveGlobals.setProperty(contextPrefix + ".type", type.toString());
        }
    }

    /**
     * Obtain the type of history strategy being used.
     *
     * @return The current type of strategy being used.
     */
    public Type getType(){
        return type;
    }

    /**
     * Add a message to the current chat history. The strategy type will determine what 
     * actually happens to the message.
     *
     * @param packet The packet to add to the chatroom's history.
     */
    public void addMessage(Message packet){
        // get the conditions based on default or not
        Type strategyType;
        int strategyMaxNumber;
        if (type == Type.defaulType && parent != null) {
            strategyType = parent.getType();
            strategyMaxNumber = parent.getMaxNumber();
        }
        else {
            strategyType = type;
            strategyMaxNumber = maxNumber;
        }

        // Room subject change messages are special
        boolean subjectChange = false;
        if (packet.getSubject() != null && packet.getSubject().length() > 0){
            subjectChange = true;
            roomSubject = packet;
        }

        // store message according to active strategy
        if (strategyType == Type.none){
            if (subjectChange) {
                history.clear();
                history.add(packet);
            }
        }
        else if (strategyType == Type.all) {
            history.addLast(packet);
        }
        else if (strategyType == Type.number) {
            if (history.size() >= strategyMaxNumber) {
                // We have to remove messages so the new message won't exceed
                // the max history size
                // This is complicated somewhat because we must skip over the
                // last room subject
                // message because we want to preserve the room subject if
                // possible.
                ListIterator historyIter = history.listIterator();
                while (historyIter.hasNext() && history.size() > strategyMaxNumber) {
                    if (historyIter.next() != roomSubject) {
                        historyIter.remove();
                    }
                }
            }
            history.addLast(packet);
        }
    }

    /**
     * Obtain the current history as an iterator of messages to play back to a new room member.
     * 
     * @return An iterator of Message objects to be sent to the new room member.
     */
    public Iterator getMessageHistory(){
        LinkedList list = new LinkedList(history);
        return list.iterator();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list 
     * iterator will be positioned at the end of the history so senders of this message must 
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator getReverseMessageHistory(){
        LinkedList list = new LinkedList(history);
        return list.listIterator(list.size());
    }

    /**
     * Strategy type.
     */
    public enum Type {
        defaulType, none, all, number;
    }

    /**
     * Obtain the strategy type from string name. See the Type enumeration name
     * strings for the names strings supported. If nothing matches
     * and parent != null DEFAULT is used, otherwise, NUMBER is used.
     *
     * @param typeName The text name of the strategy type
     */
    public void setTypeFromString(String typeName) {
        try {
            setType(Type.valueOf(typeName));
        }
        catch (IllegalArgumentException ie) {
            if (parent != null) {
                setType(Type.defaulType);
            }
            else {
                setType(Type.number);
            }
        }
    }

    /**
     * Sets the prefix to use for retrieving and saving settings (and also
     * triggers an immediate loading of properties).
     *
     * @param prefix the prefix to use (without trailing dot) on property names.
     */
    public void setContext(String prefix) {
        this.contextPrefix = prefix;
        setTypeFromString(JiveGlobals.getProperty(prefix + ".type"));
        String maxNumberString = JiveGlobals.getProperty(prefix + ".maxNumber");
        if (maxNumberString != null && maxNumberString.trim().length() > 0){
            try {
                setMaxNumber(Integer.parseInt(maxNumberString));
            }catch (Exception e){
                Log.info("Jive property "
                        + prefix + ".maxNumber not a valid number.");
            }
        }
    }
}
