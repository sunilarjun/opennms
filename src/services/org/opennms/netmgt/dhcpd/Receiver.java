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
// Tab Stop = 8
//
package org.opennms.netmgt.dhcpd;

import java.lang.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InterruptedIOException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;

import java.util.List;
import java.util.Iterator;

import org.opennms.core.fiber.Fiber;
import edu.bucknell.net.JDHCP.DHCPMessage;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;

final class Receiver
	implements Runnable, Fiber
{
	private final static short	DHCP_TARGET_PORT = 68;

	private DatagramSocket	m_receiver;

	private String		m_name;
	
	private int		m_status;

	private Thread		m_worker;

	private List		m_clients;


	Receiver(List clients)
		throws IOException
	{
		m_name = "DHCPReceiver";
		m_worker = null;
		m_status = START_PENDING;
		m_receiver = new DatagramSocket(DHCP_TARGET_PORT);
		m_receiver.setSoTimeout(1000);
		m_clients = clients;
	}

	public synchronized void start()
	{
		if(m_worker != null)
			throw new IllegalStateException("The fiber has already been started");

		m_worker = new Thread(this, getName());
		m_worker.setDaemon(true);
		m_worker.start();
		m_status = STARTING;
	}

	public synchronized void stop()
	{
		m_status = STOP_PENDING;
		m_receiver.close();
		m_worker.interrupt();
	}

	public synchronized int getStatus()
	{
		return m_status;
	}

	public String getName()
	{
		return m_name;
	}

	public void run()
	{
		Category log = ThreadCategory.getInstance(getClass());

		// set the state
		//
		synchronized(this)
		{
			m_status = RUNNING;
		}

		byte[] dgbuf = new byte[2048];

		// Roundy, Roundy, Round we go...
		//
		for(;;)
		{
			try
			{
				DatagramPacket pkt = new DatagramPacket(dgbuf, dgbuf.length);
				m_receiver.receive(pkt);
				log.debug("Receiver:  got a DHCP broadcast response.");
				Message msg = new Message(pkt.getAddress(), new DHCPMessage(pkt.getData()));

				log.debug("Receiver:  Forwarding DHCP message to all clients.");
				synchronized(m_clients)
				{
					Iterator iter = m_clients.iterator();
					while(iter.hasNext())
					{
						Client c = (Client)iter.next();
						if(c.getStatus() == RUNNING)
						{
							try
							{
								log.debug("Receiver:  sending DHCP response pkt to client " + c.getName());
								c.sendMessage(msg);
							}
							catch(IOException ex)
							{
								log.warn("Error sending response to client " + c.getName());
							}
						}
						else if(c.getStatus() == STOPPED)
						{
							log.debug("Receiver:  Removing stale client " + c.getName());
							iter.remove();
						}
					}
				}

			}
			catch(InterruptedIOException ex)
			{
				// ignore
			}
			catch(IOException ex)
			{
				synchronized(this)
				{
					if(m_status == RUNNING)
						log.warn("Failed to read message, I/O error", ex);
				}
				break;
			}
			catch(Throwable t)
			{
				synchronized(this)
				{
					if(m_status == RUNNING)
						log.warn("Undeclared throwable caught", t);
				}
				break;
			}

			synchronized(this)
			{
				if(m_status != RUNNING)
					break;
			}
		}

		synchronized(this)
		{
			m_status = STOP_PENDING;
		}
	
		// close the datagram socket
		//
		m_receiver.close();

		synchronized(this)
		{
			m_status = STOPPED;
		}

	} // end run() method

}
