/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chatbot.spi;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotInfo;
import java.util.Date;

public class BasicChatbotInfo implements ChatbotInfo {
    private long id;
    private String description;
    private Date creationDate;
    private Date modificationDate;

    public BasicChatbotInfo(long id) {
        this.id = id;
        description = "None";
        creationDate = new Date();
        modificationDate = creationDate;
    }

    public BasicChatbotInfo(long id,
                            String description,
                            Date creationDate,
                            Date modificationDate) {
        this.id = id;
        this.description = description;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
    }

    public long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) throws UnauthorizedException {
        modificationDate = new Date();
        this.description = description;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) throws UnauthorizedException {
        modificationDate = new Date();
        this.creationDate = creationDate;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) throws UnauthorizedException {
        this.modificationDate = modificationDate;
    }
}
