package org.unreal.modelrouter.entity;

import java.util.List;
import java.util.Map;

public record MergeInstanceResult(List<Map<String, Object>> mergedInstances, List<String> conflicts,
                                  List<String> warnings) {
}
