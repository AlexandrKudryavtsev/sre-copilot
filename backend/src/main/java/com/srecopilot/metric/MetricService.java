package com.srecopilot.metric;

import com.srecopilot.common.exception.FeatureNotImplementedException;
import com.srecopilot.metric.MetricDtos.MetricSnapshotResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MetricService {

    public MetricSnapshotResponse current(long projectId) {
        throw new FeatureNotImplementedException("Get current metrics");
    }

    public List<MetricSnapshotResponse> history(long projectId, Instant from, Instant to) {
        throw new FeatureNotImplementedException("Get metric history");
    }
}
