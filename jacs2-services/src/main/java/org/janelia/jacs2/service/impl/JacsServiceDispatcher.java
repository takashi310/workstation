package org.janelia.jacs2.service.impl;

import com.google.common.collect.ImmutableList;
import org.janelia.jacs2.model.page.PageRequest;
import org.janelia.jacs2.model.page.PageResult;
import org.janelia.jacs2.model.page.SortCriteria;
import org.janelia.jacs2.model.page.SortDirection;
import org.janelia.jacs2.model.service.ServiceInfo;
import org.janelia.jacs2.model.service.ServiceState;
import org.janelia.jacs2.persistence.ServiceInfoPersistence;
import org.janelia.jacs2.service.ServerStats;
import org.janelia.jacs2.service.ServiceRegistry;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

@Startup
@Singleton
public class JacsServiceDispatcher {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_WAITING_SLOTS = 100;
    private static final int MAX_RUNNING_SLOTS = 200;

    @Named("SLF4J")
    @Inject
    private Logger logger;
    @Resource
    private ManagedExecutorService managedExecutorService;
    @Inject
    private Instance<ServiceInfoPersistence> serviceInfoPersistenceSource;
    @Inject
    private Instance<ServiceRegistry> serviceRegistrarSource;
    private final Queue<QueuedService> waitingServices;
    private final Semaphore availableSlots;
    private boolean noWaitingSpaceAvailable;
    private int runningServices;

    public JacsServiceDispatcher() {
        availableSlots = new Semaphore(MAX_RUNNING_SLOTS, true);
        waitingServices = new PriorityBlockingQueue<>(MAX_WAITING_SLOTS, new DefaultServiceInfoComparator());
        noWaitingSpaceAvailable = false;
    }

    public ServiceComputation submitService(ServiceInfo serviceArgs, Optional<ServiceInfo> currentService) {
        ServiceDescriptor serviceDescriptor = getServiceDescriptor(serviceArgs.getName());
        ServiceComputation serviceComputation = serviceDescriptor.createComputationInstance();
        serviceArgs.setServiceType(ServiceComputation.class.getName());
        if (currentService.isPresent()) {
            serviceArgs.updateParentService(currentService.get());
        }
        ServiceInfoPersistence serviceInfoPersistence = serviceInfoPersistenceSource.get();
        persistServiceInfo(serviceInfoPersistence, serviceArgs);
        enqueueService(serviceInfoPersistence, new QueuedService(serviceArgs, serviceComputation));
        return serviceComputation;
    }

