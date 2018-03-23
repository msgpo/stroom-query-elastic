package stroom.autoindex.indexing;

/**
 * Manages access to the index jobs registered in the system.
 * Jobs will be created for each auto index, when job is completed it should be deleted from the database.
 */
public interface IndexJobDao {
    /**
     *
     * @param docRefUuid The UUID of the Auto Index doc ref
     * @return The index job found, or next one created.
     */
    IndexJob getOrCreate(String docRefUuid) throws Exception;

}