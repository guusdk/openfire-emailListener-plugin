/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

import org.jivesoftware.messenger.container.ModuleProperties;
import java.io.*;
import java.util.*;
import org.dom4j.Element;

/**
 * Provides the the ability to use simple XML property files. Each property is
 * in the form X.Y.Z, which would map to an XML snippet of:
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z&gt;someValue&lt;/Z&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * <p/>
 * The XML file is passed in to the constructor and must be readable and
 * writtable. Setting property values will automatically persist those value
 * to disk. The file encoding used is UTF-8.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class XMLProperties implements ModuleProperties {

    private File file;
    private Element rootElement;

    /**
     * Parsing the XML file every time we need a property is slow. Therefore,
     * we use a Map to cache property values that are accessed more than once.
     */
    private Map propertyCache = new HashMap();

    /**
     * <p>Creates a sub-property object rooted at the given root element.</p>
     *
     * @param propFile The property file this XML property should be saved to
     * @param root     The root element for the properties
     */
    private XMLProperties(File propFile, Element root) {
        file = propFile;
        rootElement = root;
    }

    /**
     * Creates a new XMLProperties object.
     *
     * @param fileName the full path the file that properties should be read from
     *                 and written to.
     */
    public XMLProperties(String fileName) throws IOException {
        this.file = new File(fileName);
        if (!file.exists()) {
            // Attempt to recover from this error case by seeing if the
            // tmp file exists. It's possible that the rename of the
            // tmp file failed the last time Jive was running,
            // but that it exists now.
            File tempFile;
            tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            if (tempFile.exists()) {
                Log.error("WARNING: " + fileName + " was not found, but temp file from " +
                        "previous write operation was. Attempting automatic recovery." +
                        " Please check file for data consistency.");
                tempFile.renameTo(file);
            }
            // There isn't a possible way to recover from the file not
            // being there, so throw an error.
            else {
                throw new FileNotFoundException("XML properties file does not exist: "
                        + fileName);
            }
        }
        // Check read and write privs.
        if (!file.canRead()) {
            throw new IOException("XML properties file must be readable: " + fileName);
        }
        if (!file.canWrite()) {
            throw new IOException("XML properties file must be writable: " + fileName);
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            rootElement = XPPReader.parseDocument(reader,
                    this.getClass()).getRootElement();
        }
        catch (Exception e) {
            Log.error("Error reading XML properties file " + fileName + ".", e);
            throw new IOException(e.getMessage());
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name) {
        String value = (String)propertyCache.get(name);
        if (value != null) {
            return value;
        }

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = rootElement;
        for (int i = 0; i < propName.length; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return null.
                return null;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        value = element.getTextTrim();
        if ("".equals(value)) {
            return null;
        }
        else {
            // Add to cache so that getting property next time is fast.
            propertyCache.put(name, value);
            return value;
        }
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public String[] getProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = rootElement;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        Iterator iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList props = new ArrayList();
        String value;
        while (iter.hasNext()) {
            // Empty strings are skipped.
            value = ((Element)iter.next()).getTextTrim();
            if (!"".equals(value)) {
                props.add(value);
            }
        }
        String[] childrenNames = new String[props.size()];
        return (String[])props.toArray(childrenNames);
    }

    public ModuleProperties createChildProperty(String name) {

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = rootElement;
        for (int i = 0; i < propName.length - 1; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        element = element.addElement(propName[propName.length - 1]);
        // write the XML properties to disk
        saveProperties();
        return new XMLProperties(file, element);
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public Iterator getChildProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = rootElement;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return Collections.EMPTY_LIST.iterator();
            }
        }
        // We found matching property, return names of children.
        Iterator iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList props = new ArrayList();
        while (iter.hasNext()) {
            props.add(new XMLProperties(file, (Element)iter.next()));
        }
        return props.iterator();
    }

    /**
     * Sets a property to an array of values. Multiple values matching the same property
     * is mapped to an XML file as multiple elements containing each value.
     * For example, using the name "foo.bar.prop", and the value string array containing
     * {"some value", "other value", "last value"} would produce the following XML:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name   the name of the property.
     * @param values The array of values for the property (can be empty but not null)
     */
    public void setProperties(String name, String[] values) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = rootElement;
        for (int i = 0; i < propName.length - 1; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        String childName = propName[propName.length - 1];
        // We found matching property, clear all children.
        List toRemove = new ArrayList();
        Iterator iter = element.elementIterator(childName);
        while (iter.hasNext()) {
            toRemove.add(iter.next());
        }
        for (iter = toRemove.iterator(); iter.hasNext();) {
            element.remove((Element)iter.next());
        }
        // Add the new children
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                element.addElement(childName).setText(values[i]);
            }
        }
        saveProperties();
    }

    /**
     * Return all children property names of a parent property as a String array,
     * or an empty array if the if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>, then
     * the child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt>.
     *
     * @param parent the name of the parent property.
     * @return all child property values for the given parent.
     */
    public String[] getChildrenProperties(String parent) {
        String[] propName = parsePropertyName(parent);
        // Search for this property by traversing down the XML heirarchy.
        Element element = rootElement;
        for (int i = 0; i < propName.length; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        List children = element.elements();
        int childCount = children.size();
        String[] childrenNames = new String[childCount];
        for (int i = 0; i < childCount; i++) {
            childrenNames[i] = ((Element)children.get(i)).getName();
        }
        return childrenNames;
    }

    /**
     * Sets the value of the specified property. If the property doesn't
     * currently exist, it will be automatically created.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     */
    public synchronized void setProperty(String name, String value) {
        if (name == null) return;
        if (value == null) value = "";

        // Set cache correctly with prop name and value.
        propertyCache.put(name, value);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = rootElement;
        for (int i = 0; i < propName.length; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        // Set the value of the property in this node.
        element.setText(value);
        // write the XML properties to disk
        saveProperties();
    }

    /**
     * Deletes the specified property.
     *
     * @param name the property to delete.
     */
    public synchronized void deleteProperty(String name) {
        // Remove property from cache.
        propertyCache.remove(name);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = rootElement;
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            // Can't find the property so return.
            if (element == null) {
                return;
            }
        }
        // Found the correct element to remove, so remove it...
        element.remove(element.element(propName[propName.length - 1]));
        // .. then write to disk.
        saveProperties();
    }

    /**
     * Saves the properties to disk as an XML document. A temporary file is
     * used during the writing process for maximum safety.
     */
    private synchronized void saveProperties() {
        boolean error = false;
        // Write data out to a temporary file first.
        File tempFile = null;
        Writer writer = null;
        try {
            tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            writer = new FileWriter(tempFile);
            XPPWriter.write(rootElement.getDocument(), writer);
        }
        catch (Exception e) {
            Log.error(e);
            // There were errors so abort replacing the old property file.
            error = true;
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (IOException e1) {
                    Log.error(e1);
                    error = true;
                }
            }
        }

        // No errors occured, so delete the main file.
        if (!error) {
            // Delete the old file so we can replace it.
            if (!file.delete()) {
                Log.error("Error deleting property file: " + file.getAbsolutePath());
                return;
            }
            // Copy new contents to the file.
            try {
                FileCopier.copy(tempFile, file);
            }
            catch (Exception e) {
                Log.error(e);
                // There were errors so abort replacing the old property file.
                error = true;
            }
            // If no errors, delete the temp file.
            if (!error) {
                tempFile.delete();
            }
        }
    }

    /**
     * Returns an array representation of the given Jive property. Jive
     * properties are always in the format "prop.name.is.this" which would be
     * represented as an array of four Strings.
     *
     * @param name the name of the Jive property.
     * @return an array representation of the given Jive property.
     */
    private String[] parsePropertyName(String name) {
        List propName = new ArrayList(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return (String[])propName.toArray(new String[propName.size()]);
    }

    public void setProperties(Map propertyMap) {
        /*
        Iterator iter = propertyMap.keySet().iterator();
        while (iter.hasNext()) {
            String propertyName = (String) iter.next();
            String propertyValue = (String) propertyMap.get(propertyName);
            setProperty(propertyName, propertyValue);
        }
        */
    }
}
