package org.janelia.jacs2.dataservice.persistence;

import org.janelia.jacs2.dao.JacsServiceDataDao;
import org.janelia.jacs2.model.jacsservice.JacsServiceData;
import org.janelia.jacs2.model.jacsservice.JacsServiceState;
import org.janelia.jacs2.model.page.PageRequest;
import org.janelia.jacs2.model.page.PageResult;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class JacsServiceDataPersistence extends AbstractDataPersistence<JacsServiceDataDao, JacsServiceData, Number> {

    @Inject
    JacsServiceDataPersistence(Instance<JacsServiceDataDao> serviceDataDaoSource) {
        super(serviceDataDaoSource);
    }

    public PageResult<JacsServiceData> findServicesByState(Set<JacsServiceState> requestStates, PageRequest pageRequest) {
        JacsServiceDataDao jacsServiceDataDao = daoSource.get();
        try {
            return jacsServiceDataDao.findServiceByState(requestStates, pageRequest);
        } finally {
            daoSource.destroy(jacsServiceDataDao);
        }
    }

    public List<JacsServiceData> findChildServices(Number serviceId) {
        JacsServiceDataDao jacsServiceDataDao = daoSource.get();
        try {
            return jacsServiceDataDao.findChildServices(serviceId);
        } finally {
            daoSource.destroy(jacsServiceDataDao);
        }
    }

    public List<JacsServiceData> findServiceHierarchy(Number serviceId) {
        JacsServiceDataDao jacsServiceDataDao = daoSource.get();
        try {
            return jacsServiceDataDao.findServiceHierarchy(serviceId);
        } finally {
            daoSource.destroy(jacsServiceDataDao);
        }
    }

    public void saveHierarchy(JacsServiceData jacsServiceData) {
        JacsServiceDataDao jacsServiceDataDao = daoSource.get();
        try {
            jacsServiceDataDao.saveServiceHierarchy(jacsServiceData);
        } finally {
            daoSource.destroy(jacsServiceDataDao);
        }
    }

    public void save(JacsServiceData jacsServiceData) {
        JacsServiceDataDao jacsServiceDataDao = daoSource.get();
        try {
            jacsServiceDataDao.save(jacsServiceData);
        } finally {
            daoSource.destroy(jacsServiceDataDao);
        }
    }

}