    private ServiceDescriptor getServiceDescriptor(String serviceName) {
        ServiceRegistry registrar = serviceRegistrarSource.get();
        ServiceDescriptor serviceDescriptor = registrar.lookupService(serviceName);
        if (serviceDescriptor == null) {
            logger.error("No service found for {}", serviceName);
            throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
        return serviceDescriptor;
    }

    private void enqueueService(ServiceInfoPersistence serviceInfoPersistence, QueuedService service) {
        if (noWaitingSpaceAvailable) {
            // don't even check if anything has become available since last time
            // just drop it for now - the queue will be refilled after it drains.
            logger.info("In memory queue reached the capacity so service {} will not be put in memory", service.getServiceInfo());
            return;
        }
        boolean added = addWaitingService(serviceInfoPersistence, service);
        noWaitingSpaceAvailable  = !added || (waitingCapacity() <= 0);
        if (noWaitingSpaceAvailable) {
            logger.info("Not enough space in memory queue for {}", service.getServiceInfo());
        }
    }

    private boolean addWaitingService(ServiceInfoPersistence serviceInfoPersistence, QueuedService service) {
        boolean added = waitingServices.offer(service);
        if (added) {
            ServiceInfo si = service.getServiceInfo();
            if (si.getState() == ServiceState.CREATED) {
                si.setState(ServiceState.QUEUED);
                updateServiceInfo(serviceInfoPersistence, si);
            }
        }
        return added;
    }

    public ServerStats getServerStats() {
        ServerStats stats = new ServerStats();
        stats.setWaitingServices(waitingServices.size());
        stats.setAvailableSlots(availableSlots.availablePermits());
        stats.setRunningServices(runningServices);
        return stats;
    }

    void dispatchServices() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (!availableSlots.tryAcquire()) {
                logger.debug("No available processing slots");
                return; // no slot available
            }
            QueuedService service = dequeService(serviceInfoPersistenceSource.get());
            if (service == null) {
                // nothing to do
                availableSlots.release();
                return;
            }
            CompletableFuture
                    .supplyAsync(() -> {
                        runningServices++;
                        ServiceInfo si = service.getServiceInfo();
                        service.getServiceComputation().getServiceSupplier().put(si);
                        return service;
                    }, managedExecutorService)
                    .thenApply(sc -> {
                        ServiceInfo si = sc.getServiceInfo();
                        logger.debug("Submit {}" + si);
                        si.setState(ServiceState.SUBMITTED);
                        updateServiceInfo(serviceInfoPersistenceSource.get(), si);
                        return sc;
                    })
                    .thenComposeAsync(sc -> sc.getServiceComputation().processData(), managedExecutorService)
                    .whenCompleteAsync((si, exc) -> {
                        availableSlots.release();
                        runningServices--;
                        if (exc == null) {
                            logger.info("Successfully completed {}", si);
                            si.setState(ServiceState.SUCCESSFUL);
                        } else {
                            logger.error("Error executing {}", si, exc);
                            si.setState(ServiceState.ERROR);
                        }
                        updateServiceInfo(serviceInfoPersistenceSource.get(), si);
                    }, managedExecutorService);
        }
    }

    void syncServiceQueue() {
        if (noWaitingSpaceAvailable) {
            logger.info("Sync the waiting queue");
            // if at any point we reached the capacity of the in memory waiting queue
            // synchronize the in memory queue with the database and fill the queue with services that are still in CREATED state
            enqueueAvailableServices(serviceInfoPersistenceSource.get(), EnumSet.of(ServiceState.CREATED));
        }
    }

    private QueuedService dequeService(ServiceInfoPersistence serviceInfoPersistence) {
        QueuedService service = waitingServices.poll();
        if (service == null && enqueueAvailableServices(serviceInfoPersistence, EnumSet.of(ServiceState.CREATED, ServiceState.QUEUED))) {
            service = waitingServices.poll();
        }
        return service;
    }

    private void persistServiceInfo(ServiceInfoPersistence serviceInfoPersistence, ServiceInfo si) {
        si.setState(ServiceState.CREATED);
        serviceInfoPersistence.save(si);
        logger.info("Created service {}", si);
    }

    private void updateServiceInfo(ServiceInfoPersistence serviceInfoPersistence, ServiceInfo si) {
        serviceInfoPersistence.update(si);
        logger.info("Updated service {}", si);
    }

    private boolean enqueueAvailableServices(ServiceInfoPersistence serviceInfoPersistence, Set<ServiceState> serviceStates) {
        int availableSpaces = waitingCapacity();
        if (availableSpaces <= 0) {
            return false;
        }
        PageRequest servicePageRequest = new PageRequest();
        servicePageRequest.setPageSize(availableSpaces);
        servicePageRequest.setSortCriteria(new ArrayList<>(ImmutableList.of(
                new SortCriteria("priority", SortDirection.DESC),
                new SortCriteria("creationDate"))));
        PageResult<ServiceInfo> services = serviceInfoPersistence.findServicesByState(serviceStates, servicePageRequest);
        if (services.getResultList().size() > 0) {
            services.getResultList().stream().forEach(si -> {
                try {
                    ServiceDescriptor serviceDescriptor = getServiceDescriptor(si.getName());
                    ServiceComputation serviceComputation = serviceDescriptor.createComputationInstance();
                    addWaitingService(serviceInfoPersistence, new QueuedService(si, serviceComputation));
                } catch (Exception e) {
                    logger.error("Internal error - no computation can be created for {}", si);
                }
            });
            noWaitingSpaceAvailable = waitingCapacity() <= 0;
            return true;
        }
        return false;
    }

    private int waitingCapacity() {
        return MAX_WAITING_SLOTS - waitingServices.size();
    }
}