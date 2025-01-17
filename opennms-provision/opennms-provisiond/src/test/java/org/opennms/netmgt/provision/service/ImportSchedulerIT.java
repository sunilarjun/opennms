/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.provision.service;

import com.google.common.collect.Lists;
import org.junit.*;
import org.junit.runner.RunWith;
import org.opennms.core.spring.BeanUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.opennms.netmgt.config.provisiond.ProvisiondConfiguration;
import org.opennms.netmgt.config.provisiond.RequisitionDef;
import org.opennms.netmgt.dao.api.ProvisiondConfigurationDao;
import org.opennms.netmgt.dao.mock.EventAnticipator;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-provisiond.xml",
        "classpath:/META-INF/opennms/applicationContext-snmp-profile-mapper.xml",
        "classpath:/META-INF/opennms/applicationContext-tracer-registry.xml",
        "classpath*:/META-INF/opennms/provisiond-extensions.xml",
        "classpath:/META-INF/opennms/applicationContext-rpc-dns.xml",
        "classpath*:/META-INF/opennms/detectors.xml",
        "classpath:/mockForeignSourceContext.xml",
        "classpath:/importerServiceTest.xml"
})
@JUnitConfigurationEnvironment(systemProperties="org.opennms.provisiond.enableDiscovery=false")
public class ImportSchedulerIT implements InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(ImportSchedulerIT.class);
    
    @Autowired
    ImportJobFactory m_factory;
    
    @Autowired
    Provisioner m_provisioner;
    
    @Autowired
    ImportScheduler m_importScheduler;
    
    @Autowired
    ProvisiondConfigurationDao m_dao;

    @Autowired
    MockEventIpcManager m_mockEventIpcManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    @Before
    public void setUp() throws IOException, JAXBException {
        MockLogAppender.setupLogging();
    }

    @After
    public void tearDown() {
        try {
            final ListenerManager listenerManager = m_importScheduler.getScheduler().getListenerManager();
            final List<String> triggerListeners = listenerManager.getTriggerListeners().stream().map(tl -> tl.getName()).collect(Collectors.toList());
            triggerListeners.forEach(tlName -> listenerManager.removeTriggerListener(tlName));
        } catch (final Exception e) {
            LOG.warn("Failed to clean up existing trigger listeners.", e);
        }
        try {
            m_importScheduler.getScheduler().clear();
        } catch (SchedulerException e) {
            LOG.warn("Failed to clear existing scheduler.", e);
        }
    }

    @Test
    @JUnitTemporaryDatabase
    public void createJobAndVerifyImportJobFactoryIsRegistered() throws SchedulerException, InterruptedException, IOException {
        
        RequisitionDef def = m_dao.getDefs().get(0);
        
        JobDetail detail = JobBuilder.newJob(ImportJob.class).withIdentity("test", ImportScheduler.JOB_GROUP).storeDurably(false).requestRecovery(false).build();
        detail.getJobDataMap().put(ImportJob.URL, def.getImportUrlResource().orElse(null));
        detail.getJobDataMap().put(ImportJob.RESCAN_EXISTING, def.getRescanExisting());

        
        class MyBoolWrapper {
            volatile Boolean m_called = false;
            
            public Boolean getCalled() {
                return m_called;
            }
            
            public void setCalled(Boolean called) {
                m_called = called;
            }
        }
        
        final MyBoolWrapper callTracker = new MyBoolWrapper();
        
        m_importScheduler.getScheduler().getListenerManager().addTriggerListener(new TriggerListener() {
            
            
            @Override
            public String getName() {
                return "TestTriggerListener";
            }

            @Override
            public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {
                LOG.info("triggerComplete called on trigger listener");
                callTracker.setCalled(true);
            }

            @Override
            public void triggerFired(Trigger trigger, JobExecutionContext context) {
                LOG.info("triggerFired called on trigger listener");
                Job jobInstance = context.getJobInstance();
                
                if (jobInstance instanceof ImportJob) {
                    Assert.assertNotNull( ((ImportJob)jobInstance).getProvisioner());
                    Assert.assertTrue(context.getJobDetail().getJobDataMap().containsKey(ImportJob.URL));
                    Assert.assertEquals("dns://localhost/localhost", context.getJobDetail().getJobDataMap().get(ImportJob.URL));
                    Assert.assertTrue(context.getJobDetail().getJobDataMap().containsKey(ImportJob.RESCAN_EXISTING));
                    Assert.assertEquals("dbonly", context.getJobDetail().getJobDataMap().get(ImportJob.RESCAN_EXISTING));
                }
                callTracker.setCalled(true);
            }

            @Override
            public void triggerMisfired(Trigger trigger) {
                LOG.info("triggerMisFired called on trigger listener");
                callTracker.setCalled(true);
            }

            @Override
            public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
                LOG.info("vetoJobExecution called on trigger listener");
                callTracker.setCalled(true);
                return false;
            }
            
        });
        
        Calendar testCal = Calendar.getInstance();
        testCal.add(Calendar.SECOND, 5);

        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("test", ImportScheduler.JOB_GROUP).startAt(testCal.getTime()).build();
        m_importScheduler.getScheduler().scheduleJob(detail, trigger);
        m_importScheduler.start();
        
        int callCheck = 0;
        while (!callTracker.getCalled() && callCheck++ < 2 ) {
            Thread.sleep(5000);
        }
        
        //TODO: need to fix the interrupted exception that occurs in the provisioner
        
    }

    @Test
    @JUnitTemporaryDatabase
    public void buildImportSchedule() throws SchedulerException, InterruptedException, IOException {
        // Add a simple definition to the configuration that attempts
        // to import a non existent file every 5 seconds
        RequisitionDef def = new RequisitionDef();
        // Every 5 seconds
        def.setCronSchedule("*/5 * * * * ? *");
        def.setImportName("test");
        def.setImportUrlResource("file:///tmp/should-not-exist.xml");
        def.setRescanExisting(Boolean.FALSE.toString());

        m_dao.getConfig().setRequisitionDefs(Lists.newArrayList(def));

        // The import should start, and then fail
        EventAnticipator anticipator = m_mockEventIpcManager.getEventAnticipator();
        EventBuilder builder = new EventBuilder(EventConstants.IMPORT_STARTED_UEI, "Provisiond");
        anticipator.anticipateEvent(builder.getEvent());

        builder = new EventBuilder(EventConstants.IMPORT_FAILED_UEI, "Provisiond");
        anticipator.anticipateEvent(builder.getEvent());

        // Go
        m_importScheduler.buildImportSchedule();
        m_importScheduler.start();

        // Verify
        anticipator.waitForAnticipated(10*1000);
        anticipator.verifyAnticipated();
    }

    @Test
    @Ignore
    public void dwRemoveCurrentJobsFromSchedule() {
        fail("Not yet implemented");
    }

}
