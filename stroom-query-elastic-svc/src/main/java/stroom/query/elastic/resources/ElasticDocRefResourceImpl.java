package stroom.query.elastic.resources;

import org.apache.http.HttpStatus;
import stroom.query.audit.DocRefResource;
import stroom.query.elastic.hibernate.ElasticIndexConfig;
import stroom.query.elastic.service.ElasticDocRefService;
import stroom.util.shared.QueryApiException;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

/**
 * This is the general Doc Ref resource that stroom will use to manage doc refs
 */
public class ElasticDocRefResourceImpl implements DocRefResource {
    private final ElasticDocRefService service;

    @Inject
    public ElasticDocRefResourceImpl(final ElasticDocRefService service) {
        this.service = service;
    }

    @Override
    public Response getAll() throws QueryApiException {
        return Response.ok(service.getAll()).build();
    }

    @Override
    public Response get(String uuid) throws QueryApiException {
        return service.get(uuid)
                .map(d -> Response.ok(d).build())
                .orElse(Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity(new ElasticIndexConfig())
                        .build());
    }

    @Override
    public Response createDocument(final String uuid,
                                   final String name) throws QueryApiException {
        return service.createDocument(uuid, name)
                .map(d -> Response.ok(d).build())
                .orElse(Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity(new ElasticIndexConfig())
                        .build());
    }

    @Override
    public Response copyDocument(final String originalUuid,
                                 final String copyUuid) throws QueryApiException {
        return service.copyDocument(originalUuid, copyUuid)
                .map(d -> Response.ok(d).build())
                .orElse(Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity(new ElasticIndexConfig())
                        .build());
    }

    @Override
    public Response documentMoved(final String uuid) throws QueryApiException {
        return service.documentMoved(uuid)
                .map(d -> Response.ok(d).build())
                .orElse(Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity(new ElasticIndexConfig())
                        .build());
    }

    @Override
    public Response documentRenamed(final String uuid, final String name) throws QueryApiException {
        return service.documentRenamed(uuid, name)
                .map(d -> Response.ok(d).build())
                .orElse(Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity(new ElasticIndexConfig())
                        .build());
    }

    @Override
    public Response deleteDocument(final String uuid) throws QueryApiException {
        service.deleteDocument(uuid);

        return Response.noContent().build();
    }
}