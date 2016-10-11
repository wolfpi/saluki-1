package com.quancheng.boot.starter.saluki.runner;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.quancheng.boot.starter.saluki.GRpcService;
import com.quancheng.boot.starter.saluki.autoconfigure.GRpcProperties;
import com.quancheng.saluki.core.config.ServiceConfig;

@Order(value = 0)
public class GRpcServerRunner implements CommandLineRunner, DisposableBean {

    private static final Logger        log = LoggerFactory.getLogger(GRpcServerRunner.class);

    @Autowired
    private GRpcProperties             grpcProperties;

    @Autowired
    private AbstractApplicationContext applicationContext;

    private ServiceConfig              serviceConfig;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting gRPC Server ...");
        ServiceConfig serviceConfig = newServiceConfig();
        for (Object obj : getTypedBeansWithAnnotation(GRpcService.class)) {
            GRpcService gRpcServiceAnn = obj.getClass().getAnnotation(GRpcService.class);
            String interfaceName = gRpcServiceAnn.interfaceName();
            if (StringUtils.isBlank(interfaceName)) {
                interfaceName = obj.getClass().getName();
            }
            serviceConfig.addServiceConfig(interfaceName, this.getGroup(gRpcServiceAnn),
                                           this.getVersion(gRpcServiceAnn), obj);
        }
        serviceConfig.export();
    }

    @Override
    public void destroy() throws Exception {
        serviceConfig.destroy();
        applicationContext.destroy();
    }

    private String getGroup(GRpcService service) {
        if (StringUtils.isNoneBlank(service.group())) {
            return service.group();
        } else {
            String group = grpcProperties.getServiceGroup();
            Preconditions.checkState(StringUtils.isBlank(group), "Group can not be null", group);
            return group;
        }
    }

    private String getVersion(GRpcService service) {
        if (StringUtils.isNoneBlank(service.version())) {
            return service.version();
        } else {
            String version = grpcProperties.getServcieVersion();
            Preconditions.checkState(StringUtils.isBlank(version), "Version can not be null", version);
            return version;
        }
    }

    private ServiceConfig newServiceConfig() {
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setRegistryName("consul");
        String registryAddress = grpcProperties.getConsulIp();
        Preconditions.checkNotNull(registryAddress, "RegistryAddress can not be null", registryAddress);
        serviceConfig.setRegistryAddress(registryAddress);
        int port = grpcProperties.getConsulPort();
        Preconditions.checkState(port != 0, "RegistryPort can not be zero", port);
        serviceConfig.setRegistryPort(grpcProperties.getConsulPort());
        int serverPort = grpcProperties.getServerPort();
        Preconditions.checkNotNull(serverPort, "ServerPort can not be null", serverPort);
        serviceConfig.setPort(grpcProperties.getServerPort());
        return serviceConfig;
    }

    private Collection<Object> getTypedBeansWithAnnotation(Class<? extends Annotation> annotationType) throws Exception {
        return Stream.of(applicationContext.getBeanNamesForAnnotation(Service.class)).filter(name -> {
            BeanDefinition beanDefinition = applicationContext.getBeanFactory().getBeanDefinition(name);
            if (beanDefinition.getSource() instanceof StandardMethodMetadata) {
                StandardMethodMetadata metadata = (StandardMethodMetadata) beanDefinition.getSource();
                return metadata.isAnnotated(annotationType.getName());
            }
            return null != applicationContext.getBeanFactory().findAnnotationOnBean(name, annotationType);
        }).map(name -> applicationContext.getBeanFactory().getBean(name)).collect(Collectors.toList());

    }

}
