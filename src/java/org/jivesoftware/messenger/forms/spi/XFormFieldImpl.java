/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.forms.spi;

import org.jivesoftware.messenger.forms.FormField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * A concrete FormField capable of sending itself to a writer and recover its state from an XMPP
 * stanza.
 *
 * @author gdombiak
 */
public class XFormFieldImpl implements FormField {

    private String description;
    private boolean required = false;
    private String label;
    private String variable;
    private String type;
    private List<Option> options = new ArrayList<Option>();
    private List<String> values = new ArrayList<String>();

    public XFormFieldImpl() {
        super();
    }

    public XFormFieldImpl(String variable) {
        this.variable = variable;
    }

    public String getNamespace() {
        // Is someone sending this message?
        return "jabber:x:data";
    }

    public void setNamespace(String namespace) {
        // Is someone sending this message?
        // Do nothing
    }

    public String getName() {
        // Is someone sending this message?
        return "x";
    }

    public void setName(String name) {
        // Is someone sending this message?
        // Do nothing
    }

    public Element asXMLElement() {
        Element field = DocumentHelper.createElement("field");
        if (getLabel() != null) {
            field.addAttribute("label", getLabel());
        }
        if (getVariable() != null) {
            field.addAttribute("var", getVariable());
        }
        if (getType() != null) {
            field.addAttribute("type", getType());
        }

        if (getDescription() != null) {
            field.addElement("desc").addText(getDescription());
        }
        if (isRequired()) {
            field.addElement("required");
        }
        // Loop through all the values and append them to the stream writer
        if (values.size() > 0) {
            Iterator<String> valuesItr = getValues();
            while (valuesItr.hasNext()) {
                field.addElement("value").addText(valuesItr.next());
            }
        }
        // Loop through all the options and append them to the stream writer
        if (options.size() > 0) {
            Iterator<Option> optionsItr = getOptions();
            while (optionsItr.hasNext()) {
                field.add((optionsItr.next()).asXMLElement());
            }
        }

        // Loop through all the values and append them to the stream writer
        /*Iterator frags = fragments.iterator();
        while (frags.hasNext()){
            XMPPFragment frag = (XMPPFragment) frags.next();
            frag.send(xmlSerializer,version);
        }*/

        return field;
    }

    public void addValue(String value) {
        if (value == null) {
            value = "";
        }
        synchronized (values) {
            values.add(value);
        }
    }

    public void clearValues() {
        synchronized (values) {
            values.clear();
        }
    }

    public void addOption(String label, String value) {
        synchronized (options) {
            options.add(new Option(label, value));
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public String getVariable() {
        return variable;
    }

    public Iterator<String> getValues() {
        synchronized (values) {
            return Collections.unmodifiableList(new ArrayList<String>(values)).iterator();
        }
    }

    public String getType() {
        return type;
    }

    /**
     * Returns an Iterator for the available options that the user has in order to answer
     * the question.
     *
     * @return Iterator for the available options.
     */
    private Iterator<Option> getOptions() {
        synchronized (options) {
            return Collections.unmodifiableList(new ArrayList<Option>(options)).iterator();
        }
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public void parse(Element formElement) {
        variable = formElement.attributeValue("var");
        setLabel(formElement.attributeValue("label"));
        setType(formElement.attributeValue("type"));

        Element descElement = formElement.element("desc");
        if (descElement != null) {
            setDescription(descElement.getTextTrim());
        }
        if (formElement.element("required") != null) {
            setRequired(true);
        }
        Iterator valueElements = formElement.elementIterator("value");
        while (valueElements.hasNext()) {
            addValue(((Element)valueElements.next()).getTextTrim());
        }
        Iterator optionElements = formElement.elementIterator("option");
        Element optionElement;
        while (optionElements.hasNext()) {
            optionElement = (Element)optionElements.next();
            addOption(optionElement.attributeValue("label"), optionElement.elementTextTrim("value"));
        }
    }

    public String toString() {
        return "XFormFieldImpl " + Integer.toHexString(hashCode()) + " " + getVariable() + ">" + values
                + " o: " + (options.isEmpty() ? "no options" : options.toString());
    }

    /**
     * Represents the available option of a given FormField.
     *
     * @author Gaston Dombiak
     */
    private static class Option {
        private String label;
        private String value;

        public Option(String label, String value) {
            this.label = label;
            this.value = value;
        }

        /**
         * Returns the label that represents the option.
         *
         * @return the label that represents the option.
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the value of the option.
         *
         * @return the value of the option.
         */
        public String getValue() {
            return value;
        }

        public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
            xmlSerializer.writeStartElement("jabber:x:data", "option");
            if (getLabel() != null) {
                xmlSerializer.writeAttribute("label", getLabel());
            }
            if (getValue() != null) {
                xmlSerializer.writeStartElement("jabber:x:data", "value");
                xmlSerializer.writeCharacters(getValue());
                xmlSerializer.writeEndElement();
            }
            xmlSerializer.writeEndElement();
        }

        public Element asXMLElement() {
            Element option = DocumentHelper.createElement("option");
            if (getLabel() != null) {
                option.addAttribute("label", getLabel());
            }
            if (getValue() != null) {
                option.addElement("value").addText(getValue());
            }
            return option;
        }
    }
}