<%@ page import="org.jivesoftware.messenger.Session,
                 org.jivesoftware.messenger.Presence"%>
 <%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%--
  - This page is meant to be included in other pages. It assumes 2 variables:
  -     * 'sess', a org.jivesoftware.messenger.Session object
  -     * 'count', an int representing the row number we're on.
  -     * 'current', a boolean which indicates the current row the user is looking (pass in
  -       false if there is no current row.
  -     * 'linkURL', a String representing the JSP page to link to
--%>

<%  if (current) { %>

    <tr class="jive-current">

<%  } else { %>

    <tr class="jive-<%= (((count % 2) == 0) ? "even" : "odd") %>">

<%  } %>

    <td width="1%" nowrap><%= count %></td>
    <td width="10%" nowrap>
        <%  String name = sess.getAddress().getName(); %>
        <a href="session-details.jsp?jid=<%= sess.getAddress() %>" title="Click for more info..."
         ><%= ((name != null && !"".equals(name)) ? name : "<i>Anonymous</i>") %></a>

        <%  if (sess.getConnection().isSecure()) { %>

            <img src="images/lock.gif" width="9" height="12" border="0"
             title="User is connected via SSL" hspace="2">

        <%  } %>

    </td>
    <td width="15%" nowrap>
        <%= sess.getAddress().getResource() %>
    </td>
    <td width="25%">
        <%  int _status = sess.getStatus();
            if (_status == Session.STATUS_CLOSED) {
        %>
            Closed

        <%  } else if (_status == Session.STATUS_CONNECTED) { %>

            Connected

        <%  } else if (_status == Session.STATUS_STREAMING) { %>

            Streaming

        <%  } else if (_status == Session.STATUS_AUTHENTICATED) { %>

            Authenticated

        <%  } else { %>

            Unknown

        <%  } %>
    </td>

    <%  int _show = sess.getPresence().getShow();
        String _stat = sess.getPresence().getStatus();
        if (_show == Presence.SHOW_AWAY) {
    %>
        <td width="1%"
            ><img src="images/status-away.gif" width="14" height="14" border="0" title="Away"
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= _stat %>

            <%  } else { %>

                Away

            <%  } %>
        </td>

    <%  } else if (_show == Presence.SHOW_CHAT) { %>

        <td width="1%"
            ><img src="images/status-chat.gif" width="14" height="14" border="0" title="Available to Chat"
            ></td>
        <td width="46%">
            Available to Chat
        </td>

    <%  } else if (_show == Presence.SHOW_DND) { %>

        <td width="1%"
            ><img src="images/status-dnd.gif" width="14" height="14" border="0" title="Do Not Disturb"
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= sess.getPresence().getStatus() %>

            <%  } else { %>

                Do Not Disturb

            <%  } %>
        </td>

    <%  } else if (_show == Presence.SHOW_INVISIBLE) { %>

        <td colspan="2" width="47%">
            <%  if (_stat != null) { %>

                <%= sess.getPresence().getStatus() %>

            <%  } else { %>

                Invisible

            <%  } %>
        </td>

    <%  } else if (_show == Presence.SHOW_NONE) { %>

        <td width="1%"
            ><img src="images/status-online.gif" width="14" height="14" border="0" title="Online"
            ></td>
        <td width="46%">
            Online
        </td>

    <%  } else if (_show == Presence.SHOW_XA) { %>

        <td width="1%"
            ><img src="images/status-xaway.gif" width="14" height="14" border="0" title="Extended Away"
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= sess.getPresence().getStatus() %>

            <%  } else { %>

                Extended Away

            <%  } %>
        </td>

    <%  } else { %>

        <td colspan="2" width="46%">
            Unknown/Not Recognized
        </td>

    <%  } %>

    <td width="1%" nowrap>
        <%= sess.getConnection().getInetAddress().getHostAddress() %>
    </td>

    <td width="1%" nowrap align="center">
        <a href="session-summary.jsp?jid=<%= sess.getAddress() %>&close=true"
         title="Click to kill session..."
         onclick="return confirm('Are you sure you want to close this connection?');"
         ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
    </td>
</tr>