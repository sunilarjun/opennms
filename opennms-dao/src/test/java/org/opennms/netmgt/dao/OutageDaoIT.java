/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opennms.core.utils.InetAddressUtils.addr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.spring.BeanUtils;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.netmgt.dao.api.ApplicationDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.EventDao;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.dao.api.ServiceTypeDao;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.OnmsCriteria;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsEvent;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsOutage;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.model.ServiceSelector;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.model.outage.CurrentOutageDetails;
import org.opennms.netmgt.model.outage.OutageSummary;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.collect.Sets;

/**
 * @author mhuot
 *
 */
@SuppressWarnings("deprecation")
@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-mockConfigManager.xml",
        "classpath:/META-INF/opennms/applicationContext-databasePopulator.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
public class OutageDaoIT implements InitializingBean {
    @Autowired
    private DistPollerDao m_distPollerDao;

    @Autowired
    private MonitoringLocationDao m_locationDao;

    @Autowired
    private NodeDao m_nodeDao;

    @Autowired
    private IpInterfaceDao m_ipInterfaceDao;

    @Autowired
    private MonitoredServiceDao m_monitoredServiceDao;

    @Autowired
    private MonitoringLocationDao m_monitoringLocationDao;

    @Autowired
    private ApplicationDao m_applicationDao;

    @Autowired
    private OutageDao m_outageDao;

    @Autowired
    private ServiceTypeDao m_serviceTypeDao;

    @Autowired
    private EventDao m_eventDao;

    @Autowired
    TransactionTemplate m_transTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    @Before
    public void setUp() throws Exception {
        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                for (final String service : Arrays.asList("ICMP", "Minion-Heartbeat", "Minion-RPC")) {
                    if (m_serviceTypeDao.findByName(service) == null) {
                        m_serviceTypeDao.save(new OnmsServiceType(service));
                    }
                }
            }
        });
    }

    @Test
    @Transactional
    public void testSave() {
        OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "localhost");
        m_nodeDao.save(node);

        OnmsIpInterface ipInterface = new OnmsIpInterface(addr("172.16.1.1"), node);

        OnmsServiceType serviceType = m_serviceTypeDao.findByName("ICMP");
        assertNotNull(serviceType);

        OnmsMonitoredService monitoredService = new OnmsMonitoredService(ipInterface, serviceType);

        OnmsEvent event = new OnmsEvent();

        OnmsOutage outage = new OnmsOutage(new Date(), monitoredService);
        outage.setServiceLostEvent(event);
        m_outageDao.save(outage);

        //it works we're so smart! hehe
        outage = m_outageDao.load(outage.getId());
        assertEquals("ICMP", outage.getMonitoredService().getServiceType().getName());
