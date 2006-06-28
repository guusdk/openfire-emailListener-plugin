<%--
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page errorPage="error.jsp" import="org.jivesoftware.util.ByteFormat,
                                       org.jivesoftware.util.Version,
                                       org.jivesoftware.wildfire.XMPPServer,
                                       org.jivesoftware.wildfire.container.Plugin"
    %>
<%@ page import="org.jivesoftware.wildfire.container.PluginManager" %>
<%@ page import="org.jivesoftware.wildfire.update.AvailablePlugin" %>
<%@ page import="org.jivesoftware.wildfire.update.UpdateManager" %>
<%@ page import="java.io.File" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.text.SimpleDateFormat"%>
<%@ page import="org.jivesoftware.util.WebManager"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    WebManager webManager = new WebManager();
%>

<%
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    List<AvailablePlugin> plugins = updateManager.getNotInstalledPlugins();

    // Sort plugins alphabetically
    Collections.sort(plugins, new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((AvailablePlugin)o1).getName().compareTo(((AvailablePlugin)o2).getName());
        }
    });

    String updateList = request.getParameter("autoupdate");
    if(updateList != null){
        updateManager.checkForPluginsUpdates(true);
    }


    if (downloadRequested) {
        // Download and install new plugin
        updateManager.downloadPlugin(url);
    }

%>

<html>
<head>
<title><fmt:message key="plugin.available.title"/></title>
<meta name="pageID" content="available-plugins"/>

<style type="text/css">

.light-gray-border {
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
}



.table-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 0px 1px 0px;
    padding: 5px;
}

.row-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.table-header-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 0px 1px 1px;
    padding: 5px;

}

.table-header-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.line-bottom-border {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;
    border-color: #bbb;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
    padding: 5px;
}


</style>

<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>
<script type="text/javascript">
    function downloadPlugin(url, id) {
        document.getElementById(id + "-image").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        document.getElementById(id).style.background = "#FFFFF7";
        downloader.installPlugin(downloadComplete, url, id);
    }

    function downloadComplete(id) {
        document.getElementById(id).style.display = 'none';
        document.getElementById(id + "-row").style.display = '';
        setTimeout("fadeIt('" + id + "')", 3000);
    }

    function fadeIt(id) {
        Effect.Fade(id + "-row");
    }


     DWREngine.setErrorHandler(handleError);

     function handleError(error){
     }

</script>
</head>

<body>

<p>
    <fmt:message key="plugin.available.info"/>
</p>

<p>

