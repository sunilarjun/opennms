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

package org.opennms.web.notification.bobject;

import java.util.*;

import org.opennms.core.utils.*;
import org.opennms.web.admin.users.parsers.*;


/**A NotificationTarget representing a user target
 * parsed from the notifications.xml.
 * 
 * @author <A HREF="mailto:jason@opennms.org">Jason Johns</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 * 
 * @version 1.1.1.1
*/
public class GroupTarget extends NotificationTarget
{
	/**The userId of the target
	*/
	private String m_groupName;
	
	/**The User object associated with this target
	*/
	private List m_userTargets;
	
	/**The command name to use to contact this user
	*/
	private String m_commandName;
	
	/**Default Constructor
	*/
	public GroupTarget()
	{
		m_userTargets = new ArrayList();
	}
	
	/**Sets the userId for this target
	   @param String
	*/
	public void setGroupName(String name)
	{
		m_groupName = name;
	}
	
	/**Returns the userId for this target
	   @return String
	*/
	public String getGroupName()
	{
		return m_groupName;
	}
	
	/**Sets the command name for this target
	   @param String
	*/
	public void setCommandName(String commandName)
	{
		m_commandName = commandName;
	}
	
	/**Returns the command name for this target
	   @return String
	*/
	public String getCommandName()
	{
		return m_commandName;
	}
	
	/**Adds a user target to this group target
	   @param target
	*/
	public void addUserTarget(UserTarget target)
	{
		m_userTargets.add(target);
	}
	
	/**Returns the list of UserTargets in this group target
	   @param List, a list of UserTarget objects
	*/
	public List getUserTargets()
	{
		return m_userTargets;
	}
	
	/**Returns the type of the target
	   @return int, NotificationTask.TARGET_TYPE_USER
	*/
	public int getType()
	{
		return TARGET_TYPE_GROUP;
	}
}
