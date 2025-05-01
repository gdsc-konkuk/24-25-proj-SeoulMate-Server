package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceSourceData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for repositories providing source data for tourist places. Implementations collect basic place information
 * from various data sources which will later be enriched with additional details.
 * <p>
 * It focuses on collecting the following data:
 * <ul>
 *     <li>Place name</li>
 *     <li>Place description</li>
 * </ul>
 *
 * @see PlaceSourceData
 */
public interface PlaceSourceDataRepository {

    /**
     * Retrieves all available place source data from the data source.
     *
     * @return List of PlaceSourceData with basic information (name, description)
     */
    List<PlaceSourceData> findAll();

    /**
     * Asynchronously retrieves all available place source data.
     *
     * @return CompletableFuture that resolves to a list of PlaceSourceData
     */
    CompletableFuture<List<PlaceSourceData>> findAllAsync();
}
