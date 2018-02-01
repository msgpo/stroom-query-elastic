package stroom.query.elastic;

import event.logging.EventLoggingService;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.elasticsearch.common.collect.Tuple;
import stroom.query.audit.AuditedQueryBundle;
import stroom.query.audit.authorisation.AuthorisationService;
import stroom.query.audit.rest.AuditedDocRefResourceImpl;
import stroom.query.audit.rest.AuditedQueryResourceImpl;
import stroom.query.audit.service.DocRefService;
import stroom.query.audit.service.QueryService;
import stroom.query.elastic.config.Config;
import stroom.query.elastic.health.ElasticHealthCheck;
import stroom.query.elastic.hibernate.ElasticIndexDocRefEntity;
import stroom.query.elastic.service.ElasticDocRefServiceImpl;
import stroom.query.elastic.service.ElasticQueryServiceImpl;
import stroom.query.elastic.transportClient.TransportClientBundle;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

public class App extends Application<Config> {

    public TransportClientBundle<Config> transportClientBundle = new TransportClientBundle<Config>() {

        @Override
        protected Map<String, Integer> getHosts(final Config config) {
            return Arrays.stream(config.getElasticConfig().getHosts().split(ElasticConfig.ENTRY_DELIMITER))
                .map(h -> h.split(ElasticConfig.HOST_PORT_DELIMITER))
                .filter(h -> (h.length == 2))
                .map(h -> new Tuple<>(h[0], Integer.parseInt(h[1])))
                .collect(Collectors.toMap(Tuple::v1, Tuple::v2));
        }

        @Override
        protected String getClusterName(final Config config) {
            return config.getElasticConfig().getClusterName();
        }
    };

    public static final class AuditedElasticDocRefResource extends AuditedDocRefResourceImpl<ElasticIndexDocRefEntity> {

        @Inject
        public AuditedElasticDocRefResource(final DocRefService<ElasticIndexDocRefEntity> service,
                                            final EventLoggingService eventLoggingService,
                                            final AuthorisationService authorisationService) {
            super(service, eventLoggingService, authorisationService);
        }
    }

    public static final class AuditedElasticQueryResource extends AuditedQueryResourceImpl<ElasticIndexDocRefEntity> {

        @Inject
        public AuditedElasticQueryResource(final EventLoggingService eventLoggingService,
                                           final QueryService service,
                                           final AuthorisationService authorisationService,
                                           final DocRefService<ElasticIndexDocRefEntity> docRefService) {
            super(eventLoggingService, service, authorisationService, docRefService);
        }
    }

    private final AuditedQueryBundle<Config,
            ElasticIndexDocRefEntity,
            ElasticQueryServiceImpl,
            AuditedElasticQueryResource,
            ElasticDocRefServiceImpl,
            AuditedElasticDocRefResource> auditedQueryBundle =
            new AuditedQueryBundle<>(
                            ElasticIndexDocRefEntity.class,
                            ElasticQueryServiceImpl.class,
                            AuditedElasticQueryResource.class,
                            ElasticDocRefServiceImpl.class,
                            AuditedElasticDocRefResource.class);

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void run(final Config configuration, final Environment environment) {
        environment.healthChecks().register(
                "Elastic",
                new ElasticHealthCheck(transportClientBundle.getTransportClient())
        );
        environment.jersey().register(
                new Module(transportClientBundle.getTransportClient())
        );

        configureCors(environment);
    }


    @Override
    public void initialize(final Bootstrap<Config> bootstrap) {
        super.initialize(bootstrap);

        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));

        bootstrap.addBundle(this.transportClientBundle);
        bootstrap.addBundle(this.auditedQueryBundle);

    }

    private static void configureCors(final Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, new String[]{"/*"});
        cors.setInitParameter("allowedMethods", "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter("allowedOrigins", "*");
        cors.setInitParameter("Access-Control-Allow-Origin", "*");
        cors.setInitParameter("allowedHeaders", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
        cors.setInitParameter("allowCredentials", "true");
    }
}
