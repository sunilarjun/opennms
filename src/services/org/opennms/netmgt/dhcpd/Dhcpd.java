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
// Tab Stop = 8
//
//
package org.opennms.netmgt.dhcpd;

import java.lang.*;
import java.lang.reflect.UndeclaredThrowableException;

import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;

import java.util.LinkedList;
import java.util.List;
import java.util.Observer;
import java.util.Observable;
import java.util.Collections;

import org.opennms.core.fiber.PausableFiber;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.config.DhcpdConfigFactory;

import org.apache.log4j.Category;

import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;

/**
 * <P>The DHCP client daemon serves as a multiplexor for DHCP requests and responses.
 * The Bootp/DHCP protocol specifies that a DHCP server listens for requests
 * on local UDP/67 and will either send/broadcast responses to UDP/68 or will
 * unicast responses back to the client's UDP port on from which the request 
 * originated. </p>
 * 
 * <p>The DHCP daemon accepts client connections on TCP/5818.  Once a client is 
 * connected it can begin forwarding requests.  A list of all currently
 * connected clients is maintained. Requests have the following
 * format:</p>
 * <ul>
 * <li>	byte 1 - byte 4 : 32-bit remote host IP address </li>
 * <li> byte 5 - byte 8 : 32-bit buffer length </li>
 * <li> byte 9 - byte n : buffer containing the formatted DHCP discover request.</li>
 * </ul>
 * 
 * <p>The client indicates that it is finished by sending a request with the 
 * remote host IP address set to zero (0).</p>
 * 
 * <p>Incoming requests are sent to UDP/67 on specified remote host.
 * If the remote host is runnning a DHCP server it will send/braodcast an appropriate 
 * response to UDP/68 (or will unicast the response).</p>
 * 
 * <p>The DHCP daemon includes a listener thread which binds to UDP/68 and simply
 * listens for any incoming DHCP responses.  When a datagram is received by the
 * listener thread it loops through the list of currently connected clients and
 * forwards the DHCP response packet to each client.  It is the responsibility
 * of the client to validate that the datagram is in response to a DHCP discover
 * request packet that it generated.</p>
 *
 * @author <A HREF="mailto:mike@opennms.org">Mike</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 *
 */
