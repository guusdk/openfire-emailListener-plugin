<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    // Get parameters

    String serverType = ParamUtils.getParameter(request, "serverType");
    // Server type should never be null, but if it is, assume "other"
    if (serverType == null) {
        serverType = "other";
    }

    // Determine the right default values based on the the server type.
    String defaultGroupNameField = JiveGlobals.getXMLProperty("ldap.groupNameField");
    String defaultGroupMemberField = JiveGlobals.getXMLProperty("ldap.groupMemberField");
    String defaultGroupDescriptionField = JiveGlobals.getXMLProperty("ldap.groupDescriptionField");
    String posixModeString = JiveGlobals.getXMLProperty("ldap.posixMode");
    boolean defaultPosixMode = Boolean.parseBoolean(posixModeString);
    String defaultGroupSearchFilter = JiveGlobals.getXMLProperty("ldap.groupSearchFilter");

    if (serverType.equals("activedirectory")) {
        if (defaultGroupNameField == null) {
            defaultGroupNameField = "cn";
        }
        if (defaultGroupMemberField == null) {
            defaultGroupMemberField = "member";
        }
        if (defaultGroupDescriptionField == null) {
            defaultGroupDescriptionField = "description";
        }
        if (posixModeString == null) {
            defaultPosixMode = false;
        }
        if (defaultGroupSearchFilter == null) {
            defaultGroupSearchFilter = "(objectClass=group)";
        }
    }
    else {
        if (defaultGroupNameField == null) {
            defaultGroupNameField = "cn";
        }
        if (defaultGroupMemberField == null) {
            defaultGroupMemberField = "member";
        }
        if (defaultGroupDescriptionField == null) {
            defaultGroupDescriptionField = "description";
        }
        if (posixModeString == null) {
            defaultPosixMode = false;
        }
    }

    String groupNameField = ParamUtils.getParameter(request, "groupNameField");
    if (groupNameField == null) {
        groupNameField = defaultGroupNameField;
    }
    String groupMemberField = ParamUtils.getParameter(request, "groupMemberField");
    if (groupMemberField == null) {
        groupMemberField = defaultGroupMemberField;
    }
    String groupDescriptionField = ParamUtils.getParameter(request, "groupDescriptionField");
    if (groupDescriptionField == null) {
        groupDescriptionField = defaultGroupDescriptionField;
    }
    String posixModeParam = ParamUtils.getParameter(request, "posixMode");
    boolean posixMode;
    if (posixModeParam == null) {
        posixMode = defaultPosixMode;
    }
    else {
        posixMode = Boolean.parseBoolean(posixModeParam);
    }
    String groupSearchFilter = ParamUtils.getParameter(request, "groupSearchFilter");
    if (groupSearchFilter == null) {
        groupSearchFilter = defaultGroupSearchFilter;
    }

    boolean save = request.getParameter("save") != null;
    if (save) {
        if (groupNameField != null) {
            JiveGlobals.setXMLProperty("ldap.groupNameField", groupNameField);
        }
        if (groupMemberField != null) {
            JiveGlobals.setXMLProperty("ldap.groupMemberField", groupMemberField);
        }
        if (groupDescriptionField != null) {
            JiveGlobals.setXMLProperty("ldap.groupDescriptionField", groupDescriptionField);
        }
        JiveGlobals.setXMLProperty("ldap.posixMode", Boolean.toString(posixMode));
        if (groupSearchFilter != null) {
            JiveGlobals.setXMLProperty("ldap.groupSearchFilter", groupSearchFilter);
        }

        // Enable the LDAP auth provider. The LDAP user provider will be enabled on the next step.
        JiveGlobals.setXMLProperty("provider.group.className",
                "org.jivesoftware.wildfire.ldap.LdapGroupProvider");

        // Redirect
        response.sendRedirect("setup-admin-settings.jsp?ldap=true");
        return;
    }
%>
<html>
<head>
    <title><fmt:message key="setup.ldap.title" /></title>
    <meta name="currentStep" content="3"/>
</head>

