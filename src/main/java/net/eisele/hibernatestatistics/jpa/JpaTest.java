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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import net.eisele.hibernatestatistics.domain.Employee;
import net.eisele.hibernatestatistics.domain.Department;
import java.util.UUID;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.hibernate.Session;
import org.hibernate.ejb.EntityManagerImpl;
import org.hibernate.jmx.StatisticsService;

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

    /**
     * Public constructor
     *
     * @param manager
     */
    public JpaTest(EntityManager manager) {
        this.manager = manager;
    }

    /**
     * Main example
     *
     * @param args
     */
    public static void main(String[] args) {
        EntityManagerFactory factory = Persistence.createEntityManagerFactory("hibernatestatistics");
        EntityManager manager = factory.createEntityManager();
        JpaTest test = new JpaTest(manager);

        registerHibernateMBeans(manager);
        initJolokiaServer();

        EntityTransaction tx = manager.getTransaction();
        tx.begin();
        try {
            test.createEmployees();
        } catch (Exception e) {
            logger.error("Error creating data", e);
        }
        tx.commit();

        test.listEmployees();

        logger.info(".. done");
    }

    private void createEmployees() {

        Department department = new Department("java");
        Department department2 = new Department("redhat");

        manager.persist(department);
        manager.persist(department2);

        for (int i = 0; i < 10000; i++) {
            String surename = UUID.randomUUID().toString();
            manager.persist(new Employee("Jakab " + surename, department));
            manager.persist(new Employee("Captain " + surename, department2));
        }

    }

    private void listEmployees() {
        List<Employee> resultList = manager.createQuery("Select a From Employee a", Employee.class).getResultList();
        System.out.println("num of employess:" + resultList.size());
        for (Employee next : resultList) {
            logger.info("next employee: " + next);
        }
    }

    private static void registerHibernateMBeans(EntityManager manager) {

        try {
            MBeanServer mbeanServer
                    = ManagementFactory.getPlatformMBeanServer();
            ObjectName on
                    = new ObjectName("Hibernate:type=statistics,application=hibernatestatistics");
            StatisticsService mBean = new StatisticsService();
            mBean.setSessionFactory(getHibernateSession(manager).getSessionFactory());
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

    private static Session getHibernateSession(EntityManager entityManager) {
        Session session;
        if (entityManager.getDelegate() instanceof EntityManagerImpl) {
            EntityManagerImpl entityManagerImpl = (EntityManagerImpl) entityManager.getDelegate();
            session = entityManagerImpl.getSession();
        } else {
            session = (Session) entityManager.getDelegate();
        }
        return session;
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
