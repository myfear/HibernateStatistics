/*
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.eisele.hibernatestatistics.jpa;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import net.eisele.hibernatestatistics.domain.Department;
import net.eisele.hibernatestatistics.domain.Employee;

import org.hibernate.SessionFactory;
import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="https://community.jboss.org/people/myfear">Markus Eisele</a>
 */
public class JpaTest {

    private final EntityManager manager;

    private final static Logger logger = LoggerFactory.getLogger(JpaTest.class);

    private final Random random;

    /**
     * Public constructor
     *
     * @param manager
     */
    public JpaTest(EntityManager manager) {
        this.manager = manager;
        random = new Random();
    }

    /**
     * Main example
     *
     * @param args
     */
    public static void main(String[] args) {
        // Create the EntityManager to use.
        EntityManagerFactory factory = Persistence.createEntityManagerFactory("hibernatestatistics");
        registerHibernateMBeans(factory);

        EntityManager manager = factory.createEntityManager();
        JpaTest test = new JpaTest(manager);

        // register the Statistics Mbean

        // initialize the Jolokia Server to expose JMX via JSON for Hawtio
        initJolokiaServer();

        // Do something with the entities
        EntityTransaction tx = manager.getTransaction();
        tx.begin();
        try {
            test.createEmployees();
        } catch (Exception e) {
            logger.error("Error creating data", e);
        }
        tx.commit();

        test.listEmployees();

        manager.close();

        logger.info(".. done");
    }

    private void createEmployees() {

        Department department = new Department("java");
        Department department2 = new Department("redhat");

        manager.persist(department);
        manager.persist(department2);

        for (int i = 0; i < 1000; i++) {
            // assign a UUID name
            String surename = UUID.randomUUID().toString();
            // assign random department
            Department dep = random.nextBoolean() ? department : department2;
            manager.persist(new Employee("Elena " + surename, dep));
            manager.persist(new Employee("Paisley " + surename, dep));
        }

    }

    private void listEmployees() {
        List<Employee> resultList = manager.createQuery("Select a From Employee a", Employee.class).getResultList();
        logger.info("num of employees:" + resultList.size());

        Department department = (Department) manager.createQuery("SELECT d FROM Department d WHERE d.name=:name")
                .setParameter("name", "java")
                .getSingleResult();

        logger.info( "Department: " + department.getName() );

        List<Employee> resultList2 = manager.createQuery("SELECT e FROM Employee e WHERE e.department.name=:department", Employee.class)
                .setParameter("department", "java")
                .getResultList();

        logger.info("num of java employees:" + resultList2.size());

    }

    private static void registerHibernateMBeans(EntityManagerFactory emf) {
        SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);

        try {
            MBeanServer mbeanServer
                    = ManagementFactory.getPlatformMBeanServer();
            ObjectName on
                    = new ObjectName("Hibernate:type=statistics,application=hibernatestatistics");

            StatisticsService mBean = new DelegatingStatisticsService(sessionFactory.getStatistics());
            mBean.setStatisticsEnabled(true); // alternative is to enable it in persistence.xml
            mbeanServer.registerMBean(mBean, on);

        } catch (MalformedObjectNameException ex) {
            logger.error("", ex);
        } catch (InstanceAlreadyExistsException ex) {
            logger.error("", ex);
        } catch (MBeanRegistrationException ex) {
            logger.error("", ex);
        } catch (NotCompliantMBeanException ex) {
            logger.error("", ex);
        }

    }

    private static void initJolokiaServer() {
        try {
            //8778 default server port
            JolokiaServerConfig config = new JolokiaServerConfig(new HashMap<String, String>());
            JolokiaServer jolokiaServer = new JolokiaServer(config, true);
            jolokiaServer.start();
        } catch (IOException e) {
            logger.error("unable to start jolokia server", e);
        }
    }

}
