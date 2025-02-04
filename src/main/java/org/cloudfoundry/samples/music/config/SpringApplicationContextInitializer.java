package org.cloudfoundry.samples.music.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudException;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.BaseServiceInfo;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.*;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Log logger = LogFactory.getLog(SpringApplicationContextInitializer.class);

    private static final Map<Class<? extends ServiceInfo>, String> serviceTypeToProfileName = new HashMap<>();
    private static final List<String> validLocalProfiles =
            Arrays.asList("mysql", "postgres", "sqlserver", "oracle", "mongodb", "redis", "rabbitmq");

    static {
        serviceTypeToProfileName.put(MongoServiceInfo.class, "mongodb");
        serviceTypeToProfileName.put(PostgresqlServiceInfo.class, "postgres");
        serviceTypeToProfileName.put(MysqlServiceInfo.class, "mysql");
        serviceTypeToProfileName.put(RedisServiceInfo.class, "redis");
        serviceTypeToProfileName.put(OracleServiceInfo.class, "oracle");
        serviceTypeToProfileName.put(SqlServerServiceInfo.class, "sqlserver");
        serviceTypeToProfileName.put(AmqpServiceInfo.class, "rabbitmq");
        //serviceTypeToProfileName.put(BaseServiceInfo.class, "elasticsearch");
        //serviceTypeToProfileName.put(CassandraServiceInfo.class, "cassandra");



    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Cloud cloud = getCloud();

        ConfigurableEnvironment appEnvironment = applicationContext.getEnvironment();

        validateActiveProfiles(appEnvironment);

        addCloudProfile(cloud, appEnvironment);

        excludeAutoConfiguration(appEnvironment);
    }

    private void addCloudProfile(Cloud cloud, ConfigurableEnvironment appEnvironment) {
        if (cloud == null) {
            return;
        }

        List<String> profiles = new ArrayList<>();

        List<ServiceInfo> serviceInfos = cloud.getServiceInfos();

        logger.info("Found serviceInfos: " + StringUtils.collectionToCommaDelimitedString(serviceInfos));

        for (ServiceInfo serviceInfo : serviceInfos) {
            if (serviceTypeToProfileName.containsKey(serviceInfo.getClass())) {
                profiles.add(serviceTypeToProfileName.get(serviceInfo.getClass()));
            }
        }

        if (profiles.size() > 1) {
            throw new IllegalStateException(
                    "Only one service of the following types may be bound to this application: " +
                            serviceTypeToProfileName.values().toString() + ". " +
                            "These services are bound to the application: [" +
                            StringUtils.collectionToCommaDelimitedString(profiles) + "]");
        }

        if (profiles.size() > 0) {
            appEnvironment.addActiveProfile(profiles.get(0));
            appEnvironment.addActiveProfile(profiles.get(0) + "-cloud");
        }
    }

    private Cloud getCloud() {
        try {
            CloudFactory cloudFactory = new CloudFactory();
            return cloudFactory.getCloud();
        } catch (CloudException ce) {
            return null;
        }
    }

    private void validateActiveProfiles(ConfigurableEnvironment appEnvironment) {
        List<String> serviceProfiles = Stream.of(appEnvironment.getActiveProfiles())
                .filter(validLocalProfiles::contains)
                .collect(Collectors.toList());

        if (serviceProfiles.size() > 1) {
            throw new IllegalStateException("Only one active Spring profile may be set among the following: " +
                    validLocalProfiles.toString() + ". " +
                    "These profiles are active: [" +
                    StringUtils.collectionToCommaDelimitedString(serviceProfiles) + "]");
        }
    }

    private void excludeAutoConfiguration(ConfigurableEnvironment environment) {
        List<String> exclude = new ArrayList<>();
        if (environment.acceptsProfiles("redis")) {
            excludeDataSourceAutoConfiguration(exclude);
            excludeMongoAutoConfiguration(exclude);
            excludeRabbitAutoConfiguration(exclude);
        } else if (environment.acceptsProfiles("mongodb")) {
            excludeDataSourceAutoConfiguration(exclude);
            excludeRedisAutoConfiguration(exclude);
            excludeRabbitAutoConfiguration(exclude);
        } else if (environment.acceptsProfiles("rabbitmq")) {
            excludeMongoAutoConfiguration(exclude);
            excludeRedisAutoConfiguration(exclude);
            excludeDataSourceAutoConfiguration(exclude);
        } else {
            excludeMongoAutoConfiguration(exclude);
            excludeRedisAutoConfiguration(exclude);
            excludeRabbitAutoConfiguration(exclude);
        }

        Map<String, Object> properties = Collections.singletonMap("spring.autoconfigure.exclude",
                StringUtils.collectionToCommaDelimitedString(exclude));

        PropertySource<?> propertySource = new MapPropertySource("springMusicAutoConfig", properties);

        environment.getPropertySources().addFirst(propertySource);
    }

    private void excludeDataSourceAutoConfiguration(List<String> exclude) {
        exclude.add(DataSourceAutoConfiguration.class.getName());
    }

    private void excludeMongoAutoConfiguration(List<String> exclude) {
        exclude.addAll(Arrays.asList(
                MongoAutoConfiguration.class.getName(),
                MongoDataAutoConfiguration.class.getName(),
                MongoRepositoriesAutoConfiguration.class.getName()
        ));
    }

    private void excludeRedisAutoConfiguration(List<String> exclude) {
        exclude.addAll(Arrays.asList(
                RedisAutoConfiguration.class.getName(),
                RedisRepositoriesAutoConfiguration.class.getName()
        ));
    }

    private void excludeRabbitAutoConfiguration(List<String> exclude) {
        exclude.addAll(Arrays.asList(
                RabbitAutoConfiguration.class.getName()
        ));
    }
}