<%if(plugins.size() == 0){ %>
<div style="padding:10px;background:#FFEBB5;border:1px solid #DEB24A;width:600px;">
    <fmt:message key="plugin.available.no.list" />&nbsp;<a href="available-plugins.jsp?autoupdate=true"><fmt:message key="plugin.available.list" /></a>
</div>
<br/>
<div style="width:800px;">
    <p>
   <fmt:message key="plugin.available.no.list.description" />
</p>

<% if(!updateManager.isServiceEnabled()){ %>
<fmt:message key="plugin.available.auto.update.currently" /> <b><fmt:message key="plugin.available.auto.update.currently.disabled" /></b>. <a href="manage-updates.jsp"><fmt:message key="plugin.available.click.here" /></a> <fmt:message key="plugin.available.change" />
<% } %>
</div>
<% } else {%>






<div class="light-gray-border" style="padding:10px;">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr style="background:#F7F7FF;">
        <td class="table-header-left">&nbsp;</td>
        <td nowrap colspan="2" class="table-header"><fmt:message key="plugin.available.open_source"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.description"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.version"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.author"/></td>
        <td nowrap class="table-header">File Size</td>
        <td nowrap class="table-header-right"><fmt:message key="plugin.available.install"/></td>
    </tr>
</thead>
<tbody>

<%
    // If only the admin plugin is installed, show "none".
    if (plugins.isEmpty()) {
%>
<tr>
    <td align="center" colspan="8"><fmt:message key="plugin.available.no_plugin"/></td>
</tr>
<%
    }

    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (plugin.isCommercial()) {
            continue;
        }
%>
<tr id="<%= plugin.hashCode()%>">
    <td width="1%" class="line-bottom-border">
        <% if (plugin.getIcon() != null) { %>
        <img src="<%= plugin.getIcon() %>" width="16" height="16" alt="Plugin">
        <% }
        else { %>
        <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
        <% } %>
    </td>
    <td width="20%" nowrap class="line-bottom-border">
        <%= (pluginName != null ? pluginName : "") %> &nbsp;
    </td>
    <td nowrap valign="top" class="line-bottom-border">
        <% if (plugin.getReadme() != null) { %>
        <a href="<%= plugin.getReadme() %>"
            ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
        <% }
        else { %> &nbsp; <% } %>
        <% if (plugin.getChangelog() != null) { %>
        <a href="<%= plugin.getChangelog() %>"
            ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
        <% }
        else { %> &nbsp; <% } %>
    </td>
    <td width="60%" class="line-bottom-border">
        <%= pluginDescription != null ? pluginDescription : "" %>
    </td>
    <td width="5%" align="center" valign="top" class="line-bottom-border">
        <%= pluginVersion != null ? pluginVersion : "" %>
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= fileSize %>
    </td>
    <td width="1%" align="center" valign="top" class="line-bottom-border">
        <%
            String updateURL = plugin.getURL();
            if (updateManager.isPluginDownloaded(updateURL)) {
        %>
        &nbsp;
        <%  }
        else { %>
        <%

        %>
        <span id="<%= plugin.hashCode() %>-image"><a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><img src="images/add-16x16.gif" width="16" height="16" border="0"
                                                                                                                                        alt="<fmt:message key="plugin.available.download" />"></a></span>

        <% } %>
    </td>
</tr>
<tr id="<%= plugin.hashCode()%>-row" style="display:none;">
    <td width="1%" class="line-bottom-border">
        <img src="<%= plugin.getIcon()%>" width="16" height="16"/>
    </td>
    <td nowrap class="line-bottom-border"><%= plugin.getName()%> <fmt:message key="plugin.available.installation.success" /></td>
    <td colspan="5" class="line-bottom-border">&nbsp;</td>
    <td class="line-bottom-border" align="center">
        <img src="images/success-16x16.gif" height="16" width="16"/>
    </td>
</tr>
<%
    }
%>
<tr><td><br/></td></tr>
<tr style="background:#F7F7FF;">
    <td class="table-header-left">&nbsp;</td>
    <td nowrap colspan="7" class="row-header"><fmt:message key="plugin.available.commercial_plugins" /></td>
</tr>
<%
    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (!plugin.isCommercial()) {
            continue;
        }
%>
<tr id="<%= plugin.hashCode()%>">
    <td width="1%" class="line-bottom-border">
        <% if (plugin.getIcon() != null) { %>
        <img src="<%= plugin.getIcon() %>" width="16" height="16" alt="Plugin">
        <% }
        else { %>
        <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
        <% } %>
    </td>
    <td width="20%" nowrap class="line-bottom-border">
        <%= (pluginName != null ? pluginName : "") %> &nbsp;
    </td>
    <td nowrap valign="top" class="line-bottom-border">
        <% if (plugin.getReadme() != null) { %>
        <a href="<%= plugin.getReadme() %>"
            ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
        <% }
        else { %> &nbsp; <% } %>
        <% if (plugin.getChangelog() != null) { %>
        <a href="<%= plugin.getChangelog() %>"
            ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
        <% }
        else { %> &nbsp; <% } %>
    </td>
    <td width="60%" class="line-bottom-border">
        <%= pluginDescription != null ? pluginDescription : "" %>
    </td>
    <td width="5%" align="center" valign="top" class="line-bottom-border">
        <%= pluginVersion != null ? pluginVersion : "" %>
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= fileSize  %>
    </td>
    <td width="1%" align="center" valign="top" class="line-bottom-border">
        <%
            String updateURL = plugin.getURL();
            if (updateManager.isPluginDownloaded(updateURL)) {
        %>
        &nbsp;
        <%  }
        else { %>

        <span id="<%= plugin.hashCode() %>-image"><a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><img src="images/add-16x16.gif" width="16" height="16" border="0"
                                                                                                                                        alt="<fmt:message key="plugin.available.download" />"></a></span>
        <% } %>
    </td>
