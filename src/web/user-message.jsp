<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.util.Date,
                 java.text.DateFormat,
                 java.util.HashMap,
                 org.jivesoftware.messenger.user.*,
                 java.util.Map" %>


<c:set var="sbar" value="session" scope="page" />  

<%  // Get parameters
    String username = ParamUtils.getParameter(request,"username");
    boolean send = ParamUtils.getBooleanParameter(request,"send");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean sendToAll = ParamUtils.getBooleanParameter(request,"sendToAll");
    boolean tabs = ParamUtils.getBooleanParameter(request,"tabs",true);
    String jid = ParamUtils.getParameter(request,"jid");
    String[] jids = ParamUtils.getParameters(request,"jid");
    String sessionID = ParamUtils.getParameter(request,"sessionID");
    String message = ParamUtils.getParameter(request,"message");
%>



<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(pageContext); %>



<%

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        if (username == null) {
            response.sendRedirect("session-summary.jsp");
            return;
        }
        else {
            response.sendRedirect("user-properties.jsp?username=" + username);
            return;
        }
    }

    // Get the user - a user might not be passed in if this is a system-wide message
    User user = null;
    if (username != null) {
        user = webManager.getUserManager().getUser(username);
    }

    // Get the session manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Handle the request to send a message:
    Map errors = new HashMap();
    if (send) {
        // Validate the message and jid
        if (jid == null && !sendToAll && user != null) {
            errors.put("jid","jid");
        }
        if (message == null) {
            errors.put("message","message");
        }
        if (errors.size() == 0) {
            // no errors, so continue
            if (user == null) {
                // system-wide message:
                sessionManager.sendServerMessage(null,message);
            }
            else {
                if (sendToAll) {
                    // loop through all sessions based on the user assoc with the JID, send
                    // message to all
                    for (int i=0; i<jids.length; i++) {
                        XMPPAddress address = XMPPAddress.parseJID(jids[i]);
                        Session s = sessionManager.getSession(address);
                        sessionManager.sendServerMessage(address, null, message);
                    }
                }
                else {
                    sessionManager.sendServerMessage(XMPPAddress.parseJID(jid),null,message);
                }
            }
            if(username != null){
              response.sendRedirect("user-message.jsp?success=true&username=" + username + "&tabs=" + tabs);
            }
                return;
        }
    }

    // Get all sessions associated with this user:
    int numSessions = -1;
    Session sess = null;
    Collection<Session> sessions = null;
    if (user != null) {
        numSessions = sessionManager.getSessionCount(user.getUsername());
        sessions = sessionManager.getSessions(user.getUsername());
        if (numSessions == 1) {
            sess = (Session)sessions.iterator().next();
        }
    }
%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Send Administrative Message";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-message.jsp"));
    pageinfo.setPageID("user-message");
%>
<%@ include file="top.jsp" %>
<jsp:include page="title.jsp" flush="true" />



<%  if (tabs && username != null) { %>
    <br>
    <c:set var="tab" value="message" />
    <%@ include file="user-tabs.jsp" %>

<%  } %>

<%  if (success) { %>

    <p class="jive-success-text">
    Message was sent successfully!
    </p>

<%  } %>

<p>

</p>

<script language="JavaScript" type="text/javascript">
function updateSelect(el) {
    if (el.checked) {
        for (var e=0; e<el.form.jid.length; e++) {
            el.form.jid[e].selected = true;
        }
    }
    else {
        for (var e=0; e<el.form.jid.length; e++) {
            el.form.jid[e].selected = false;
        }
    }
    el.form.message.focus();
}
</script>

<form action="user-message.jsp" method="post" name="f">
<% if(username != null){ %>
<input type="hidden" name="username" value="<%= username %>">
<% } %>
<input type="hidden" name="tabs" value="<%= tabs %>">
<input type="hidden" name="send" value="true">
<%  if (sess != null) { %>

    <input type="hidden" name="sessionID" value="<%= sess.getAddress().toString() %>">

<%  } %>

<table cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeader"><td colspan="3" align="left">Send Administrative Message</td></tr>
<tr><td colspan=3 class="text">
<%   if (user == null) { %>

    Use the form below to send an administrative message to all users.

<%  } else { %>

    User the form below to send an administrative message to the specified user. If the user is
    connected from multiple sessions you will need to choose which session to message.

<%  } %>
</td></tr>
<tr>
    <td class="jive-label">
        To:
    </td>
    <td>
        <%  if (user == null) { %>

            All Online Users

        <%  } else { %>

            <%  if (sess != null && numSessions == 1) { %>

                <%= sess.getAddress().toString() %>
                <input type="hidden" name="jid" value="<%= sess.getAddress().toString() %>">

            <%  } else { %>

                <select size="2" name="jid" multiple>

                <%   Iterator iter = sessions.iterator();
                     while (iter.hasNext()) {
                        sess = (Session)iter.next();
                %>
                    <option value="<%= sess.getAddress().toString() %>"><%= sess.getAddress().toString() %></option>

                <%  } %>

                </select>

                <input type="checkbox" name="sendToAll" value="true" id="cb01"
                 onfocus="updateSelect(this);" onclick="updateSelect(this);">
                <label for="cb01">Send to all user sessions</label>

            <%  } %>

            <%  if (errors.get("jid") != null) { %>

                <br>
                <span class="jive-error-text">
                Please choose a valid address.
                </span>

            <%  } %>

        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label">
        Message:
    </td>
    <td>
        <%  if (errors.get("message") != null) { %>

            <span class="jive-error-text">
            Please enter a valid message.
            </span>
            <br>

        <%  } %>
        <textarea name="message" cols="55" rows="5" wrap="virtual"></textarea>
    </td>
</tr>
</table>
</div>

<br>

<input type="submit" value="Send Message">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.message.focus();
</script>

<%@ include file="footer.jsp" %>