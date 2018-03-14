package stroom.autoindex.animals;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.ClassRule;
import stroom.autoindex.animals.app.AnimalDocRefEntity;
import stroom.autoindex.animals.app.AnimalApp;
import stroom.autoindex.animals.app.AnimalConfig;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.*;
import stroom.query.testing.DropwizardAppWithClientsRule;
import stroom.query.testing.QueryResourceIT;
import stroom.query.testing.StroomAuthenticationRule;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.junit.Assert.assertTrue;

public class AnimalsQueryResourceIT extends QueryResourceIT<AnimalDocRefEntity, AnimalConfig> {
    @ClassRule
    public static final DropwizardAppWithClientsRule<AnimalConfig> appRule =
            new DropwizardAppWithClientsRule<>(AnimalApp.class, resourceFilePath("animal/config.yml"));

    @ClassRule
    public static StroomAuthenticationRule authRule =
            new StroomAuthenticationRule(WireMockConfiguration.options().port(10080), AnimalDocRefEntity.TYPE);

    public AnimalsQueryResourceIT() {
        super(AnimalDocRefEntity.class,
                AnimalDocRefEntity.TYPE,
                appRule,
                authRule);
    }

    @Override
    protected SearchRequest getValidSearchRequest(final DocRef docRef,
                                                  final ExpressionOperator expressionOperator,
                                                  final OffsetRange offsetRange) {
        final String queryKey = UUID.randomUUID().toString();
        return new SearchRequest.Builder()
                .query(new Query.Builder()
                        .dataSource(docRef)
                        .expression(expressionOperator)
                        .build())
                .key(queryKey)
                .dateTimeLocale("en-gb")
                .incremental(true)
                .addResultRequests(new ResultRequest.Builder()
                        .fetch(ResultRequest.Fetch.ALL)
                        .resultStyle(ResultRequest.ResultStyle.FLAT)
                        .componentId("componentId")
                        .requestedRange(offsetRange)
                        .addMappings(new TableSettings.Builder()
                                .queryId(queryKey)
                                .extractValues(false)
                                .showDetail(false)
                                .addFields(new Field.Builder()
                                        .name(AnimalDocRefEntity.SPECIES)
                                        .expression("${" + AnimalDocRefEntity.SPECIES + "}")
                                        .build())
                                .addMaxResults(10)
                                .build())
                        .build())
                .build();
    }

    @Override
    protected void assertValidDataSource(final DataSource dataSource) {
        final Set<String> resultFieldNames = dataSource.getFields().stream()
                .map(DataSourceField::getName)
                .collect(Collectors.toSet());

        assertTrue(resultFieldNames.contains(AnimalDocRefEntity.SPECIES));
    }

    @Override
    protected AnimalDocRefEntity getValidEntity(final DocRef docRef) {
        return new AnimalDocRefEntity.Builder()
                .docRef(docRef)
                .species("Fattus Rattus")
                .build();
    }
}
