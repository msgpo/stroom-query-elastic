package stroom.autoindex.animals.app;

import stroom.query.audit.ExportDTO;
import stroom.query.audit.model.DocRefEntity;
import stroom.query.audit.security.ServiceUser;
import stroom.query.audit.service.DocRefService;
import stroom.query.audit.service.QueryApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AnimalDocRefServiceImpl implements DocRefService<AnimalDocRefEntity> {
    private static Map<String, AnimalDocRefEntity> data = new HashMap<>();

    public static void eraseAllData() {
        data.clear();
    }

    @Override
    public String getType() {
        return AnimalDocRefEntity.TYPE;
    }

    @Override
    public List<AnimalDocRefEntity> getAll(final ServiceUser user) throws QueryApiException {
        return new ArrayList<>(data.values());
    }

    @Override
    public Optional<AnimalDocRefEntity> get(final ServiceUser user,
                                          final String uuid) throws QueryApiException {
        return Optional.ofNullable(data.get(uuid));
    }

    @Override
    public Optional<AnimalDocRefEntity> createDocument(final ServiceUser user,
                                                       final String uuid,
                                                       final String name) throws QueryApiException {
        final Long now = System.currentTimeMillis();
        data.put(uuid, new AnimalDocRefEntity.Builder()
                .uuid(uuid)
                .name(name)
                .createUser(user.getName())
                .createTime(now)
                .updateUser(user.getName())
                .updateTime(now)
                .build());

        return get(user, uuid);
    }

    @Override
    public Optional<AnimalDocRefEntity> update(final ServiceUser user,
                                             final String uuid,
                                             final AnimalDocRefEntity updatedConfig) throws QueryApiException {
        return get(user, uuid)
                .map(d -> new AnimalDocRefEntity.Builder(d)
                        .updateTime(System.currentTimeMillis())
                        .updateUser(user.getName())
                        .dataDirectory(updatedConfig.getDataDirectory())
                        .build());
    }

    @Override
    public Optional<AnimalDocRefEntity> copyDocument(final ServiceUser user,
                                                   final String originalUuid,
                                                   final String copyUuid) throws QueryApiException {
        final AnimalDocRefEntity existing = data.get(originalUuid);
        if (null != existing) {
            createDocument(user, copyUuid, existing.getName());
            return update(user, copyUuid, existing);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AnimalDocRefEntity> moveDocument(final ServiceUser user,
                                                   final String uuid) throws QueryApiException {
        return get(user, uuid);
    }

    @Override
    public Optional<AnimalDocRefEntity> renameDocument(final ServiceUser user,
                                                     final String uuid,
                                                     final String name) throws QueryApiException {
        return get(user, uuid)
                .map(d -> new AnimalDocRefEntity.Builder(d)
                        .updateTime(System.currentTimeMillis())
                        .updateUser(user.getName())
                        .name(name)
                        .build());
    }

    @Override
    public Optional<Boolean> deleteDocument(final ServiceUser user,
                                            final String uuid) throws QueryApiException {
        if (data.containsKey(uuid)) {
            data.remove(uuid);
            return Optional.of(Boolean.TRUE);
        } else {
            return Optional.of(Boolean.FALSE);
        }
    }

    @Override
    public ExportDTO exportDocument(final ServiceUser user,
                                    final String uuid) throws QueryApiException {
        return get(user, uuid)
                .map(d -> new ExportDTO.Builder()
                        .value(DocRefEntity.NAME, d.getName())
                        .value(AnimalDocRefEntity.DATA_DIRECTORY, d.getDataDirectory())
                        .build())
                .orElse(new ExportDTO.Builder()
                        .message(String.format("Could not find test doc ref: %s", uuid))
                        .build());
    }

    @Override
    public Optional<AnimalDocRefEntity> importDocument(final ServiceUser user,
                                                     final String uuid,
                                                     final String name,
                                                     final Boolean confirmed,
                                                     final Map<String, String> dataMap) throws QueryApiException {
        if (confirmed) {
            final Optional<AnimalDocRefEntity> index = createDocument(user, uuid, name);

            if (index.isPresent()) {
                final AnimalDocRefEntity indexConfig = index.get();
                indexConfig.setDataDirectory(dataMap.get(AnimalDocRefEntity.DATA_DIRECTORY));
                return update(user, uuid, indexConfig);
            } else {
                return Optional.empty();
            }
        } else {
            return get(user, uuid)
                    .map(d -> Optional.<AnimalDocRefEntity>empty())
                    .orElse(Optional.of(new AnimalDocRefEntity.Builder()
                            .uuid(uuid)
                            .name(name)
                            .dataDirectory(dataMap.get(AnimalDocRefEntity.DATA_DIRECTORY))
                            .build()));
        }
    }
}