public final class Dhcpd
	implements PausableFiber, Runnable, Observer
{
	/**
	 * Log4J prefix
	 */
	private static final String LOG4J_CATEGORY = "OpenNMS.Dhcpd";
	
	/**
	 * The singular instance of the DHCP server.
	 */
	private final static Dhcpd 	m_singleton = new Dhcpd();
	
	/**
	 * List of clients currently connected to the DHCP daemon
	 */
	private static List 	m_clients;
	
	/**
	 * Socket over which the daemon actively listens for new
	 * client connection requests.
	 */
	private ServerSocket 	m_server;
	
	/**
	 * DHCP response listener.
	 */
	private Receiver	m_listener;
	
	/**
	 * The current status of the fiber.
	 */
	private int		m_status;

	/**
	 * The working thread
	 */
	private Thread		m_worker;

	/**
	 * Constructs a new DHCP server instance. All of the internal
	 * fields are initialized to <code>null</code>.
	 *
	 */
	private Dhcpd()
	{
		m_clients = null;
		m_server  = null;
		m_listener= null;
		m_status  = START_PENDING;
		m_worker  = null;
	}

	/**
	 * Starts the server instance. If the server is running
	 * then an exception is thrown. Also, since the opening
	 * of sockets and other resources are delayed until this
	 * method is invoked, standard exceptions are rethrown as an
	 * {@link java.lang.reflect.UndeclaredThrowableException
	 * undeclared throwable}.
	 *
	 * @throws java.lang.IllegalStateException Thrown if the server is
	 * 	already running.
	 *
	 * @throws java.lang.reflect.UndeclaredThrowableException Thrown if a 
	 * 	non-runtime exception is genereated during startup.
	 *
	 */
	public synchronized void start()
	{
		ThreadCategory.setPrefix(LOG4J_CATEGORY);
		Category log = ThreadCategory.getInstance(getClass());
		if (log.isDebugEnabled())
			log.debug("start: DHCP client daemon starting...");

		m_status = STARTING;
		
		// Only allow start to be called once.
		//
		if(m_worker != null && m_worker.isAlive())
			throw new IllegalStateException("The server is already running");

		// Unless the worker has died, then stop
		// and continue.
		//
		if(m_worker != null)
			stop();

		// the client list
		//
		m_clients = Collections.synchronizedList(new LinkedList());
		
		// load the dhcpd configuration
		DhcpdConfigFactory dFactory = null;
		try
		{
			DhcpdConfigFactory.reload();
			dFactory = DhcpdConfigFactory.getInstance();
		}
		catch(MarshalException ex)
		{
			log.error("Failed to load dhcpd configuration", ex);
			throw new UndeclaredThrowableException(ex);
		}
		catch(ValidationException ex)
		{
			log.error("Failed to load dhcpd configuration", ex);
			throw new UndeclaredThrowableException(ex);
		}
		catch(IOException ex)
		{
			log.error("Failed to load dhcpd configuration", ex);
			throw new UndeclaredThrowableException(ex);
		}

		// open the server
		//
		try
		{
			if (log.isDebugEnabled())
				log.debug("start: listening on TCP port " + dFactory.getPort() + " for incoming client requests.");
			m_server  = new ServerSocket(dFactory.getPort());
		}
		catch(IOException ex)
		{
			throw new UndeclaredThrowableException(ex);
		}

		// open the receiver socket
		//
		try
		{
			if (log.isDebugEnabled())
				log.debug("start: starting unicast receiver thread..");
			m_listener = new Receiver(m_clients);
			m_listener.start();
		}
		catch(IOException ex)
		{
			try { m_server.close(); } catch(IOException ex1) { }
			throw new UndeclaredThrowableException(ex);
		}

		m_worker = new Thread(this, getName());
		m_worker.start();
	}

	/**
	 * Stops the currently running instance of the DHCP
	 * server. If the server is not running, then this
	 * command is silently discarded.
	 *
	 */
	public synchronized void stop()
	{
		if(m_worker == null)
			return;
		
		// set the pending status
		//
		m_status = STOP_PENDING;

		// stop the receiver
		//
		m_listener.stop();

		// close the server socket
		//
		try
		{
			m_server.close();
		}
		catch(IOException ex) { }

		// close all the clients
		//
		Object[] list = null;
		synchronized(m_clients)
		{
			list = m_clients.toArray();
		}

		for(int x = 0 ; list != null && x < list.length; x++)
		{
			((Client)list[x]).stop();
		}

		m_server  = null;
		m_clients = null;
		m_worker  = null;
		m_listener= null;
		m_status  = STOPPED;
	}

	/**
	 * Returns the current status of the fiber.
	 */
	public synchronized int getStatus()
	{
		return m_status;
	}

	/**
	 * Returns the current name of the fiber.
	 */
	public String getName()
	{
		return "OpenNMS.Dhcpd";
	}

	/**
	 * Pauses Dhcpd
	 */
	public void pause()
	{
		// do nothing
		m_status = PAUSED;
	}

	/**
	 * Resumes Trapd
	 */
	public void resume()
	{
		// do nothing
		m_status = RUNNING;
	}
	
	/**
	 * The main routine of the DHCP server. This method accepts incomming
	 * client requests and starts new client handlers to process each
	 * request.
	 *
	 */
	public void run()
	{
		Category log = ThreadCategory.getInstance(getClass());

		// update the status
		//
		synchronized(this)
		{
			if(m_status != STARTING)
				return;

			if (log.isDebugEnabled())
				log.debug("run: setting status to running...");
			m_status = RUNNING;
		}

		if (log.isDebugEnabled())
			log.debug("run: DHCPD client daemon running...");
		
		// Begin accepting connections from clients
		// For each new client create new DHCP Client Handler
		// thread to handle the client's requests.
		//
		try 
		{
			m_server.setSoTimeout(1000); // Wake up every second to check the status
			
			for(;;)
			{
				synchronized(this)
				{
					if(m_status == PAUSED)
					{
						try
						{
							wait();
						}
						catch(InterruptedException e)
						{
							// ignore
						}
					}
					else if(m_status != RUNNING)
						break;
				}

				Socket sock;
				try
				{	
					sock = m_server.accept();
				}
				catch (InterruptedIOException iE)
				{
					continue;
				}
	
				// Add the client's new socket connection to the client list
				//
				if (log.isDebugEnabled())
					log.debug("run: got connection request...creating client handler...");
					
				try
				{
					Client clnt = new Client(sock);
					m_clients.add(clnt);
					clnt.addObserver(this);
					clnt.start();
				}
				catch (IOException ioE) 
				{
					synchronized(this)
					{
						if(m_status == RUNNING)
							log.error("I/O exception occured creating client handler.", ioE);
					}
				}
			}
		}
		catch (IOException ioE) 
		{
			synchronized(this)
			{
				if(m_status == RUNNING)
					log.error("I/O exception occured processing incomming request", ioE);
			}
		}
		catch(Throwable t)
		{
			synchronized(this)
			{
				if(m_status == RUNNING)
					log.error("An undeclared throwable was caught", t);
			}
		}
		finally
		{
			synchronized(this)
			{
				m_status = STOPPED;
			}
		}
		
	}

	/**
	 * This method is called by the observable instances
	 * that the server has registered to receive. 
	 *
	 * @param inst	The observable object that has changed.
	 * @param ignored Thsi parameter is ignored by this method.
	 *
	 */
	public void update(Observable inst, Object ignored)
	{
		synchronized(this)
		{
			if(m_clients != null)
				m_clients.remove(inst);
		}
	}

	/**
	 * Returns the singular instance of the DHCP server.
	 *
	 */
	public static Dhcpd getInstance()
	{
		return m_singleton;
	}

	/**
	 * Contacts the public server and checks to see if the 
	 * the passed address is a DHCP server.
	 *
	 * @param address	The address to query.
	 * @param timeout	The time to wait between retries.
	 * @param retries	The maximum number of attempts.
	 *
	 * @return response time in milliseconds if remote box is
	 * 	a DHCP server or -1 if it is NOT.
	 *
	 * @throws java.io.IOException Thrown if an error occurs.
	 */
	public static long isServer(InetAddress address, long timeout, int retries)
		throws IOException
	{
		return Poller.isServer(address, timeout, retries);
	}

	/**
	 * Contacts the public server and checks to see if the 
	 * the passed address is a DHCP server.
	 *
	 * @param address	The address to query.
	 *
	 * @return response time in milliseconds if remote box is
	 * 	a DHCP server or -1 if it is NOT.
	 *
	 * @throws java.io.IOException Thrown if an error occurs.
	 */
	public static long isServer(InetAddress address)
		throws IOException
	{
		return Poller.isServer(address, Poller.DEFAULT_TIMEOUT, Poller.DEFAULT_RETRIES);
	}
}
