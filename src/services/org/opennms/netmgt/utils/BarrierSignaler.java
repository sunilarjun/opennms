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
package org.opennms.netmgt.utils;

/**
 * <P> The Signaler interface was designed to get around
 * the problem of not being able to extend the functionality
 * of the Object.notify and Object.notifyAll methods. In some
 * instances is would be nice to alter the default behavior
 * slightly, the signaler interface allows this to occur.</P>
 *
 * <P>An object that implements the Signaler interface is used
 * just like a typical object. But instead of using notify and
 * notifyAll, the methods signal and signalAll should be used 
 * in their place.</P>
 *
 * @author <A HREF="mailto:weave@opennms.org">Weave</A>
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 *
 */
public final class BarrierSignaler
	implements Signaler
{
	/**
	 * The barrier where the signal starts
	 * occuring on each call.
	 */
	private int 	m_barrier;

	/**
	 * The count of signal calls. This latches
	 * at the barrier and does not increment
	 * further.
	 */
	private int	m_counter;

	/**
	 * Constructs a new barrier signaler.
	 *
	 * @param barrier The barrier for notification to start.
	 *
	 */
	public BarrierSignaler(int barrier)
	{
		m_barrier = barrier;
		m_counter = 0;
	}

	/**
	 * <P>Provides the functionality of the notify
	 * method, but may be overridden by the implementor
	 * to provide additional functionality.</P>
	 *
	 * @see java.lang.Object#notify
	 */
	public synchronized void signal()
	{
		if(++m_counter == m_barrier)
		{
			--m_counter;
			notify();
		}
	}
	
	/**
	 * <P>Provides the functionality of the notifyAll
	 * method, but may be overridden by the implementor
	 * to provide additional functionality.</P>
	 *
	 * @see java.lang.Object#notifyAll
	 */
	public void signalAll()
	{
		if(++m_counter == m_barrier)
		{
			--m_counter;
			notifyAll();
		}
	}
}

