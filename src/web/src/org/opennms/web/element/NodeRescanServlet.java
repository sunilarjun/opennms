//
// Copyright (C) 2002 Sortova Consulting Group, Inc.  All rights reserved.
// Parts Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.sortova.com/
//
package org.opennms.web.element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opennms.core.resource.Vault;
import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.xml.event.*;
import org.opennms.netmgt.utils.EventProxy;
import org.opennms.netmgt.utils.TcpEventProxy;
import org.opennms.web.MissingParameterException;


/**
 *
 * @author <A HREF="larry@opennms.org">Larry Karnowski</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 */
public class NodeRescanServlet extends HttpServlet
{
    protected EventProxy proxy;

    public void init() throws ServletException {
        try {
            this.proxy = new TcpEventProxy();
        }
        catch( Exception e ) {
            throw new ServletException( "Exception", e );
        }
    }

    
    public void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
        //required parameters
        String nodeIdString = request.getParameter( "node" );
        String returnUrl = request.getParameter( "returnUrl" );
        
        if( nodeIdString == null ) {
            throw new MissingParameterException( "node", new String[] {"node", "returnUrl"} );
        }
        if( returnUrl == null ) {
            throw new MissingParameterException( "returnUrl", new String[] {"node", "returnUrl"} );
        }

        try {
            int nodeId = Integer.parseInt(nodeIdString);
            
            //prepare the event
            Event outEvent = new Event();
            outEvent.setSource("NodeRescanServlet");        
            outEvent.setUei(EventConstants.FORCE_RESCAN_EVENT_UEI);
            outEvent.setNodeid(nodeId);
            outEvent.setHost("host");
	    outEvent.setTime(EventConstants.formatToString(new java.util.Date()));
            
            //send the event
            this.proxy.send( outEvent );
            
            //redirect the request for display
            response.sendRedirect( request.getContextPath() + "/" + returnUrl );
        }
        catch( Exception e ) {
            throw new ServletException( "Exception sending node rescan event", e );
        }
    }
            
}