//        outage.setEventBySvcRegainedEvent();
        
    }

    @Test
    @JUnitTemporaryDatabase
    public void testGetMatchingOutages() {
        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "localhost");
                m_nodeDao.save(node);
                insertEntitiesAndOutage("172.16.1.1", "ICMP", node);
            }
        });

        /*
         * We need to flush and finish the transaction because JdbcFilterDao
         * gets its own connection from the DataSource and won't see our data
         * otherwise.
         */

        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                String[] svcs = new String[] { "ICMP" };
                ServiceSelector selector = new ServiceSelector("ipAddr IPLIKE 172.16.1.1", Arrays.asList(svcs));
                Collection<OnmsOutage> outages = m_outageDao.matchingCurrentOutages(selector);
                assertEquals("outage count", 1, outages.size());
            }
        });
    }

    @Test
    @JUnitTemporaryDatabase
    public void testGetMatchingOutagesWithEmptyServiceList() {
        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "localhost");
                m_nodeDao.save(node);
                insertEntitiesAndOutage("172.16.1.1", "ICMP", node);
            }
        });

        /*
         * We need to flush and finish the transaction because JdbcFilterDao
         * gets its own connection from the DataSource and won't see our data
         * otherwise.
         */

        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                ServiceSelector selector = new ServiceSelector("ipAddr IPLIKE 172.16.1.1", new ArrayList<String>(0));
                Collection<OnmsOutage> outages = m_outageDao.matchingCurrentOutages(selector);
                assertEquals(1, outages.size());
            }
        });
    }

    @Test
    @Transactional
    public void testDuplicateOutages() {
        for (final OnmsNode node : m_nodeDao.findAll()) {
            m_nodeDao.delete(node);
        }
        OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "shoes");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.1", "ICMP", node);
        insertEntitiesAndOutage("192.0.2.1", "ICMP", node);
        
        node = new OnmsNode(m_locationDao.getDefaultLocation(), "megaphone");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.2", "ICMP", node);
        insertEntitiesAndOutage("172.17.1.2", "ICMP", node);
        insertEntitiesAndOutage("172.18.1.2", "ICMP", node);

        node = new OnmsNode(m_locationDao.getDefaultLocation(), "grunties");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.3", "ICMP", node);

        List<OutageSummary> outages = m_outageDao.getNodeOutageSummaries(0);
        System.err.println(outages);
        assertEquals(3, outages.size());
    }

    @Test
    @Transactional
    public void testGetStatusChangesForApplicationIdBetween() {
        final OnmsMonitoringLocation rdu = new OnmsMonitoringLocation();
        rdu.setLocationName("RDU");
        rdu.setMonitoringArea("USA");
        rdu.setPriority(1L);
        m_monitoringLocationDao.save(rdu);

        final OnmsMonitoringLocation fulda = new OnmsMonitoringLocation();
        fulda.setLocationName("Fulda");
        fulda.setMonitoringArea("Germany");
        fulda.setPriority(1L);
        m_monitoringLocationDao.save(fulda);

        final OnmsMonitoringLocation ottawa = new OnmsMonitoringLocation();
        ottawa.setLocationName("Ottawa");
        ottawa.setMonitoringArea("Canada");
        ottawa.setPriority(1L);
        m_monitoringLocationDao.save(ottawa);

        final OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "node");
        m_nodeDao.save(node);

        final OnmsMonitoredService app1Service1 = getMonitoredService(getIpInterface("172.16.1.1", node), getServiceType("ICMP"));
        final OnmsMonitoredService app1Service2 = getMonitoredService(getIpInterface("172.16.1.2", node), getServiceType("ICMP"));
        final OnmsMonitoredService service3 = getMonitoredService(getIpInterface("172.18.1.2", node), getServiceType("ICMP"));

        final OnmsApplication app1 = new OnmsApplication();
        app1.setName("APP1");
        app1.getPerspectiveLocations().add(rdu);
        app1.getPerspectiveLocations().add(fulda);
        int app1Id = this.m_applicationDao.save(app1);

        app1Service1.setApplications(Sets.newHashSet(app1));
        app1Service2.setApplications(Sets.newHashSet(app1));
        this.m_monitoredServiceDao.saveOrUpdate(app1Service1);
        this.m_monitoredServiceDao.saveOrUpdate(app1Service2);

        assertEquals(0, this.m_outageDao.findAll().size());
        assertEquals(0, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #1 |---|               |---|
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service1, 9000L, 9500L);
        addOutage(rdu, app1Service1, 20500L, 21000L);
        assertEquals(2, this.m_outageDao.findAll().size());
        assertEquals(0, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #2    |---|
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service1, 5000L, 15000L);
        assertEquals(3, this.m_outageDao.findAll().size());
        assertEquals(1, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #3           |---|
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service2, 12500L, 17500L);
        assertEquals(4, this.m_outageDao.findAll().size());
        assertEquals(2, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #4                  |---|
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service1, 15000L, 25000L);
        assertEquals(5, this.m_outageDao.findAll().size());
        assertEquals(3, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #5                  |---> ongoing
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service2, 15000L, null);
        assertEquals(6, this.m_outageDao.findAll().size());
        assertEquals(4, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #6    |-----------------|
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service1, 5000L, 25000L);
        assertEquals(7, this.m_outageDao.findAll().size());
        assertEquals(5, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // Case #7    |-----------------> ongoing
        // Timeframe    | <- - - - -> |
        addOutage(rdu, app1Service1, 5000L, null);
        assertEquals(8, this.m_outageDao.findAll().size());
        assertEquals(6, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());

        // now, insert stuff in the time interval that is not related to the application's services and locations
        addOutage(rdu, service3, 11000L, 19000L);
        addOutage(fulda, service3, 12000L, 18000L);
        addOutage(ottawa, app1Service1, 13000L, 17000L);
        addOutage(ottawa, app1Service2, 14000L, 16000L);

        assertEquals(12, this.m_outageDao.findAll().size());
        assertEquals(6, this.m_outageDao.getStatusChangesForApplicationIdBetween(new Date(10000), new Date(20000), app1Id).size());
    }

    private void addOutage(final OnmsMonitoringLocation location, final OnmsMonitoredService monitoredService, final Long start, final Long end) {
        final OnmsOutage onmsOutage = new OnmsOutage();

        if (start != null) {
            onmsOutage.setIfLostService(new Date(start));
        }

        if (end!= null) {
            onmsOutage.setIfRegainedService(new Date(end));
        }

        onmsOutage.setMonitoredService(monitoredService);
        onmsOutage.setPerspective(location);

        m_outageDao.save(onmsOutage);
        m_outageDao.flush();
    }

    @Test
    @Transactional
    public void testLimitDuplicateOutages() {
        for (final OnmsNode node : m_nodeDao.findAll()) {
            m_nodeDao.delete(node);
        }
        OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "shoes");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.1", "ICMP", node);
        insertEntitiesAndOutage("192.0.2.1", "ICMP", node);
        
        node = new OnmsNode(m_locationDao.getDefaultLocation(), "megaphone");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.2", "ICMP", node);
        insertEntitiesAndOutage("172.17.1.2", "ICMP", node);
        insertEntitiesAndOutage("172.18.1.2", "ICMP", node);

        node = new OnmsNode(m_locationDao.getDefaultLocation(), "grunties");
        m_nodeDao.save(node);
        insertEntitiesAndOutage("172.16.1.3", "ICMP", node);

        List<OutageSummary> outages = m_outageDao.getNodeOutageSummaries(2);
        System.err.println(outages);
        assertEquals(2, outages.size());

        outages = m_outageDao.getNodeOutageSummaries(3);
        System.err.println(outages);
        assertEquals(3, outages.size());

        outages = m_outageDao.getNodeOutageSummaries(4);
        System.err.println(outages);
        assertEquals(3, outages.size());

        outages = m_outageDao.getNodeOutageSummaries(5);
        System.err.println(outages);
        assertEquals(3, outages.size());
    }

    @Test
    @JUnitTemporaryDatabase
    public void testNewestOutages() {
        for (final OnmsNode node : m_nodeDao.findAll()) {
            m_nodeDao.delete(node);
        }
        m_nodeDao.flush();

        OnmsNode node = new OnmsNode(m_locationDao.getDefaultLocation(), "minion");
        m_nodeDao.save(node);

        final OnmsServiceType minionHeartbeat = getServiceType("Minion-Heartbeat");
        final OnmsServiceType minionRpc = getServiceType("Minion-RPC");
        final OnmsServiceType icmp = getServiceType("ICMP");

        final OnmsIpInterface iface = getIpInterface("172.16.1.1", node);
        final OnmsIpInterface v6 = getIpInterface("::1", node);

        final OnmsMonitoredService nodeHeartbeatService = getMonitoredService(iface, minionHeartbeat);
        final OnmsMonitoredService nodeRpcService = getMonitoredService(iface, minionRpc);
        final OnmsMonitoredService icmpService = getMonitoredService(iface, icmp);

        final OnmsMonitoredService v6IcmpService = getMonitoredService(v6, icmp);

        final Date date1 = new Date(1);
        final Date date2 = new Date(2);
        final Date date3 = new Date(3);
        final Date date4 = new Date(4);
        final Date date5 = new Date(5);
        final Date date6 = new Date(6);

        OnmsOutage h1 = new OnmsOutage(date1, date1, nodeHeartbeatService);
        OnmsOutage h2 = new OnmsOutage(date3, date3, nodeHeartbeatService);
        OnmsOutage h3 = new OnmsOutage(date5, (Date)null, nodeHeartbeatService);

        OnmsOutage r1 = new OnmsOutage(date2, date2, nodeRpcService);
        OnmsOutage r2 = new OnmsOutage(date4, date4, nodeRpcService);
        OnmsOutage r3 = new OnmsOutage(date6, (Date)null, nodeRpcService);

        OnmsOutage i1 = new OnmsOutage(date1, date1, icmpService);

        OnmsOutage v1 = new OnmsOutage(date1, date1, v6IcmpService);

        m_outageDao.save(h1);
        m_outageDao.save(h2);
        m_outageDao.save(h3);

        m_outageDao.save(r1);
        m_outageDao.save(r2);
        m_outageDao.save(r3);

        m_outageDao.save(i1);

        m_outageDao.save(v1);

        m_outageDao.flush();

        m_transTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) {
                final List<String> services = Arrays.asList("Minion-Heartbeat", "Minion-RPC");
                Collection<CurrentOutageDetails> outages = m_outageDao.newestCurrentOutages(services);
                assertEquals("we should have 2 outages", 2, outages.size());

                Iterator<CurrentOutageDetails> it = outages.iterator();
                CurrentOutageDetails outage = it.next();
                assertEquals("the first outage should be a heartbeat outage", "Minion-Heartbeat", outage.getServiceName());
                assertEquals("the first outage should be the *latest* heartbeat outage", date5, outage.getIfLostService());

                outage = it.next();
                assertEquals("the second outage should be an RPC outage", "Minion-RPC", outage.getServiceName());
                assertEquals("the second outage should be the *latest* RPC outage", date6, outage.getIfLostService());

                // also make sure it works when no services are specified
                outages = m_outageDao.newestCurrentOutages(Collections.emptyList());
                assertEquals("we should have 2 outages", 2, outages.size());

                it = outages.iterator();
                outage = it.next();
                assertEquals("the first outage should be a heartbeat outage", "Minion-Heartbeat", outage.getServiceName());
                assertEquals("the first outage should be the *latest* heartbeat outage", date5, outage.getIfLostService());

                outage = it.next();
                assertEquals("the second outage should be an RPC outage", "Minion-RPC", outage.getServiceName());
                assertEquals("the second outage should be the *latest* RPC outage", date6, outage.getIfLostService());
            }
        });
    }

    private OnmsDistPoller getLocalHostDistPoller() {
        return m_distPollerDao.whoami();
    }
    
    private OnmsOutage insertEntitiesAndOutage(final String ipAddr, final String serviceName, OnmsNode node) {
        OnmsIpInterface ipInterface = getIpInterface(ipAddr, node);
        OnmsServiceType serviceType = getServiceType(serviceName);
        OnmsMonitoredService monitoredService = getMonitoredService(ipInterface, serviceType);
        
        OnmsEvent event = getEvent();

        OnmsOutage outage = getOutage(monitoredService, event);
        
        return outage;
    }

    private OnmsOutage getOutage(OnmsMonitoredService monitoredService, OnmsEvent event) {
        OnmsOutage outage = new OnmsOutage(new Date(), monitoredService);
        outage.setServiceLostEvent(event);
        m_outageDao.save(outage);
        m_outageDao.flush();
        return outage;
    }

    private OnmsEvent getEvent() {
        OnmsEvent event = new OnmsEvent();
        event.setDistPoller(getLocalHostDistPoller());
        event.setEventUei("foo!");
        event.setEventTime(new Date());
        event.setEventCreateTime(new Date());
        event.setEventSeverity(OnmsSeverity.INDETERMINATE.getId());
        event.setEventSource("your mom");
        event.setEventLog("Y");
        event.setEventDisplay("Y");
        m_eventDao.save(event);
        m_eventDao.flush();
        return event;
    }

    private OnmsMonitoredService getMonitoredService(OnmsIpInterface ipInterface, OnmsServiceType serviceType) {
        final OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class)
            .add(Restrictions.eq("ipInterface", ipInterface))
            .add(Restrictions.eq("serviceType", serviceType));
        final List<OnmsMonitoredService> services = m_monitoredServiceDao.findMatching(criteria);
        OnmsMonitoredService monitoredService;
        if (services.size() > 0) {
            monitoredService = services.get(0);
        } else {
            monitoredService = new OnmsMonitoredService(ipInterface, serviceType);
        }
        m_monitoredServiceDao.save(monitoredService);
        m_monitoredServiceDao.flush();
        return monitoredService;
    }

    private OnmsServiceType getServiceType(final String serviceName) {
        OnmsServiceType serviceType = m_serviceTypeDao.findByName(serviceName);
        assertNotNull("Couldn't find " + serviceName + " in the database", serviceType);
        return serviceType;
    }

    private OnmsIpInterface getIpInterface(String ipAddr, OnmsNode node) {
        OnmsIpInterface ipInterface = m_ipInterfaceDao.findByNodeIdAndIpAddress(node.getId(), ipAddr);
        if (ipInterface == null) {
            ipInterface = new OnmsIpInterface(addr(ipAddr), node);
            ipInterface.setIsManaged("M");
            m_ipInterfaceDao.save(ipInterface);
        }
        m_ipInterfaceDao.flush();
        return ipInterface;
    }
}