<body>

	<h1><fmt:message key="setup.ldap.profile" />: <span><fmt:message key="setup.ldap.group_mapping" /></h1>

	<!-- BEGIN jive-contentBox_stepbar -->
	<div id="jive-contentBox_stepbar">
		<span class="jive-stepbar_step"><em>1. <fmt:message key="setup.ldap.connection_settings" /></em></span>
		<span class="jive-stepbar_step"><em>2. <fmt:message key="setup.ldap.user_mapping" /></em></span>
		<span class="jive-stepbar_step"><strong>3. <fmt:message key="setup.ldap.group_mapping" /></strong></span>
	</div>
	<!-- END jive-contentBox-stepbar -->

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox jive-contentBox_for-stepbar">

	<h2><fmt:message key="setup.ldap.step_three" />: <span><fmt:message key="setup.ldap.group_mapping" /></span></h2>
	<p><fmt:message key="setup.ldap.group.description" /></p>

	<form action="" method="get">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">

			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			<td colspan="2"><strong><fmt:message key="setup.ldap.group_mapping" /></strong></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.group.name_field" />:</td>
			<td><input type="text" name="groupNameField" id="jiveLDAPgroupname" size="22" maxlength="50" value="<%= groupNameField!=null?groupNameField:""%>"><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.group.name_field_description" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.group.member_field" />:</td>
			<td><input type="text" name="groupMemberField" id="jiveLDAPgroupmember" size="22" maxlength="50" value="<%= groupMemberField!=null?groupMemberField:""%>"><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.group.member_field_description" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.group.description_field" />:</td>
			<td><input type="text" name="groupDescriptionField" id="jiveLDAPgroupdesc" size="22" maxlength="50" value="<%= groupDescriptionField!=null?groupDescriptionField:""%>"><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.group.description_field_description" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span></td>
			</tr>
			</table>

			<!-- BEGIN jiveAdvancedButton -->
			<div class="jiveAdvancedButton jiveAdvancedButtonTopPad">
				<a href="#" onclick="togglePanel(jiveAdvanced); return false;" id="jiveAdvancedLink"><fmt:message key="setup.ldap.advanced" /></a>
			</div>
			<!-- END jiveAdvancedButton -->

			<!-- BEGIN jiveAdvancedPanelu (advanced user mapping settings) -->
				<div class="jiveadvancedPanelu" id="jiveAdvanced" style="display: none;">
					<div>
						<table border="0" cellpadding="0" cellspacing="2">
						<tr>
						<td align="right"><fmt:message key="setup.ldap.group.posix" />:</td>
						<td><span style="float: left;">
							<input type="radio" name="posixMode" value="true" style="float: none;" id="posix1" <% if(posixMode) {%>checked<% } %>><label for="posix1"> <fmt:message key="global.yes" />  </label>
							<input type="radio" name="posixMode" value="false" style="float: none;" id="posix2" <% if(!posixMode) {%>checked<% } %>><label for="posix2"> <fmt:message key="global.no" />  </label>
							</span>
							<span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.group.posix_description" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span></td>
						</tr>
						<tr>
						<td align="right"><fmt:message key="setup.ldap.group.filter" /></td>
						<td><input type="text" name="groupSearchFilter" value="<%= groupSearchFilter!=null?groupSearchFilter:""%>" id="jiveLDAPgroupsearchfilter" size="22" maxlength="250"><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.group.filter_description" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span></td>
						</tr>
						</table>
					</div>
				</div>
			<!-- END jiveAdvancedPanelu (advanced user mapping settings) -->

		</div>
		<!-- END jive-contentBox_bluebox -->



		<!-- BEGIN jive-buttons -->
		<div class="jive-buttons">

			<!-- BEGIN right-aligned buttons -->
			<div align="right">
				<%--<a href="setup-ldap-group_test.jsp" class="lbOn" id="jive-setup-test2">
				<img src="../images/setup_btn_gearplay.gif" alt="" width="14" height="14" border="0">
                <fmt:message key="setup.ldap.test" />
				</a>--%>

				<input type="Submit" name="save" value="<fmt:message key="setup.ldap.continue" />" id="jive-setup-save" border="0">
			</div>
			<!-- END right-aligned buttons -->

		</div>
		<!-- END jive-buttons -->

	</form>

	</div>
	<!-- END jive-contentBox -->



</body>
</html>
