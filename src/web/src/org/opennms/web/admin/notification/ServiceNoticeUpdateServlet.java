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

package org.opennms.web.admin.notification;

import java.util.*;
import java.io.*;
import java.sql.*;
import java.sql.Connection;
import javax.servlet.*;
import javax.servlet.http.*;

import org.opennms.core.resource.Vault;

/**
 * A servlet that handles updating the ifservices table with the notice status
 *
 * @author <A HREF="mailto:jason@opennms.org">Jason Johns</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 */
public class ServiceNoticeUpdateServlet extends HttpServlet
{
	private static final String UPDATE_SERVICE = "UPDATE ifservices SET notify = ? WHERE nodeID = ? AND ipaddr = ? AND serviceid = ?";
		
	public void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException 
	{
		HttpSession userSession = request.getSession(false);
		Map servicesCheckedMap = (Map)userSession.getAttribute("service.notify.map");
		
		String checkedServices[] = request.getParameterValues("serviceCheck");
		
		if (checkedServices != null)
		{
			for (int i = 0; i < checkedServices.length; i++)
			{
				System.out.println(checkedServices[i]);
				servicesCheckedMap.put(checkedServices[i], "Y");
			}
		}
		
		Iterator iterator = servicesCheckedMap.keySet().iterator();
		while(iterator.hasNext())
		{
			String key = (String)iterator.next();
			
			//decompose the key into nodeid, ipaddres and service id
			StringTokenizer tokenizer = new StringTokenizer(key, ",");
			int nodeID = Integer.parseInt(tokenizer.nextToken());
			String ipAddress = tokenizer.nextToken();
			int serviceID = Integer.parseInt(tokenizer.nextToken());
			
			updateService(nodeID, ipAddress, serviceID, (String)servicesCheckedMap.get(key));
		}
		
		response.sendRedirect( "index.jsp" );
	}
	
	/**
	*/
	private void updateService(int nodeID, String interfaceIP, int serviceID, String notifyFlag)
		throws ServletException
	{
		Connection connection = null;
		
		try
		{
			connection = Vault.getDbConnection();
			
			PreparedStatement stmt = connection.prepareStatement(UPDATE_SERVICE);
			stmt.setString(1, notifyFlag);
			stmt.setInt(2, nodeID);
			stmt.setString(3, interfaceIP);
			stmt.setInt(4, serviceID);
			
			stmt.executeUpdate();
			
			//close off the db connection
			Vault.releaseDbConnection(connection);
		}
		catch (SQLException e)
		{
			try
			{
				connection.rollback();
				Vault.releaseDbConnection(connection);
			}
			catch (SQLException sqlEx)
			{
				throw new ServletException("Couldn't roll back update to service " + serviceID + " on interface " + interfaceIP + " notify as " + notifyFlag + " in the database.", sqlEx);
			}
			
			throw new ServletException("Error when updating to service " + serviceID + " on interface " + interfaceIP + " notify as " + notifyFlag + " in the database.", e);
		}
	}
}
