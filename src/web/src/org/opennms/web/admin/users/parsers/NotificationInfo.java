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

package org.opennms.web.admin.users.parsers;

import java.util.*;

import org.opennms.web.admin.users.parsers.*;

/**This class stores notification information for a user
 * 
 * @author <A HREF="mailto:jason@opennms.org">Jason Johns</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 *
 * @version 1.1.1.1
 * 
 */
public class NotificationInfo implements Cloneable
{
	/**The email address of the user
	*/
	private String m_email;
	
	/**The email address for a pager (in case there is no analog modem to dial
	*  out with a page request
	*/
	private String m_pagerEmail;
	
	/**The service to use for numerical pages
	*/
	private String m_numericalService;
	
	/**The pin to use for numerical pages
	*/
	private String m_numericalPin;
	
	/**The service to use for text pages
	*/
	private String m_textService;
	
	/**The pin to use for text pages
	*/
	private String m_textPin;
	
	/**The list of duty schedules associated with this user
	*/
	private List m_dutySchedules;
	
	/**Default constructor, initializes the member variables
	*/
	public NotificationInfo()
	{
		m_email = "";
		m_pagerEmail = "";
		m_numericalService = "";
		m_numericalPin = "";
		m_textService = "";
		m_textPin = "";
		m_dutySchedules = new Vector();
	}
	
	/**
	*/
	public Object clone()
	{
		try
		{
			super.clone();
		}
		catch(CloneNotSupportedException e)
		{
			return null;
		}
		
		NotificationInfo newInfo = new NotificationInfo();
		
		newInfo.setEmail(m_email);
		newInfo.setPagerEmail(m_pagerEmail);
		newInfo.setNumericalService(m_numericalService);
		newInfo.setNumericalPin(m_numericalPin);
		newInfo.setTextService(m_textService);
		newInfo.setTextPin(m_textPin);
		
		for (int i = 0; i < m_dutySchedules.size(); i++)
		{
			DutySchedule curOldSched = (DutySchedule)m_dutySchedules.get(i);
			DutySchedule curNewSched = new DutySchedule(curOldSched.toString());
			newInfo.addDutySchedule(curNewSched);
		}
		
		return newInfo;
	}
	
	/**Sets the email address
	   @param String, the email address
	*/
	public void setEmail(String anEmail)
	{
		m_email = anEmail;
	}
	
	/**Sets the pager email address
	   @param String
	*/
	public void setPagerEmail(String anEmail)
	{
		m_pagerEmail = anEmail;
	}
	
	/**Sets the numerical service string
	   @param String, the numerical service
	*/
	public void setNumericalService(String aService)
	{
		m_numericalService = aService;
	}
	
	/**Sets the numerical pin
	   @param String, the numerical pin
	*/
	public void setNumericalPin(String aPin)
	{
		m_numericalPin = aPin;
	}
	
	/**Sets the text service string
	   @param String, the text service
	*/
	public void setTextService(String aService)
	{
		m_textService = aService;
	}
	
	/**Sets the text pin string
	   @param String, the text pin
	*/
	public void setTextPin(String aPin)
	{
		m_textPin = aPin;
	}
	
	/**This method adds a duty schedule
	   @param DutySchedule, a new duty schedule to associate with a user
	*/
	public void addDutySchedule(DutySchedule aSchedule)
	{
		m_dutySchedules.add(aSchedule);
	}
	
	/**This method sets a full list of duty schedules for a user
	   @List, a list of DutySchedule objects for a user
	*/
	public void setDutySchedule(List someSchedules)
	{
		m_dutySchedules = someSchedules;
	}
	
	/**Returns the email address
	   @return String, 
	*/
	public String getEmail()
	{
		return m_email;
	}
	
	/**Returns the pager email address
	   @return String, 
	*/
	public String getPagerEmail()
	{
		return m_pagerEmail;
	}
	
	/**Returns the numerical service string
	   @return String, the numerical service
	*/
	public String getNumericalService()
	{
		return m_numericalService;
	}
	
	/**Returns the numerical pin string
	   @return String, the numerical pin
	*/
	public String getNumericalPin()
	{
		return m_numericalPin;
	}
	
	/**Returns the text service string
	   @return String, the text service
	*/
	public String getTextService()
	{
		return m_textService;
	}
	
	/**Returns the text pin string
	   @return String, the text pin
	*/
	public String getTextPin()
	{
		return m_textPin;
	}
	
	/**Returns the number of DutySchedule object for a user
	   @return int, the number of DutySchedules
	*/
	public int getDutyScheduleCount()
	{
		return m_dutySchedules.size();
	}
	
	/**Returns the full list of DutySchedules
	   @return List, the full list of DutySchedules
	*/
	public List getDutySchedules()
	{
		return m_dutySchedules;
	}
	
	/**Returns a boolean indicating if the user is on duty at the 
	   specified time.
	   @param Calendar aTime, a time to see if the user is on duty
	   @return boolean, true if the user is on duty, false otherwise
	*/
	public boolean isOnDuty(Calendar aTime)
	{
		boolean result = false;
		
		//if there is no schedule assume that the user is on duty
		if (m_dutySchedules.size() == 0)
		{
			return true;
		}
		
		DutySchedule curSchedule = null;
		
		for (int i = 0; i < m_dutySchedules.size(); i++)
		{
			curSchedule = (DutySchedule)m_dutySchedules.get(i);
			
			result = curSchedule.isInSchedule(aTime);
			
			//don't continue if the time is in this schedule
			if (result)
				break;
		}
		
		return result;
	}
	
	/**A String representation of the notification information, primarily used for debugging.
	   @return String, representation of the notification information
	*/
	public String toString()
	{
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("email             = " + m_email + "\n");
		buffer.append("numerical service = " + m_numericalService + "\n");
		buffer.append("numerical pin     = " + m_numericalPin + "\n");
		buffer.append("text service      = " + m_textService + "\n");
		buffer.append("text pin          = " + m_textPin + "\n");
		
		for (int i = 0; i < m_dutySchedules.size(); i++)
		{
			buffer.append(m_dutySchedules.get(i).toString() + "\n");
		}
		
		return buffer.toString();
	}
}
