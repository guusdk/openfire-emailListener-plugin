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
package org.jivesoftware.messenger.muc.spi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.muc.ConflictException;
import org.jivesoftware.messenger.muc.ForbiddenException;
import org.jivesoftware.messenger.muc.MUCRoom;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.handler.IQHandler;

/**
 * This class is not an actual IQHandler since all the packets with namespace
 * jabber:iq:register will be handled by IQRegisterHandler. So currently IQRegisterHandler is 
 * delegating the responsibility for managing room registation to this class. In the future, when we
 * implement the component JEP we will have to review this design since this is a temporary 
 * solution. Most probably this class will not inherit from IQHandler but for now this is
 * a workaround that allows us to avoid the need to create a new interface. Note: The abstract class
 * of this class is used in IQRegisterHandler to model any possible delegate.<p>
 * 
 * However, the idea of having this class as a delegate will still persist in the future since 
 * MultiUserChatServer will be the main target for the room registration packets and that class 
 * will delegate the responsiblity to this class.
 * 
 * @author Gaston Dombiak
 */
public class IQMUCRegisterHandler extends IQHandler {

    private static MetaDataFragment probeResult;
    private IQHandlerInfo info;
    private MultiUserChatServer mucServer;

    public IQMUCRegisterHandler(MultiUserChatServer mucServer) {
        super("XMPP MUC Registration Handler");
        info = new IQHandlerInfo("query", "jabber:iq:register");
        this.mucServer = mucServer;
        initialize();
    }

    public void initialize() {
        if (probeResult == null) {
            // Create the basic element of the probeResult which contains the basic registration
            // information for the room (e.g. first name, last name, nickname, etc.)
            Element element = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));

            XDataFormImpl registrationForm = new XDataFormImpl(DataForm.TYPE_FORM);
            registrationForm.setTitle(LocaleUtils.getLocalizedString("muc.form.reg.title"));
            registrationForm.addInstruction(LocaleUtils
                    .getLocalizedString("muc.form.reg.instruction"));

            XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
            field.setType(FormField.TYPE_HIDDEN);
            field.addValue("http://jabber.org/protocol/muc#user");
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_first");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.first-name"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_last");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.last-name"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_roomnick");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.nickname"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_url");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.url"));
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_email");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.email"));
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#user_faqentry");
            field.setType(FormField.TYPE_TEXT_MULTI);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.faqentry"));
            registrationForm.addField(field);

            // Create the probeResult and add the basic info together with the registration form
            probeResult = new MetaDataFragment(element);
            probeResult.addFragment(registrationForm);
        }
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException, XMLStreamException {
        Session session = packet.getOriginatingSession();
        IQ reply = null;
        if (IQ.GET.equals(packet.getType())) {
            reply = packet.createResult();
            reply.setChildFragment(probeResult);
        }
        else if (IQ.SET.equals(packet.getType())) {
            try {
                reply = packet.createResult();

                XMPPFragment iq = packet.getChildFragment();
                Element formElement = ((XMPPDOMFragment)iq).getRootElement().element("x");
                // Check if a form was used to provide the registration info
                if (formElement != null) {
                    // Get the sent form
                    XDataFormImpl registrationForm = new XDataFormImpl();
                    registrationForm.parse(formElement);
                    // Get the desired nickname sent in the form
                    Iterator values = registrationForm.getField("muc#user_roomnick").getValues();
                    String nickname = (values.hasNext() ? (String)values.next() : null);
                    
                    // TODO The rest of the fields of the form are ignored. If we have a requirement
                    // in the future where we need those fields we'll have to change 
                    // MUCRoom.addMember in order to receive a RegistrationInfo (new class) 
                    
                    // Get the target room
                    MUCRoom room = mucServer.getChatRoom(packet.getRecipient().getNamePrep());
                    if (room != null) {
                        // Keep a registry of the updated presences
                        List presences = new ArrayList();
                        // Add the new member to the members list
                        presences.addAll(room.addMember(
                                packet.getSender().toBareStringPrep(),
                                nickname,
                                room.getRole()));
                        // Send the updated presences to the room occupants
                        try {
                            for (Iterator it = presences.iterator(); it.hasNext();) {
                                room.send((Presence)it.next());
                            }
                        }
                        catch (UnauthorizedException e) {
                            // Do nothing
                        }
                    }
                    else {
                        reply.setError(XMPPError.Code.NOT_FOUND);
                    }
                }
                else {
                    reply.setError(XMPPError.Code.BAD_REQUEST);
                }
            }
            catch (ForbiddenException e) {
                reply = packet.createResult();
                reply.setError(XMPPError.Code.FORBIDDEN);
            }
            catch (ConflictException e) {
                reply = packet.createResult();
                reply.setError(XMPPError.Code.CONFLICT);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (reply != null) {
            // why is this done here instead of letting the iq handler do it?
            session.getConnection().deliver(reply);
        }
        return null;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

}
