<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.admin.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" scope="page" />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Offline Messages";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "offline-messages.jsp"));
    pageinfo.setPageID("server-offline-messages");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<c:set var="success" />

<%! // Global vars and methods:

    // Strategy definitions:
    static final int BOUNCE = 1;
    static final int DROP = 2;
    static final int STORE = 3;
    static final int ALWAYS_STORE = 4;
    static final int STORE_AND_BOUNCE = 5;
    static final int STORE_AND_DROP = 6;
%>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    int strategy = ParamUtils.getIntParameter(request,"strategy",-1);
    int storeStrategy = ParamUtils.getIntParameter(request,"storeStrategy",-1);
    int quota = ParamUtils.getIntParameter(request,"quota",0);

    // Get the offline message manager
    OfflineMessageStrategy manager
            = (OfflineMessageStrategy)admin.getServiceLookup().lookup(OfflineMessageStrategy.class);

    // Update the session kick policy if requested
    Map errors = new HashMap();
    if (update) {
        // Validate params
        if (strategy != BOUNCE && strategy != DROP && strategy != STORE) {
            errors.put("general","Please choose one of the 3 strategies below.");
        }
        else {
            // Validate the storage policy of store strat is chosen:
            if (strategy == STORE) {
                if (storeStrategy != ALWAYS_STORE && storeStrategy != STORE_AND_BOUNCE
                        && storeStrategy != STORE_AND_DROP)
                {
                    errors.put("general","Please choose a valid storage policy.");
                }
                else {
                    // Validate the store size limit:
                    if (quota <= 0) {
                        errors.put("quota","Please enter a store size greater than 0 bytes.");
                    }
                }
            }
        }
        // If no errors, continue:
        if (errors.size() == 0) {

            if (strategy == STORE) {
                manager.setType(OfflineMessageStrategy.Type.store);

                if (storeStrategy == STORE_AND_BOUNCE) {
                    manager.setType(OfflineMessageStrategy.Type.store_and_bounce);
                }
                else if (storeStrategy == STORE_AND_DROP) {
                    manager.setType(OfflineMessageStrategy.Type.store_and_drop);
                }
                else /* (storeStrategy == ALWAYS_STORE) */ {
                    manager.setType(OfflineMessageStrategy.Type.store);
                }
            }
            else {
                if (strategy == BOUNCE) {
                    manager.setType(OfflineMessageStrategy.Type.bounce);
                }
                else if (strategy == DROP) {
                    manager.setType(OfflineMessageStrategy.Type.drop);
                }
            }

            manager.setQuota(quota);
%>
<c:set var="success" value="true" />
<%
        }
    }

    // Update variable values

    if (errors.size() == 0) {
        if (manager.getType() == OfflineMessageStrategy.Type.store
                || manager.getType() == OfflineMessageStrategy.Type.store_and_bounce
                || manager.getType() == OfflineMessageStrategy.Type.store_and_drop)
        {
            strategy = STORE;

            if (manager.getType() == OfflineMessageStrategy.Type.store_and_bounce) {
                storeStrategy = STORE_AND_BOUNCE;
            }
            else if (manager.getType() == OfflineMessageStrategy.Type.store_and_drop) {
                storeStrategy = STORE_AND_DROP;
            }
            else /*(manager.getType() == OfflineMessageStrategy.STORE)*/ {
                storeStrategy = ALWAYS_STORE;
            }
        }
        else {
            if (manager.getType() == OfflineMessageStrategy.Type.bounce) {
                strategy = BOUNCE;
            }
            else if (manager.getType() == OfflineMessageStrategy.Type.drop) {
                strategy = DROP;
            }
        }

        quota = manager.getQuota();
        if (quota < 0) {
            quota = 0;
        }
    }
%>


<c:if test="${success}" >
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>
</c:if>

<%  if (errors.containsKey("general") || errors.containsKey("quota")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <%  if (errors.containsKey("general")) { %>
            <%= errors.get("general") %>
        <%  } else if (errors.containsKey("quota")) { %>
            <%= errors.get("quota") %>
        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
XMPP provides the option for servers to store-and-forward IM messages when they sent to a user that
is not logged in. Supporting store-and-forward of 'offline messages' can be a very convenient
feature of an XMPP deployment. However, offline messages, like email, can take up a significant
amount of space on a server. <fmt:message key="title" bundle="${lang}" /> provides the option to handle offline messages in a
variety of ways. Select the offline message handling strategy that best suites your needs.
</p>

<form action="offline-messages.jsp">

<fieldset>
    <legend>Offline Message Policy</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="strategy" value="<%= BOUNCE %>" id="rb01"
                 <%= ((strategy==BOUNCE) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Always Bounce</b></label> - Never store the message, bounce the user
                back to the sender.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="strategy" value="<%= DROP %>" id="rb02"
                 <%= ((strategy==DROP) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Always Drop</b></label> - Never store the message, drop the message
                so the sender is not notified.
            </td>
        </tr>
        <tr valign="top" class="">
            <td width="1%" nowrap>
                <input type="radio" name="strategy" value="<%= STORE %>" id="rb03"
                 <%= ((strategy==STORE) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b>Store the Message</b></label> - Store the message for later. The
                message will be delivered when the recipient next logs-in. Choose a storage policy and
                storage store max size below.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">

                <table cellpadding="4" cellspacing="0" border="0" width="100%">
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="storeStrategy" value="<%= ALWAYS_STORE %>" id="rb05"
                         onclick="this.form.strategy[2].checked=true;"
                         <%= ((storeStrategy==ALWAYS_STORE) ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb05"><b>Always Store</b></label> - Always save the message.
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="storeStrategy" value="<%= STORE_AND_BOUNCE%>" id="rb06"
                         onclick="this.form.strategy[2].checked=true;"
                         <%= ((storeStrategy==STORE_AND_BOUNCE) ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb06"><b>Always Store then Bounce</b></label> - Always save the message
                        but bounce the message back to the sender.
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="storeStrategy" value="<%= STORE_AND_DROP %>" id="rb07"
                         onclick="this.form.strategy[2].checked=true;"
                         <%= ((storeStrategy==STORE_AND_DROP) ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb07"><b>Always Store then Drop</b></label> - Always save the message
                        but drop the message so the sender is not notified.
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        Offline message storage limit (in bytes):
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <input type="text" size="5" maxlength="12" name="quota"
                         value="<%= (quota>0 ? ""+quota : "") %>"
                         onclick="this.form.strategy[2].checked=true;">
                        bytes (1024 bytes = 1 K, 1048576 bytes = 1 Megabyte)
                    </td>
                </tr>
                </table>

            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
