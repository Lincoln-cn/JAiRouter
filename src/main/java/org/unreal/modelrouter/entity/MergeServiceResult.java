package org.unreal.modelrouter.entity;

import java.util.List;
import java.util.Map;

public record MergeServiceResult(Map<String, Object> mergedServices, List<String> conflicts, List<String> warnings) {
}
