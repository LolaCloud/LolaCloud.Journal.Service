package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.model.LogEvent;
import com.miranda.luna.journal.domain.repository.LogEventRepository;
import com.miranda.luna.journal.infrastructure.web.dto.LogEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

@Service
public class S3ArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(S3ArchiveService.class);
    private static final DateTimeFormatter ARCHIVE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd/HHmmss").withZone(ZoneOffset.UTC);

    private final S3Client s3Client;
    private final LogEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${lola.journal.s3.bucket-name:lola-journal-archives}")
    private String archiveBucketName;

    @Value("${lola.journal.store.api-url:http://localhost:8086}")
    private String storeApiUrl;

    public S3ArchiveService(
            S3Client s3Client,
            LogEventRepository eventRepository,
            ObjectMapper objectMapper
    ) {
        this.s3Client = s3Client;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureArchiveBucketExists() {
        try {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(archiveBucketName).build());
                logger.info("S3 archive bucket '{}' verified.", archiveBucketName);
                return;
            } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
                logger.info("Bucket '{}' not present or inaccessible, attempting creation...", archiveBucketName);
            }

            s3Client.createBucket(CreateBucketRequest.builder().bucket(archiveBucketName).build());
            logger.info("Successfully created S3 archive bucket '{}'", archiveBucketName);

            // Notify / register with Store API if available
            registerWithStoreService();
        } catch (Exception e) {
            logger.warn("S3 archive bucket '{}' could not be initialized at startup: {}", archiveBucketName, e.getMessage());
        }
    }

    public ArchiveResult archiveGroupLogs(String groupName, Instant before) {
        Instant cutoff = before != null ? before : Instant.now().minus(Duration.ofDays(7));
        List<LogEvent> events = eventRepository.findEventsForArchive(groupName, cutoff);

        if (events.isEmpty()) {
            return new ArchiveResult(groupName, null, 0, 0L, Instant.now());
        }

        List<LogEventResponse> responses = events.stream().map(LogEventResponse::from).toList();

        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(responses);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(jsonBytes);
            }
            byte[] compressedBytes = baos.toByteArray();

            String sanitizedGroupName = groupName.replace("/", "_").replaceAll("^_+", "");
            String objectKey = String.format("archives/%s/%s.json.gz", sanitizedGroupName, ARCHIVE_PATH_FORMATTER.format(Instant.now()));

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(archiveBucketName)
                            .key(objectKey)
                            .contentType("application/gzip")
                            .contentEncoding("gzip")
                            .build(),
                    RequestBody.fromBytes(compressedBytes)
            );

            logger.info("Archived {} log events for group '{}' to S3 key '{}' ({} bytes)",
                    events.size(), groupName, objectKey, compressedBytes.length);

            return new ArchiveResult(groupName, objectKey, events.size(), (long) compressedBytes.length, Instant.now());
        } catch (Exception e) {
            logger.error("Failed archiving logs for group '{}': {}", groupName, e.getMessage(), e);
            throw new RuntimeException("Failed archiving logs: " + e.getMessage(), e);
        }
    }

    private void registerWithStoreService() {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "name", archiveBucketName,
                    "region", "us-east-1",
                    "accessPolicy", "PRIVATE",
                    "versioningEnabled", false,
                    "isProtected", true
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(storeApiUrl + "/api/v1/store/buckets"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> logger.debug("Store service registration returned status {}", res.statusCode()))
                    .exceptionally(t -> {
                        logger.debug("Store service registration skipped: {}", t.getMessage());
                        return null;
                    });
        } catch (Exception ignored) {
        }
    }

    public record ArchiveResult(
            String groupName,
            String s3Key,
            int eventCount,
            Long compressedSizeBytes,
            Instant archiveTimestamp
    ) {
    }
}
