<%--
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="java.util.zip.ZipFile,
                 java.util.jar.JarFile,
                 java.util.jar.JarEntry,
                 java.io.*,
                 org.dom4j.io.SAXReader,
                 org.dom4j.Document,
                 org.dom4j.Element,
                 org.dom4j.Node,
                 java.text.DateFormat,
                 org.jivesoftware.admin.AdminPageBean,
				 org.jivesoftware.messenger.XMPPServer,
				 org.jivesoftware.messenger.container.PluginManager,
				 org.jivesoftware.util.*,
                 org.jivesoftware.messenger.container.Plugin,
                 java.util.*"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
	String deletePlugin = ParamUtils.getParameter(request, "deleteplugin");
	String refreshPlugin = ParamUtils.getParameter(request, "refreshplugin");

	final PluginManager pluginManager = webManager.getXMPPServer().getPluginManager();

    List<Plugin> plugins = new ArrayList<Plugin>(pluginManager.getPlugins());

    if (plugins != null) {
        Collections.sort(plugins, new Comparator<Plugin>() {
            public int compare(Plugin p1, Plugin p2) {
                return pluginManager.getName(p1).compareTo(pluginManager.getName(p2));
            }
        });
    }
    
    if (deletePlugin != null) {
        File pluginDir = pluginManager.getPluginDirectory(pluginManager.getPlugin(deletePlugin));
		File pluginJar = new File(pluginDir.getParent(), pluginDir.getName() + ".jar");
        // Also try the .war extension.
        if (!pluginJar.exists()) {
            pluginJar = new File(pluginDir.getParent(), pluginDir.getName() + ".war");
        }
        pluginJar.delete();
        pluginManager.unloadPlugin(pluginDir.getName());
        response.sendRedirect("plugin-admin.jsp?deletesuccess=true");
        return;
	}
	
	if (refreshPlugin != null) {		
		for (Plugin plugin : plugins) {
            File pluginDir = pluginManager.getPluginDirectory(plugin);
			if (refreshPlugin.equals(pluginDir.getName())) {
				pluginManager.unloadPlugin(refreshPlugin);
				response.sendRedirect("plugin-admin.jsp?refrehsuccess=true");
                return;
			}
		}		
	}
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    String title = LocaleUtils.getLocalizedString("plugin.admin.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "plugin-admin.jsp"));
    pageinfo.setPageID("plugin-settings");    
%>

<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<% if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label"><fmt:message key="plugin.admin.deleted_success" /></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } else if ("false".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label"><fmt:message key="plugin.admin.deleted_failure" /></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } %>

<% if ("true".equals(request.getParameter("refrehsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label"><fmt:message key="plugin.admin.refresh_success" /></td></tr>
    </tbody>
    </table>
    </div>
    <br>

<% } %>

<p>
<fmt:message key="plugin.admin.info" />
</p>
<p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="plugin.admin.name" /></th>
        <th nowrap><fmt:message key="plugin.admin.description" /></th>
        <th nowrap><fmt:message key="plugin.admin.version" /></th>
        <th nowrap><fmt:message key="plugin.admin.author" /></th>
        <th nowrap><fmt:message key="plugin.admin.restart" /></th>
        <th nowrap><fmt:message key="plugin.admin.delete" /></th>
    </tr>
</thead>
<tbody>

<%
	// If only the admin plugin is installed, show "none".
    if (plugins.size() == 1) {
%>
    <tr>
        <td align="center" colspan="7"><fmt:message key="plugin.admin.no_plugin" /></td>
    </tr>
<%
    }

    int count = 0;
    for (int i=0; i<plugins.size(); i++) {
        Plugin plugin = plugins.get(i);
        String dirName = pluginManager.getPluginDirectory(plugin).getName();
        // Skip the admin plugin.
        if (!"admin".equals(dirName)) {
            count++;
            String pluginName = pluginManager.getName(plugin);
            String pluginDescription = pluginManager.getDescription(plugin);
            String pluginAuthor = pluginManager.getAuthor(plugin);
            String pluginVersion = pluginManager.getVersion(plugin);
%>

	    <tr class="jive-<%= (((count%2)==0) ? "even" : "odd") %>">
	        <td width="1%">
	            <%= count %>
	        </td>
	        <td width="20%" nowrap>
	            <%= (pluginName != null ? pluginName : dirName) %> &nbsp;
	        </td>
	        <td width="60%">
	            <%= pluginDescription != null ? pluginDescription : "" %>  &nbsp;
	        </td>
	        <td width="5%" align="center">
	             <%= pluginVersion != null ? pluginVersion : "" %>  &nbsp;
	        </td>
	        <td width="15%" nowrap>
	             <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
	        </td>
	        <td width="1%" align="center">
	            <a href="plugin-admin.jsp?refreshplugin=<%= dirName %>"
	             title="<fmt:message key="plugin.admin.click_refresh" />"
	             ><img src="images/refresh-16x16.gif" width="16" height="16" border="0"></a>
	        </td>
	        <td width="1%" align="center" style="border-right:1px #ccc solid;">
	            <a href="#" onclick="if (confirm('<fmt:message key="plugin.admin.confirm" />')) { location.replace('plugin-admin.jsp?deleteplugin=<%= dirName %>'); } "
	             title="<fmt:message key="plugin.admin.click_delete" />"
	             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
	        </td>
	    </tr>
<%		    
        }
    }
%>
</tbody>
</table>
</div>

<jsp:include page="bottom.jsp" flush="true" />