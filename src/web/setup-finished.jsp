<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.messenger.XMPPBootContainer,
                 java.util.Map,
                 java.util.Iterator"
%>

<%  boolean showSidebar = false;
        // First, update with XMPPSettings
        Map xmppSettings = (Map)session.getAttribute("xmppSettings");
        Iterator iter = xmppSettings.keySet().iterator();
        while(iter.hasNext()){
            String name = (String)iter.next();
            String value = (String)xmppSettings.get(name);
            JiveGlobals.setProperty(name, value);
        }

%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="title" bundle="${lang}" /> Setup Complete!
</p>

<p>
This installation of <fmt:message key="title" bundle="${lang}" /> is now complete.
To continue, please restart the server then
<a href="index.jsp">login to the admin console</a>.
</p>

<%@ include file="setup-footer.jsp" %>

