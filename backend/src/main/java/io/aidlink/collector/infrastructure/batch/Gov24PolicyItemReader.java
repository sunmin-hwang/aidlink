package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.PolicySourceClient;
import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.util.List;

public class Gov24PolicyItemReader implements ItemStreamReader<RawPolicyDocument> {

    private static final String READ_COUNT_KEY = "gov24.reader.readCount";
    private static final String PAGE_SIZE_KEY = "gov24.reader.pageSize";

    private final PolicySourceClient client;
    private final int pageSize;

    private List<RawPolicyDocument> buffer = List.of();
    private int bufferIndex;
    private long readCount;
    private long totalCount = -1;
    private int pageToLoad = 1;
    private int offsetOnFirstPage;

    public Gov24PolicyItemReader(PolicySourceClient client, int pageSize) {
        if (pageSize < 1 || pageSize > 1_000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        this.client = client;
        this.pageSize = pageSize;
    }

    @Override
    public RawPolicyDocument read() {
        if (totalCount >= 0 && readCount >= totalCount) {
            return null;
        }
        if (bufferIndex >= buffer.size()) {
            loadNextPage();
            if (bufferIndex >= buffer.size()) {
                return null;
            }
        }

        RawPolicyDocument item = buffer.get(bufferIndex++);
        readCount++;
        return item;
    }

    private void loadNextPage() {
        CollectedPolicyPage page = client.fetchPage(pageToLoad, pageSize);
        if (totalCount < 0) {
            totalCount = page.totalCount();
        }
        buffer = page.policies();
        bufferIndex = offsetOnFirstPage;
        offsetOnFirstPage = 0;
        pageToLoad++;

        if (buffer.isEmpty() && readCount < totalCount) {
            throw new IllegalStateException(
                    "Gov24 returned an empty page before totalCount was reached"
            );
        }
        if (bufferIndex > buffer.size()) {
            throw new IllegalStateException("Saved reader offset exceeds the returned page size");
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        long savedReadCount = executionContext.getLong(READ_COUNT_KEY, 0L);
        int savedPageSize = executionContext.getInt(PAGE_SIZE_KEY, pageSize);
        if (savedPageSize != pageSize) {
            throw new ItemStreamException(
                    "Cannot restart the Gov24 reader with a different page size"
            );
        }
        readCount = savedReadCount;
        pageToLoad = Math.toIntExact(savedReadCount / pageSize) + 1;
        offsetOnFirstPage = Math.toIntExact(savedReadCount % pageSize);
        buffer = List.of();
        bufferIndex = 0;
        totalCount = -1;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(READ_COUNT_KEY, readCount);
        executionContext.putInt(PAGE_SIZE_KEY, pageSize);
    }

    @Override
    public void close() throws ItemStreamException {
        buffer = List.of();
        bufferIndex = 0;
    }
}
