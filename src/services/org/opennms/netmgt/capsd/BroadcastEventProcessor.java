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
//
// Tab Size = 8
//
package org.opennms.netmgt.capsd;

import java.lang.*;

import java.io.StringReader;

import java.util.Properties;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.log4j.Category;

import org.opennms.core.queue.FifoQueue;
import org.opennms.core.queue.FifoQueueException;
import org.opennms.core.utils.ThreadCategory;

import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.config.DatabaseConnectionFactory;
import org.opennms.netmgt.eventd.EventIpcManagerFactory;
import org.opennms.netmgt.eventd.EventListener;

//  generated by castor
import org.opennms.netmgt.xml.event.Event;

/**
 *
 * @author <a href="mailto:weave@opennms.org">Brian Weaver</a>
 * @author <a href="http://www.opennms.org/">OpenNMS</a>
 */
final class BroadcastEventProcessor
	implements EventListener
{
	/**
	 * SQL query to retrieve nodeid of a particulary interface address
	 */
	private static String 	SQL_RETRIEVE_NODEID = "select nodeid from ipinterface where ipaddr=? and isManaged!='D'";
	
	/**
	 * The location where suspectInterface events are enqueued
	 * for processing.
	 */
	private FifoQueue	m_suspectQ;

	/**
	 * The Capsd rescan scheduler
	 */
	private Scheduler 	m_scheduler;

	/**
	 * Create message selector to set to the subscription
	 */
	private void createMessageSelectorAndSubscribe()
	{
		// Create the selector for the ueis this service is interested in
		//
		List ueiList = new ArrayList();

		// newSuspectInterface
		ueiList.add(EventConstants.NEW_SUSPECT_INTERFACE_EVENT_UEI);

		// forceRescan
		ueiList.add(EventConstants.FORCE_RESCAN_EVENT_UEI);

		// nodeAdded
		ueiList.add(EventConstants.NODE_ADDED_EVENT_UEI);

		// nodeDeleted
		ueiList.add(EventConstants.NODE_DELETED_EVENT_UEI);

		// duplicateNodeDeleted
		ueiList.add(EventConstants.DUP_NODE_DELETED_EVENT_UEI);

		EventIpcManagerFactory.init();
		EventIpcManagerFactory.getInstance().getManager().addEventListener(this, ueiList);
	}

	/**
	 * Constructor 
	 *
	 * @param suspectQ	The queue where new SuspectEventProcessor objects 
	 *                      are enqueued for running..
	 * @param scheduler	Rescan scheduler.
	 */
	BroadcastEventProcessor(FifoQueue suspectQ, Scheduler scheduler)
	{
		Category log = ThreadCategory.getInstance(getClass());

		// Suspect queue
		//
		m_suspectQ = suspectQ;

		// Scheduler
		//
		m_scheduler = scheduler;
		
		// Subscribe to eventd
		//
		createMessageSelectorAndSubscribe();
	}

	/**
	 * Unsubscribe from eventd
	 */
	public void close()
	{
		EventIpcManagerFactory.getInstance().getManager().removeEventListener(this);
	}


	/**
	 * This method is invoked by the EventIpcManager
	 * when a new event is available for processing. Currently
	 * only text based messages are processed by this callback.
	 * Each message is examined for its Universal Event Identifier
	 * and the appropriate action is taking based on each UEI.
	 *
	 * @param event	The event.
	 */
	public void onEvent(Event event)
	{
		Category log = ThreadCategory.getInstance(getClass());

		String eventUei = event.getUei();
		if (eventUei == null)
			return;
		
		if (log.isDebugEnabled())
			log.debug("Received event: " + eventUei);

		if(eventUei.equals(EventConstants.NEW_SUSPECT_INTERFACE_EVENT_UEI))
		{
			// new poll event
			try
			{
				if (log.isDebugEnabled())
					log.debug("onMessage: Adding interface to suspectInterface Q: " + event.getInterface());
				m_suspectQ.add(new SuspectEventProcessor(event.getInterface()));
			}
			catch(Exception ex)
			{
				log.error("onMessage: Failed to add interface to suspect queue", ex);
			}
		}
		else if(eventUei.equals(EventConstants.FORCE_RESCAN_EVENT_UEI))
		{
			// If the event has a node identifier use it otherwise
			// will need to use the interface to lookup the node id
			// from the database
			int nodeid = -1;
			
			if (event.hasNodeid())
				nodeid = (int)event.getNodeid();
			else
			{
				// Extract interface from the event and use it to
				// lookup the node identifier associated with the 
				// interface from the database.
				//
			
				// Get database connection and retrieve nodeid
				Connection dbc = null;
				PreparedStatement stmt = null;
				ResultSet rs = null;
				try
				{
					dbc = DatabaseConnectionFactory.getInstance().getConnection();
				
					// Retrieve node id
					stmt = dbc.prepareStatement(SQL_RETRIEVE_NODEID);
					stmt.setString(1, event.getInterface());
					rs = stmt.executeQuery();
					if (rs.next())
					{
						nodeid = rs.getInt(1);
					}
				}
				catch (SQLException sqlE)
				{
					log.error("onMessage: Database error during nodeid retrieval for interface " + event.getInterface(), sqlE);
				}
				finally	
				{
					// Close the prepared statement
					if (stmt != null)
					{
						try
						{
							stmt.close();
						}
						catch (SQLException sqlE)
						{
							// Ignore
						}
					}
				
					// Close the connection
					if (dbc != null)
					{
						try
						{
							dbc.close();
						}
						catch (SQLException sqlE)
						{
							// Ignore
						}
					}
				}
			
				if (nodeid == -1)
				{
					log.error("onMessage: Nodeid retrieval for interface " + event.getInterface() + " failed.  Unable to perform rescan.");
					return;
				}
			}
			
			// Rescan the node.  
			m_scheduler.forceRescan(nodeid);
		}
		else if(eventUei.equals(EventConstants.NODE_ADDED_EVENT_UEI))
		{
			// Schedule the new node.
			try
			{
				m_scheduler.scheduleNode((int)event.getNodeid());
			}
			catch(SQLException sqlE)
			{
				log.error("onMessage: SQL exception while attempting to schedule node " + event.getNodeid(), sqlE);
			}
		}
		else if(eventUei.equals(EventConstants.NODE_DELETED_EVENT_UEI))
		{
			// Remove the deleted node from the scheduler
			m_scheduler.unscheduleNode((int)event.getNodeid());
		}
		else if(eventUei.equals(EventConstants.DUP_NODE_DELETED_EVENT_UEI))
		{
			// Remove the deleted node from the scheduler
			m_scheduler.unscheduleNode((int)event.getNodeid());
		}
	} // end onEvent()

	/**
	 * Return an id for this event listener
	 */
	public String getName()
	{
		return "Capsd:BroadcastEventProcessor";
	}

} // end class