</tr>
<tr id="<%= plugin.hashCode()%>-row" style="display:none;">
    <td width="1%" class="line-bottom-border">
        <img src="<%= plugin.getIcon()%>" width="16" height="16"/>
    </td>
    <td class="line-bottom-border"><%= plugin.getName()%> <fmt:message key="plugin.available.installation.success" /></td>
    <td colspan="5" class="line-bottom-border">&nbsp;</td>
    <td class="line-bottom-border">
        <img src="images/success-16x16.gif" height="16" width="16"/>
    </td>
</tr>
<%
    }
%>

</tbody>
</table>

</div>

<br/>


<%
    final XMPPServer server = XMPPServer.getInstance();
    Version version = server.getServerInfo().getVersion();
    List<Plugin> outdatedPlugins = new ArrayList<Plugin>();
    for (Plugin plugin : server.getPluginManager().getPlugins()) {
        String pluginVersion = server.getPluginManager().getMinServerVersion(plugin);
        if (pluginVersion != null && pluginVersion.compareTo(version.getVersionString()) > 0) {
            outdatedPlugins.add(plugin);
        }
    }

    if (outdatedPlugins.size() > 0) {
%>
    <div class="light-gray-border" style="padding:10px;">
    <p><fmt:message key="plugin.available.outdated" /><a href="http://www.jivesoftware.org/wildfire" target="_blank"><fmt:message key="plugin.available.outdated.update" /></a></p>
    <table cellpadding="0" cellspacing="0" border="0" width="100%">


        <%
            PluginManager pluginManager = server.getPluginManager();
            for (Plugin plugin : outdatedPlugins) {
                String pluginName = pluginManager.getName(plugin);
                String pluginDescription = pluginManager.getDescription(plugin);
                String pluginAuthor = pluginManager.getAuthor(plugin);
                String pluginVersion = pluginManager.getVersion(plugin);
                File pluginDir = pluginManager.getPluginDirectory(plugin);
                File icon = new File(pluginDir, "logo_small.png");
                boolean readmeExists = new File(pluginDir, "readme.html").exists();
                boolean changelogExists = new File(pluginDir, "changelog.html").exists();
                if (!icon.exists()) {
                    icon = new File(pluginDir, "logo_small.gif");
                }
        %>
        <tr>
            <td class="line-bottom-border" width="1%">
                <% if (icon.exists()) { %>
                <img src="plugin-icon.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showIcon=true&decorator=none" width="16" height="16" alt="Plugin">
                <% }
                else { %>
                <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
                <% } %>
            </td>
            <td class="line-bottom-border" width="1%" nowrap>
                <%= pluginName%>
            </td>
            <td nowrap class="line-bottom-border">
                <p><% if (readmeExists) { %>
                    <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showReadme=true&decorator=none"
                        ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
                    <% }
                    else { %> &nbsp; <% } %>
                    <% if (changelogExists) { %>
                    <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showChangelog=true&decorator=none"
                        ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                    <% }
                    else { %> &nbsp; <% } %></p>
            </td>
            <td class="line-bottom-border">
                <%= pluginDescription %>
            </td>
            <td class="line-bottom-border">
                <%= pluginVersion%>
            </td>
            <td class="line-bottom-border">
                <%= pluginAuthor%>
            </td>
        </tr>
        <% }%>
  </table>

        <%} %>

</div>
<br/>
 <% if(updateManager.isServiceEnabled()){
        String time = JiveGlobals.getProperty("update.lastCheck");
        Date date = new Date(Long.parseLong(time));
        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");
        time = format.format(date);
    %>
       <p>
        <fmt:message key="plugin.available.autoupdate" /> <%= time%>. <fmt:message key="plugin.available.autoupdate.on"/><a href="available-plugins.jsp?autoupdate=true"><fmt:message key="plugin.available.manual.update" /></a>
        </p>
           <% } %>

<%}%>
</body>
</html>